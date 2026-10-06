package pvt.phgg.chess.server.analysis;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pvt.phgg.chess.server.analysis.GameAnalysisService.LoadedGame;
import pvt.phgg.chess.server.analysis.KeyMoves.KeyMove;
import pvt.phgg.chess.server.analysis.llm.LlmClient;
import pvt.phgg.chess.server.analysis.llm.LlmException;
import pvt.phgg.chess.server.analysis.llm.LlmReply;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// Writes the LLM comments for reviews in COMMENTING, one game at a time, on its own thread so a slow
// model never holds up Stockfish analysing other games.
//
// Reviews wait in COMMENTING in the database, so a restart simply carries on with them, and so do
// comments: a game interrupted part-way keeps the ones already written. When the LLM can't be reached
// the review stays in COMMENTING and is tried again later; the engine's labels and arrows are shown
// in the meantime.
@Component
public class CommentaryWorker {

    private static final Logger log = LoggerFactory.getLogger(CommentaryWorker.class);

    // A comment that fails the check gets one more try, then the move goes without.
    private static final int ATTEMPTS = 2;

    enum Step { COMMENTED, IDLE, LLM_UNAVAILABLE }

    private final JdbcTemplate jdbcTemplate;
    private final GameAnalysisService analysisService;
    private final LlmClient llm;
    private final CommentaryProperties properties;
    private final AnalysisProperties analysisProperties;
    private final Semaphore wakeUp = new Semaphore(0);

    private volatile Thread thread;
    private boolean warnedUnavailable;

    public CommentaryWorker(JdbcTemplate jdbcTemplate, GameAnalysisService analysisService, LlmClient llm,
                            CommentaryProperties properties, AnalysisProperties analysisProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.analysisService = analysisService;
        this.llm = llm;
        this.properties = properties;
        this.analysisProperties = analysisProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.enabled() || !analysisProperties.workerEnabled()) {
            log.info("Commentary worker not started (chess.commentary.enabled={}, chess.analysis.worker-enabled={})",
                    properties.enabled(), analysisProperties.workerEnabled());
            return;
        }
        thread = new Thread(this::run, "commentary-worker");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
            t.join(5_000);
        }
    }

    // A review has reached COMMENTING.
    void wake() {
        wakeUp.release();
    }

    private void run() {
        while (thread == Thread.currentThread()) {
            try {
                switch (runOnce()) {
                    case COMMENTED -> { }
                    case IDLE -> await(properties.pollInterval());
                    case LLM_UNAVAILABLE -> await(properties.retryInterval());
                }
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                log.error("Commentary worker error", e);
                try {
                    Thread.sleep(properties.retryInterval().toMillis());
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private void await(Duration timeout) throws InterruptedException {
        if (wakeUp.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            wakeUp.drainPermits();
        }
    }

    Step runOnce() {
        List<Long> waiting = jdbcTemplate.queryForList(
                "SELECT game_id FROM game_reviews WHERE status = 'COMMENTING' ORDER BY game_id LIMIT 1", Long.class);
        if (waiting.isEmpty()) return Step.IDLE;
        long gameId = waiting.getFirst();

        if (!llm.prepare()) {
            if (!warnedUnavailable) {
                log.warn("LLM {} not reachable; reviews wait in COMMENTING until it is", llm.modelName());
                warnedUnavailable = true;
            }
            return Step.LLM_UNAVAILABLE;
        }
        warnedUnavailable = false;
        try {
            commentGame(gameId);
            return Step.COMMENTED;
        } catch (LlmException e) {
            log.warn("Commenting game {} stopped, will retry: {}", gameId, e.getMessage());
            return Step.LLM_UNAVAILABLE;
        }
    }

    void commentGame(long gameId) throws LlmException {
        long start = System.nanoTime();
        LoadedGame game;
        List<KeyMove> moves;
        try {
            Optional<LoadedGame> loaded = analysisService.load(gameId);
            if (loaded.isEmpty()) {
                finish(gameId, null);
                return;
            }
            game = loaded.get();
            moves = KeyMoves.select(game, properties.maxComments());
        } catch (RuntimeException e) {
            // Not the LLM's fault, so retrying won't help; the review keeps its engine part.
            log.error("Can't plan comments for game {}; finishing its review without them", gameId, e);
            finish(gameId, null);
            return;
        }

        Set<Integer> written = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT ply FROM move_comments WHERE game_id = ?", Integer.class, gameId));
        int added = 0;
        int dropped = 0;
        int tokens = 0;
        Duration llmTime = Duration.ZERO;
        for (KeyMove move : moves) {
            if (written.contains(move.index() + 1)) continue;
            String prompt;
            try {
                prompt = CommentaryPrompt.user(MoveFacts.describe(game, move.index(), move.classification(), move.decisive()));
            } catch (RuntimeException e) {
                log.warn("Can't describe game {} ply {}; no comment", gameId, move.index() + 1, e);
                dropped++;
                continue;
            }
            Optional<String> comment = Optional.empty();
            for (int attempt = 1; attempt <= ATTEMPTS && comment.isEmpty(); attempt++) {
                LlmReply reply = llm.generate(CommentaryPrompt.SYSTEM, prompt);
                llmTime = llmTime.plus(reply.elapsed());
                if (reply.outputTokens() != null) tokens += reply.outputTokens();
                comment = CommentaryPrompt.check(reply.text(), prompt);
                if (comment.isEmpty()) {
                    log.info("Rejected comment for game {} ply {} (attempt {}): {}",
                            gameId, move.index() + 1, attempt, reply.text());
                }
            }
            if (comment.isPresent()) {
                jdbcTemplate.update("""
                        INSERT INTO move_comments (game_id, ply, comment) VALUES (?, ?, ?)
                        ON CONFLICT (game_id, ply) DO UPDATE SET comment = EXCLUDED.comment
                        """, gameId, move.index() + 1, comment.get());
                added++;
            } else {
                dropped++;
            }
        }
        finish(gameId, llm.modelName());

        double seconds = llmTime.toMillis() / 1000.0;
        log.info("Commented game {} with {}: {} comment(s) written, {} dropped, {} s total, {} s in the LLM, {} tokens/s",
                gameId, llm.modelName(), added, dropped,
                String.format("%.1f", (System.nanoTime() - start) / 1e9), String.format("%.1f", seconds),
                seconds > 0 ? String.format("%.1f", tokens / seconds) : "-");
    }

    private void finish(long gameId, String model) {
        jdbcTemplate.update("""
                UPDATE game_reviews SET status = 'DONE', llm_model = ?, completed_at = now()
                WHERE game_id = ? AND status = 'COMMENTING'
                """, model, gameId);
    }
}

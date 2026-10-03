package pvt.phgg.chess.server.analysis;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

// Works through the analysis queue on one background thread: claims a position, answers it from the
// cache or with Stockfish, and closes a game's review once none of its positions are pending.
//
// Claiming uses FOR UPDATE SKIP LOCKED, so a second worker could be added without changes.
@Component
public class AnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

    record Job(long id, long gameId, int ply, String epd, boolean chess960, int attempts) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final AnalysisQueue queue;
    private final PositionAnalyzer analyzer;
    private final AnalysisProperties properties;

    private volatile Thread thread;
    private boolean warnedUnavailable;

    public AnalysisWorker(JdbcTemplate jdbcTemplate, AnalysisQueue queue, PositionAnalyzer analyzer,
                          AnalysisProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.queue = queue;
        this.analyzer = analyzer;
        this.properties = properties;
    }

    // ApplicationReadyEvent: after Flyway has migrated and in-progress games have been restored.
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.workerEnabled()) {
            log.info("Analysis worker disabled (chess.analysis.worker-enabled=false)");
            return;
        }
        thread = new Thread(this::run, "analysis-worker");
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

    private void run() {
        try {
            recover();
        } catch (RuntimeException e) {
            log.error("Analysis recovery failed; continuing with the queue as it is", e);
        }
        while (thread == Thread.currentThread()) {
            try {
                if (!runOnce()) {
                    queue.awaitWork(properties.pollInterval());
                }
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                log.error("Analysis worker error", e);
                try {
                    Thread.sleep(properties.pollInterval().toMillis());
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    // One step of work. Returns false when there was nothing it could do, so the caller can wait.
    boolean runOnce() {
        queue.enqueueRequested();
        if (!analyzer.isAvailable()) {
            if (!warnedUnavailable) {
                log.warn("Stockfish not found at {}; analysis jobs stay queued until it is",
                        properties.stockfishPath());
                warnedUnavailable = true;
            }
            return false;
        }
        Job job = claim();
        if (job == null) return false;
        process(job);
        return true;
    }

    // On startup, before claiming anything: undo the effects of a stop part-way through.
    void recover() {
        int requeued = jdbcTemplate.update("UPDATE analysis_jobs SET status = 'QUEUED' WHERE status = 'RUNNING'");

        // A stop between a game's last job finishing and its review closing.
        List<Long> engineReviews = jdbcTemplate.queryForList(
                "SELECT game_id FROM game_reviews WHERE status = 'ENGINE'", Long.class);
        engineReviews.forEach(this::finishIfComplete);
        // Step 5 adds: re-run the LLM step for reviews stuck in COMMENTING.

        // Games that ended but never got queued: a stop between the game ending and the worker
        // picking up the request. Only games since this feature was installed (the V7 migration);
        // older ones are analysed on request.
        List<Long> missed = jdbcTemplate.queryForList("""
                SELECT g.id FROM games g
                WHERE g.ended_at >= (SELECT installed_on FROM flyway_schema_history WHERE version = '7')
                  AND NOT EXISTS (SELECT 1 FROM game_reviews r WHERE r.game_id = g.id)
                  AND (SELECT count(*) FROM game_moves m WHERE m.game_id = g.id) >= ?
                ORDER BY g.id
                """, Long.class, AnalysisQueue.MIN_PLIES);
        missed.forEach(queue::enqueueGame);

        if (requeued > 0 || !missed.isEmpty()) {
            log.info("Analysis recovery: {} interrupted position(s) re-queued, {} missed game(s) queued",
                    requeued, missed.size());
        }
    }

    Job claim() {
        List<Job> jobs = jdbcTemplate.query("""
                UPDATE analysis_jobs SET status = 'RUNNING', attempts = attempts + 1
                WHERE id = (SELECT id FROM analysis_jobs WHERE status = 'QUEUED'
                            ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED)
                RETURNING id, game_id, ply, epd, chess960, attempts
                """,
                (rs, rowNum) -> new Job(rs.getLong("id"), rs.getLong("game_id"), rs.getInt("ply"),
                        rs.getString("epd"), rs.getBoolean("chess960"), rs.getInt("attempts")));
        return jobs.isEmpty() ? null : jobs.getFirst();
    }

    private void process(Job job) {
        if (!isCached(job)) {
            try {
                // Stockfish accepts a FEN without move counters; the cached result is counter-free too.
                PositionEval eval = analyzer.analyze(job.epd(), job.chess960());
                save(job, eval, analyzer.engineName());
            } catch (AnalysisException | RuntimeException e) {
                boolean giveUp = job.attempts() >= properties.maxAttempts();
                log.warn("Analysis of game {} ply {} failed (attempt {}{}): {}", job.gameId(), job.ply(),
                        job.attempts(), giveUp ? ", giving up" : "", e.getMessage());
                jdbcTemplate.update("UPDATE analysis_jobs SET status = ? WHERE id = ?",
                        giveUp ? "FAILED" : "QUEUED", job.id());
                finishIfComplete(job.gameId());
                return;
            }
        }
        jdbcTemplate.update("UPDATE analysis_jobs SET status = 'DONE' WHERE id = ?", job.id());
        finishIfComplete(job.gameId());
    }

    private boolean isCached(Job job) {
        Integer hits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM position_evals WHERE epd = ? AND chess960 = ? AND depth >= ?",
                Integer.class, job.epd(), job.chess960(), properties.depth());
        return hits != null && hits > 0;
    }

    private void save(Job job, PositionEval eval, String engine) {
        jdbcTemplate.update("""
                INSERT INTO position_evals (epd, chess960, depth, eval_cp, mate_in, best_uci, pv_uci, engine)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (epd, chess960) DO UPDATE
                SET depth = EXCLUDED.depth, eval_cp = EXCLUDED.eval_cp, mate_in = EXCLUDED.mate_in,
                    best_uci = EXCLUDED.best_uci, pv_uci = EXCLUDED.pv_uci, engine = EXCLUDED.engine,
                    created_at = now()
                WHERE position_evals.depth < EXCLUDED.depth
                """,
                job.epd(), job.chess960(), eval.depth(), eval.evalCp(), eval.mateIn(),
                eval.bestUci(), eval.pvUci(), engine);
    }

    // Closes the engine stage once none of the game's positions are pending, in one guarded update
    // so two workers can't both do it. Failed positions don't hold the review back; their moves are
    // labelled UNKNOWN. More than a quarter failing points at a broken engine, so the review fails.
    //
    // Until the LLM step (step 5) exists this goes straight to DONE; then it becomes COMMENTING.
    void finishIfComplete(long gameId) {
        jdbcTemplate.update("""
                UPDATE game_reviews r
                SET status = CASE
                        WHEN (SELECT count(*) FROM analysis_jobs WHERE game_id = r.game_id AND status = 'FAILED') * 4
                             > (SELECT count(*) FROM analysis_jobs WHERE game_id = r.game_id)
                        THEN 'FAILED' ELSE 'DONE' END,
                    completed_at = now()
                WHERE r.game_id = ? AND r.status = 'ENGINE'
                  AND NOT EXISTS (SELECT 1 FROM analysis_jobs
                                  WHERE game_id = r.game_id AND status IN ('QUEUED', 'RUNNING'))
                """, gameId);
    }
}

package pvt.phgg.chess.server.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pvt.phgg.chess.GameReplay;
import pvt.phgg.chess.GameReplay.RecordedMove;
import pvt.phgg.chess.GameReplay.ReplayedPosition;
import pvt.phgg.chess.Position;
import pvt.phgg.chess.PromotionChoice;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// Turns an ended game into one analysis job per position, and wakes the worker.
@Service
public class AnalysisQueue {

    // A game needs at least one move by each side to be worth reviewing.
    static final int MIN_PLIES = 2;

    private static final Logger log = LoggerFactory.getLogger(AnalysisQueue.class);

    public enum EnqueueResult { QUEUED, NOT_FOUND, NOT_ENDED, TOO_SHORT, UNREADABLE }

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentLinkedQueue<Long> requested = new ConcurrentLinkedQueue<>();
    private final Semaphore wakeUp = new Semaphore(0);

    public AnalysisQueue(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    // Called when a game ends, from inside the game session's lock. Only notes the game and wakes the
    // worker, which replays and queues it on its own thread. If the app stops before then, startup
    // recovery finds the game by its missing review row.
    public void requestAnalysis(long gameId) {
        requested.add(gameId);
        wakeUp.release();
    }

    // Safe to call repeatedly: positions already queued are left alone. A review that FAILED is reset
    // so its failed positions are tried again.
    public EnqueueResult enqueueGame(long gameId) {
        List<Map<String, Object>> games = jdbcTemplate.queryForList(
                "SELECT ended_at, variant, starting_position FROM games WHERE id = ?", gameId);
        if (games.isEmpty()) return EnqueueResult.NOT_FOUND;
        Map<String, Object> game = games.getFirst();
        if (game.get("ended_at") == null) return EnqueueResult.NOT_ENDED;

        List<RecordedMove> moves = jdbcTemplate.query("""
                SELECT from_row, from_col, to_row, to_col, promotion_choice
                FROM game_moves WHERE game_id = ? ORDER BY move_number
                """,
                (rs, rowNum) -> new RecordedMove(
                        new Position(rs.getInt("from_row"), rs.getInt("from_col")),
                        new Position(rs.getInt("to_row"), rs.getInt("to_col")),
                        rs.getString("promotion_choice") == null ? null
                                : PromotionChoice.valueOf(rs.getString("promotion_choice"))),
                gameId);
        if (moves.size() < MIN_PLIES) return EnqueueResult.TOO_SHORT;

        boolean chess960 = "CHESS960".equals(game.get("variant"));
        List<ReplayedPosition> positions;
        try {
            positions = GameReplay.replay((String) game.get("starting_position"), chess960, moves);
        } catch (IllegalArgumentException e) {
            log.warn("Game {} can't be replayed for analysis: {}", gameId, e.getMessage());
            jdbcTemplate.update("""
                    INSERT INTO game_reviews (game_id, status, completed_at) VALUES (?, 'FAILED', now())
                    ON CONFLICT (game_id) DO NOTHING
                    """, gameId);
            return EnqueueResult.UNREADABLE;
        }

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO game_reviews (game_id, status) VALUES (?, 'ENGINE')
                    ON CONFLICT (game_id) DO NOTHING
                    """, gameId);
            jdbcTemplate.batchUpdate("""
                    INSERT INTO analysis_jobs (game_id, ply, epd, chess960) VALUES (?, ?, ?, ?)
                    ON CONFLICT (game_id, ply) DO NOTHING
                    """,
                    positions, positions.size(), (ps, position) -> {
                        ps.setLong(1, gameId);
                        ps.setInt(2, position.ply());
                        ps.setString(3, position.epd());
                        ps.setBoolean(4, chess960);
                    });
            int retried = jdbcTemplate.update("""
                    UPDATE game_reviews SET status = 'ENGINE', completed_at = NULL
                    WHERE game_id = ? AND status = 'FAILED'
                    """, gameId);
            if (retried > 0) {
                jdbcTemplate.update("""
                        UPDATE analysis_jobs SET status = 'QUEUED', attempts = 0
                        WHERE game_id = ? AND status = 'FAILED'
                        """, gameId);
            }
        });
        wakeUp.release();
        return EnqueueResult.QUEUED;
    }

    // Queues every game requested since the last call. Runs on the worker thread.
    void enqueueRequested() {
        Long gameId;
        while ((gameId = requested.poll()) != null) {
            try {
                enqueueGame(gameId);
            } catch (RuntimeException e) {
                log.error("Failed to queue game {} for analysis", gameId, e);
            }
        }
    }

    // Blocks until something may need doing or `timeout` passes, whichever is first.
    void awaitWork(Duration timeout) throws InterruptedException {
        if (wakeUp.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            wakeUp.drainPermits();
        }
    }
}

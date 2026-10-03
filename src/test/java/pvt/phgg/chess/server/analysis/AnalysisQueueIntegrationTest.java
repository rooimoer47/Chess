package pvt.phgg.chess.server.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import pvt.phgg.chess.UciMoveCodec;
import pvt.phgg.chess.UciMoveCodec.UciMove;
import pvt.phgg.chess.server.PostgresIntegrationTest;
import pvt.phgg.chess.server.analysis.AnalysisQueue.EnqueueResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** The analysis queue against real Postgres, with Stockfish replaced by a stub. */
@PostgresIntegrationTest
class AnalysisQueueIntegrationTest {

    // Nf3/Nc3 and Nc3/Nf3 orders reach the same position after four plies.
    private static final String KNIGHTS_KINGSIDE_FIRST = "g1f3 g8f6 b1c3 b8c6";
    private static final String KNIGHTS_QUEENSIDE_FIRST = "b1c3 b8c6 g1f3 g8f6";

    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        StubAnalyzer stubAnalyzer() {
            return new StubAnalyzer();
        }
    }

    static class StubAnalyzer implements PositionAnalyzer {
        final List<String> analyzed = Collections.synchronizedList(new ArrayList<>());
        volatile Predicate<String> failFor = epd -> false;
        volatile boolean available = true;

        @Override
        public PositionEval analyze(String fen, boolean chess960) throws AnalysisException {
            analyzed.add(fen);
            if (failFor.test(fen)) throw new AnalysisException("stub failure");
            return new PositionEval(Math.floorMod(fen.hashCode(), 100), null, "e2e4", "e2e4 e7e5", 16);
        }

        @Override
        public String engineName() {
            return "stub";
        }

        @Override
        public boolean isAvailable() {
            return available;
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AnalysisQueue queue;
    @Autowired AnalysisWorker worker;
    @Autowired StubAnalyzer analyzer;

    @BeforeEach
    void reset() {
        jdbc.execute("""
                TRUNCATE move_comments, analysis_jobs, game_reviews, position_evals,
                         game_moves, elo_history, games RESTART IDENTITY CASCADE
                """);
        analyzer.analyzed.clear();
        analyzer.failFor = epd -> false;
        analyzer.available = true;
    }

    private long endedGame(String uciMoves) {
        return game(uciMoves, "now()");
    }

    private long game(String uciMoves, String endedAtSql) {
        Long id = jdbc.queryForObject(
                "INSERT INTO games (mode, result, ended_at) VALUES ('HUMAN', 'RESIGNED', " + endedAtSql + ") RETURNING id",
                Long.class);
        int number = 0;
        for (String uci : uciMoves.isBlank() ? new String[0] : uciMoves.split(" ")) {
            UciMove m = UciMoveCodec.decode(uci);
            jdbc.update("""
                    INSERT INTO game_moves (game_id, move_number, from_row, from_col, to_row, to_col, promotion_choice)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, id, ++number, m.from().getRow(), m.from().getCol(), m.to().getRow(), m.to().getCol(),
                    m.promotion() == null ? null : m.promotion().name());
        }
        return id;
    }

    private void drain() {
        for (int i = 0; i < 1000 && worker.runOnce(); i++) {
            // keep going until the worker reports nothing left to do
        }
    }

    private String reviewStatus(long gameId) {
        List<String> status = jdbc.queryForList("SELECT status FROM game_reviews WHERE game_id = ?", String.class, gameId);
        return status.isEmpty() ? null : status.getFirst();
    }

    private int jobs(long gameId, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM analysis_jobs WHERE game_id = ? AND status = ?",
                Integer.class, gameId, status);
    }

    @Test
    void enqueueIsIdempotent() {
        long game = endedGame("e2e4 e7e5 g1f3");

        assertEquals(EnqueueResult.QUEUED, queue.enqueueGame(game));
        assertEquals(EnqueueResult.QUEUED, queue.enqueueGame(game));

        assertEquals(4, jobs(game, "QUEUED"), "one job per position, start included");
        assertEquals("ENGINE", reviewStatus(game));
    }

    @Test
    void onlyEndedGamesWithAMoveEachAreQueued() {
        long inProgress = game("e2e4 e7e5", "NULL");
        long oneMove = endedGame("e2e4");

        assertEquals(EnqueueResult.NOT_ENDED, queue.enqueueGame(inProgress));
        assertEquals(EnqueueResult.TOO_SHORT, queue.enqueueGame(oneMove));
        assertEquals(EnqueueResult.NOT_FOUND, queue.enqueueGame(999_999));
        assertNull(reviewStatus(inProgress));
        assertNull(reviewStatus(oneMove));
    }

    @Test
    void workerAnalysesEveryPositionAndClosesTheReview() {
        long game = endedGame("e2e4 e7e5 g1f3");
        queue.enqueueGame(game);

        drain();

        assertEquals(4, jobs(game, "DONE"));
        assertEquals("DONE", reviewStatus(game));
        assertEquals(4, analyzer.analyzed.size());
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM position_evals WHERE engine = 'stub'", Integer.class));
    }

    @Test
    void requestedGamesAreQueuedByTheWorker() {
        long game = endedGame("e2e4 e7e5");

        queue.requestAnalysis(game);
        drain();

        assertEquals("DONE", reviewStatus(game));
    }

    @Test
    void cachedPositionsAreNotAnalysedAgain() {
        long first = endedGame("e2e4 e7e5 g1f3");
        queue.enqueueGame(first);
        drain();
        analyzer.analyzed.clear();

        long second = endedGame("e2e4 e7e5 g1f3");
        queue.enqueueGame(second);
        drain();

        assertTrue(analyzer.analyzed.isEmpty(), "every position was already cached");
        assertEquals("DONE", reviewStatus(second));
    }

    @Test
    void transpositionReusesTheCachedEval() {
        queue.enqueueGame(endedGame(KNIGHTS_KINGSIDE_FIRST));
        drain();
        analyzer.analyzed.clear();

        queue.enqueueGame(endedGame(KNIGHTS_QUEENSIDE_FIRST));
        drain();

        // Shared: the start position and the final position. New: the three in between.
        assertEquals(3, analyzer.analyzed.size());
    }

    @Test
    void oneFailedPositionDoesNotBlockTheReview() {
        long game = endedGame("e2e4 e7e5 g1f3 b8c6 f1b5");
        queue.enqueueGame(game);
        String unlucky = jdbc.queryForObject("SELECT epd FROM analysis_jobs WHERE game_id = ? AND ply = 2", String.class, game);
        analyzer.failFor = unlucky::equals;

        drain();

        assertEquals(1, jobs(game, "FAILED"));
        assertEquals(3, jdbc.queryForObject("SELECT attempts FROM analysis_jobs WHERE game_id = ? AND ply = 2",
                Integer.class, game), "tried max-attempts times");
        assertEquals("DONE", reviewStatus(game));
    }

    @Test
    void reviewFailsWhenMostPositionsFail() {
        long game = endedGame("e2e4 e7e5 g1f3");
        queue.enqueueGame(game);
        analyzer.failFor = epd -> true;

        drain();

        assertEquals("FAILED", reviewStatus(game));
    }

    @Test
    void failedReviewIsRetriedWhenQueuedAgain() {
        long game = endedGame("e2e4 e7e5 g1f3");
        queue.enqueueGame(game);
        analyzer.failFor = epd -> true;
        drain();
        analyzer.failFor = epd -> false;

        queue.enqueueGame(game);
        drain();

        assertEquals("DONE", reviewStatus(game));
        assertEquals(4, jobs(game, "DONE"));
    }

    @Test
    void withoutStockfishJobsStayQueued() {
        long game = endedGame("e2e4 e7e5");
        queue.enqueueGame(game);
        analyzer.available = false;

        drain();

        assertEquals(3, jobs(game, "QUEUED"));
        assertEquals("ENGINE", reviewStatus(game));
    }

    @Test
    void recoveryRequeuesPositionsInterruptedMidAnalysis() {
        long game = endedGame("e2e4 e7e5");
        queue.enqueueGame(game);
        assertNotNull(worker.claim());
        assertEquals(1, jobs(game, "RUNNING"));

        worker.recover();

        assertEquals(0, jobs(game, "RUNNING"));
        drain();
        assertEquals("DONE", reviewStatus(game));
    }

    @Test
    void recoveryQueuesGamesThatEndedWithoutBeingQueued() {
        long missed = endedGame("e2e4 e7e5");
        long beforeAnalysisExisted = game("e2e4 e7e5", "TIMESTAMPTZ '2020-01-01 00:00:00+00'");

        worker.recover();

        assertEquals("ENGINE", reviewStatus(missed));
        assertNull(reviewStatus(beforeAnalysisExisted), "old games are only analysed on request");
    }

    @Test
    void recoveryClosesReviewsWhoseLastPositionFinishedBeforeAStop() {
        long game = endedGame("e2e4 e7e5");
        queue.enqueueGame(game);
        jdbc.update("UPDATE analysis_jobs SET status = 'DONE' WHERE game_id = ?", game);

        worker.recover();

        assertEquals("DONE", reviewStatus(game));
    }

    @Test
    void concurrentClaimsNeverGetTheSameJob() throws InterruptedException {
        for (int i = 0; i < 4; i++) {
            queue.enqueueGame(endedGame("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6"));
        }
        int total = jdbc.queryForObject("SELECT count(*) FROM analysis_jobs", Integer.class);

        Set<Long> claimedA = Collections.synchronizedSet(new HashSet<>());
        Set<Long> claimedB = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch go = new CountDownLatch(1);
        Thread a = claimer(go, claimedA);
        Thread b = claimer(go, claimedB);
        go.countDown();
        a.join(30_000);
        b.join(30_000);

        Set<Long> overlap = new HashSet<>(claimedA);
        overlap.retainAll(claimedB);
        assertTrue(overlap.isEmpty(), "jobs claimed twice: " + overlap);
        assertEquals(total, claimedA.size() + claimedB.size());
    }

    private Thread claimer(CountDownLatch go, Set<Long> claimed) {
        Thread t = new Thread(() -> {
            try {
                go.await();
            } catch (InterruptedException e) {
                return;
            }
            AnalysisWorker.Job job;
            while ((job = worker.claim()) != null) {
                claimed.add(job.id());
            }
        });
        t.start();
        return t;
    }
}

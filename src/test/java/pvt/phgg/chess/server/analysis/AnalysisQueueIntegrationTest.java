package pvt.phgg.chess.server.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pvt.phgg.chess.server.PostgresIntegrationTest;
import pvt.phgg.chess.server.analysis.AnalysisQueue.EnqueueResult;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/** The analysis queue against real Postgres, with Stockfish replaced by a stub. */
@PostgresIntegrationTest
@Import(StubAnalyzerConfig.class)
class AnalysisQueueIntegrationTest {

    // Nf3/Nc3 and Nc3/Nf3 orders reach the same position after four plies.
    private static final String KNIGHTS_KINGSIDE_FIRST = "g1f3 g8f6 b1c3 b8c6";
    private static final String KNIGHTS_QUEENSIDE_FIRST = "b1c3 b8c6 g1f3 g8f6";

    @Autowired JdbcTemplate jdbc;
    @Autowired AnalysisQueue queue;
    @Autowired AnalysisWorker worker;
    @Autowired StubAnalyzerConfig.StubAnalyzer analyzer;

    @BeforeEach
    void reset() {
        TestGames.clear(jdbc);
        analyzer.reset();
    }

    private long endedGame(String uciMoves) {
        return TestGames.ended(jdbc, uciMoves);
    }

    private long game(String uciMoves, String endedAtSql) {
        return TestGames.insert(jdbc, uciMoves, endedAtSql, null, null);
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

    private static final String START_EPD = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -";

    private void cache(String epd, int depth, String engine) {
        jdbc.update("""
                INSERT INTO position_evals (epd, chess960, depth, eval_cp, best_uci, pv_uci, engine)
                VALUES (?, false, ?, 20, 'e2e4', 'e2e4', ?)
                """, epd, depth, engine);
    }

    private Integer cachedDepth(String epd) {
        return jdbc.queryForObject("SELECT depth FROM position_evals WHERE epd = ? AND chess960 = false",
                Integer.class, epd);
    }

    @Test
    void promotionsAreReadBackFromTheDatabase() {
        long game = endedGame("a2a4 b7b5 a4b5 a7a6 b5a6 c8b7 a6b7 b8c6 b7a8n");

        assertEquals(EnqueueResult.QUEUED, queue.enqueueGame(game));
        drain();

        assertEquals(10, jobs(game, "DONE"));
        assertEquals("DONE", reviewStatus(game));
    }

    @Test
    void gameThatCannotBeReplayedFailsItsReview() {
        long game = endedGame("e2e4 e7e5 e4e6");  // the third move isn't legal

        assertEquals(EnqueueResult.UNREADABLE, queue.enqueueGame(game));

        assertEquals("FAILED", reviewStatus(game));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM analysis_jobs WHERE game_id = ?", Integer.class, game));
    }

    /** Raising the configured depth: positions cached by a shallower search are analysed again. */
    @Test
    void shallowerCachedEvalIsAnalysedAgain() {
        cache(START_EPD, 10, "old");
        queue.enqueueGame(endedGame("e2e4 e7e5"));
        drain();

        assertTrue(analyzer.analyzed.contains(START_EPD));
        assertEquals(16, cachedDepth(START_EPD));
    }

    /** Lowering the configured depth: deeper cached results still count. */
    @Test
    void deeperCachedEvalIsUsed() {
        cache(START_EPD, 30, "deep");
        queue.enqueueGame(endedGame("e2e4 e7e5"));
        drain();

        assertFalse(analyzer.analyzed.contains(START_EPD));
        assertEquals(30, cachedDepth(START_EPD));
    }

    @Test
    void savingNeverReplacesADeeperEval() {
        cache(START_EPD, 10, "old");
        // An engine that answers shallower than what's already stored (e.g. stopped early).
        analyzer.answers.put(START_EPD, new PositionEval(5, null, "d2d4", "d2d4", 8));
        queue.enqueueGame(endedGame("e2e4 e7e5"));
        drain();

        assertEquals(10, cachedDepth(START_EPD));
        assertEquals("old", jdbc.queryForObject("SELECT engine FROM position_evals WHERE epd = ?", String.class, START_EPD));
    }

    /** The background thread production runs, rather than tests calling runOnce() themselves. */
    @Test
    void workerThreadIsWokenByARequestAndStopsCleanly() throws InterruptedException {
        // A long poll interval: the game only gets analysed in time if the request wakes the worker.
        AnalysisProperties properties = new AnalysisProperties(true, "unused", 16, 1, 32, 0, 6, 3,
                Duration.ofSeconds(30), Duration.ofSeconds(5));
        AnalysisWorker running = new AnalysisWorker(jdbc, queue, analyzer, properties);
        queue.awaitWork(Duration.ZERO);  // clear wake-ups left over from earlier tests
        running.start();
        try {
            Thread.sleep(300);  // let it run recovery and go idle
            long game = endedGame("e2e4 e7e5 g1f3");

            queue.requestAnalysis(game);

            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!"DONE".equals(reviewStatus(game)) && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals("DONE", reviewStatus(game));
        } finally {
            running.stop();
        }
        assertTrue(Thread.getAllStackTraces().keySet().stream()
                        .noneMatch(t -> t.getName().equals("analysis-worker") && t.isAlive()),
                "worker thread has stopped");
    }
}

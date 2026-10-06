package pvt.phgg.chess.server.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.GameReplay;
import pvt.phgg.chess.GameReplay.ReplayedPosition;
import pvt.phgg.chess.server.PostgresIntegrationTest;
import pvt.phgg.chess.server.analysis.CommentaryWorker.Step;
import pvt.phgg.chess.server.analysis.llm.LlmClient;
import pvt.phgg.chess.server.analysis.llm.LlmException;
import pvt.phgg.chess.server.analysis.llm.LlmReply;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The LLM step against real Postgres, with the model replaced by a stub. */
@PostgresIntegrationTest
@Import(StubAnalyzerConfig.class)
class CommentaryWorkerIntegrationTest {

    // 1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6?? 4.Qxf7#
    private static final String SCHOLARS_MATE = "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7";
    private static final String GOOD_COMMENT = "Nf6 ignores the threat to f7, and White mates at once with Qxf7#.";

    @Autowired JdbcTemplate jdbc;
    @Autowired GameAnalysisService analysisService;
    @Autowired AnalysisQueue queue;
    @Autowired AnalysisProperties analysisProperties;
    @Autowired StubAnalyzerConfig.StubAnalyzer analyzer;

    private final StubLlm llm = new StubLlm();
    private final CommentaryProperties properties =
            new CommentaryProperties(true, 12, Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofMinutes(1));
    private CommentaryWorker worker;

    static class StubLlm implements LlmClient {
        final List<String> prompts = new ArrayList<>();
        final List<String> answers = new ArrayList<>();
        boolean reachable = true;
        boolean failing;

        @Override
        public LlmReply generate(String system, String user) throws LlmException {
            if (failing) throw new LlmException("stub failure", null);
            prompts.add(user);
            String answer = answers.isEmpty() ? GOOD_COMMENT : answers.removeFirst();
            return new LlmReply(answer, 20, Duration.ofMillis(100));
        }

        @Override
        public String modelName() {
            return "stub-model";
        }

        @Override
        public boolean prepare() {
            return reachable;
        }
    }

    @BeforeEach
    void reset() {
        TestGames.clear(jdbc);
        analyzer.reset();
        worker = new CommentaryWorker(jdbc, analysisService, llm, properties, analysisProperties);
    }

    // Scholar's mate, already analysed, waiting for comments.
    private long analysedScholarsMate() {
        long game = TestGames.ended(jdbc, SCHOLARS_MATE);
        List<ReplayedPosition> positions = GameReplay.replay(GameEngine.STANDARD_BACK_RANK, false,
                TestGames.moves(SCHOLARS_MATE));
        String[] pvs = {"e2e4 e7e5", "e7e5 g1f3", "g1f3 b8c6", "b8c6 d1h5", "d1h5 g8f6", "g7g6 h5f3", "h5f7", null};
        Integer[] cps = {30, 30, 30, 10, 20, 40, null, null};
        Integer[] mates = {null, null, null, null, null, null, 1, 0};
        for (int ply = 0; ply < positions.size(); ply++) {
            jdbc.update("""
                    INSERT INTO position_evals (epd, chess960, depth, eval_cp, mate_in, best_uci, pv_uci, engine)
                    VALUES (?, false, 16, ?, ?, ?, ?, 'stub')
                    """, positions.get(ply).epd(), cps[ply], mates[ply],
                    pvs[ply] == null ? null : pvs[ply].split(" ")[0], pvs[ply]);
        }
        jdbc.update("INSERT INTO game_reviews (game_id, status) VALUES (?, 'COMMENTING')", game);
        return game;
    }

    private String status(long game) {
        return jdbc.queryForObject("SELECT status FROM game_reviews WHERE game_id = ?", String.class, game);
    }

    private List<String> comments(long game) {
        return jdbc.queryForList("SELECT ply || ': ' || comment FROM move_comments WHERE game_id = ? ORDER BY ply",
                String.class, game);
    }

    @Test
    void commentsTheBlunderAndFinishes() {
        long game = analysedScholarsMate();

        assertEquals(Step.COMMENTED, worker.runOnce());

        assertEquals(List.of("6: " + GOOD_COMMENT), comments(game));
        assertEquals("DONE", status(game));
        assertEquals("stub-model", jdbc.queryForObject("SELECT llm_model FROM game_reviews WHERE game_id = ?", String.class, game));
        assertEquals(1, llm.prompts.size());
        assertTrue(llm.prompts.getFirst().contains("3...Nf6 allows White to force checkmate in 1."), llm.prompts.getFirst());
        // The review endpoint shows it on the move.
        assertEquals(GOOD_COMMENT, analysisService.analysis(game).orElseThrow().moves().get(5).comment());
        assertEquals(Step.IDLE, worker.runOnce());
    }

    @Test
    void anAnswerNamingOtherSquaresIsRetriedOnceThenDropped() {
        long game = analysedScholarsMate();
        llm.answers.addAll(List.of("The knight on g8 should stay home.", "Black should play h6."));

        worker.runOnce();

        assertEquals(2, llm.prompts.size());
        assertEquals(List.of(), comments(game));
        assertEquals("DONE", status(game), "a dropped comment doesn't hold the review back");
    }

    @Test
    void aRetryCanSucceed() {
        long game = analysedScholarsMate();
        llm.answers.add("The knight on g8 should stay home.");

        worker.runOnce();

        assertEquals(List.of("6: " + GOOD_COMMENT), comments(game));
    }

    @Test
    void anUnreachableModelLeavesTheReviewWaiting() {
        long game = analysedScholarsMate();
        llm.reachable = false;

        assertEquals(Step.LLM_UNAVAILABLE, worker.runOnce());
        assertEquals("COMMENTING", status(game));
        assertTrue(llm.prompts.isEmpty());

        llm.reachable = true;
        assertEquals(Step.COMMENTED, worker.runOnce());
        assertEquals("DONE", status(game));
    }

    @Test
    void aFailingModelLeavesTheReviewWaiting() {
        long game = analysedScholarsMate();
        llm.failing = true;

        assertEquals(Step.LLM_UNAVAILABLE, worker.runOnce());
        assertEquals("COMMENTING", status(game));
    }

    @Test
    void commentsAlreadyWrittenAreKept() {
        long game = analysedScholarsMate();
        jdbc.update("INSERT INTO move_comments (game_id, ply, comment) VALUES (?, 6, 'written before a restart')", game);

        worker.runOnce();

        assertTrue(llm.prompts.isEmpty());
        assertEquals(List.of("6: written before a restart"), comments(game));
        assertEquals("DONE", status(game));
    }

    @Test
    void theEngineStageHandsOverToCommentary() {
        AnalysisWorker engine = new AnalysisWorker(jdbc, queue, analyzer, analysisProperties, properties, worker);
        long game = TestGames.ended(jdbc, "e2e4 e7e5 g1f3");
        queue.enqueueGame(game);
        for (int i = 0; i < 100 && engine.runOnce(); i++) {
            // analyse every position
        }

        assertEquals("COMMENTING", status(game));
        assertNull(jdbc.queryForObject("SELECT completed_at FROM game_reviews WHERE game_id = ?", Object.class, game));
        // While comments are written, the engine part of the review is already shown.
        assertEquals(3, analysisService.analysis(game).orElseThrow().moves().size());

        worker.runOnce();
        assertEquals("DONE", status(game));
    }
}

package pvt.phgg.chess.server.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.GameReplay;
import pvt.phgg.chess.GameReplay.ReplayedPosition;
import pvt.phgg.chess.server.PostgresIntegrationTest;
import pvt.phgg.chess.server.analysis.GameAnalysisService.GameAnalysis;
import pvt.phgg.chess.server.analysis.GameAnalysisService.MoveReview;
import pvt.phgg.chess.server.game.GameAccess;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The review the replay viewer reads, built from the queue's results (stub engine, real Postgres). */
@PostgresIntegrationTest
@Import(StubAnalyzerConfig.class)
class GameAnalysisServiceIntegrationTest {

    // 3...f6 is given a losing evaluation below, so it comes out as a blunder.
    private static final String GAME = "e2e4 e7e5 g1f3 f7f6";

    @Autowired JdbcTemplate jdbc;
    @Autowired AnalysisQueue queue;
    @Autowired AnalysisWorker worker;
    @Autowired GameAnalysisService service;
    @Autowired GameAccess gameAccess;
    @Autowired StubAnalyzerConfig.StubAnalyzer analyzer;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        TestGames.clear(jdbc);
        jdbc.update("DELETE FROM users WHERE username LIKE 'review_%'");
        analyzer.reset();
    }

    private void drain() {
        for (int i = 0; i < 1000 && worker.runOnce(); i++) {
            // analyse everything queued
        }
    }

    private static List<String> epds(String uciMoves) {
        return GameReplay.replay(GameEngine.STANDARD_BACK_RANK, false, TestGames.moves(uciMoves)).stream()
                .map(ReplayedPosition::epd).toList();
    }

    @Test
    void doneReviewHasEveryMoveWithLabelsAndEngineAdvice() {
        List<String> epd = epds(GAME);
        analyzer.answers.put(epd.get(2), new PositionEval(30, null, "g1f3", "g1f3 b8c6 f1b5", 16));
        analyzer.answers.put(epd.get(3), new PositionEval(30, null, "b8c6", "b8c6 f1b5", 16));
        analyzer.answers.put(epd.get(4), new PositionEval(600, null, "f3e5", "f3e5", 16));
        long game = TestGames.ended(jdbc, GAME);
        queue.enqueueGame(game);
        drain();

        GameAnalysis analysis = service.analysis(game).orElseThrow();

        assertEquals("DONE", analysis.status());
        assertEquals(5, analysis.positionsTotal());
        assertEquals(5, analysis.positionsDone());
        assertEquals(5, analysis.evals().size(), "one per position, start included");
        assertEquals(600, analysis.evals().get(4).cp());
        assertEquals(List.of("e4", "e5", "Nf3", "f6"), analysis.moves().stream().map(MoveReview::san).toList());

        MoveReview nf3 = analysis.moves().get(2);
        assertEquals(3, nf3.ply());
        assertEquals("g1", nf3.from());
        assertEquals("f3", nf3.to());
        assertEquals(MoveClassification.BEST, nf3.classification(), "played the engine's move");

        MoveReview f6 = analysis.moves().get(3);
        assertEquals(MoveClassification.BLUNDER, f6.classification());
        assertEquals("Nc6", f6.bestSan());
        assertEquals("b8c6", f6.bestUci());
        assertEquals("b8", f6.bestFrom());
        assertEquals("c6", f6.bestTo());
        assertEquals(List.of("Nc6", "Bb5"), f6.line());
        assertNull(f6.comment());
    }

    @Test
    void positionWithoutEvaluationGivesUnknownMoves() {
        List<String> epd = epds(GAME);
        analyzer.failFor = epd.get(2)::equals;
        long game = TestGames.ended(jdbc, GAME);
        queue.enqueueGame(game);
        drain();

        GameAnalysis analysis = service.analysis(game).orElseThrow();

        assertEquals("DONE", analysis.status(), "one failure in five doesn't fail the review");
        assertNull(analysis.evals().get(2));
        assertEquals(MoveClassification.UNKNOWN, analysis.moves().get(1).classification(), "the move into it");
        assertEquals(MoveClassification.UNKNOWN, analysis.moves().get(2).classification(), "the move out of it");
        assertNull(analysis.moves().get(2).bestSan());
        assertEquals(List.of(), analysis.moves().get(2).line());
    }

    @Test
    void notQueuedAndInProgressReviewsOnlyReportProgress() {
        long game = TestGames.ended(jdbc, GAME);
        assertEquals("NONE", service.analysis(game).orElseThrow().status());

        queue.enqueueGame(game);
        worker.runOnce();
        GameAnalysis analysis = service.analysis(game).orElseThrow();

        assertEquals("ENGINE", analysis.status());
        assertEquals(5, analysis.positionsTotal());
        assertEquals(1, analysis.positionsDone());
        assertTrue(analysis.moves().isEmpty());
        assertTrue(analysis.evals().isEmpty());
    }

    @Test
    void liveAndMissingGamesHaveNoReview() {
        long live = TestGames.insert(jdbc, GAME, "NULL", null, null);

        assertTrue(service.analysis(live).isEmpty());
        assertTrue(service.analysis(999_999).isEmpty());
    }

    @Test
    void onlyTheTwoPlayersHaveAccess() {
        Long white = jdbc.queryForObject(
                "INSERT INTO users (username, password_hash) VALUES ('review_white', 'x') RETURNING id", Long.class);
        jdbc.update("INSERT INTO users (username, password_hash) VALUES ('review_stranger', 'x')");
        long game = TestGames.insert(jdbc, GAME, "now()", white, null);

        assertTrue(gameAccess.isPlayer("review_white", game));
        assertFalse(gameAccess.isPlayer("review_stranger", game));
        assertFalse(gameAccess.isPlayer("review_nobody", game));
    }

    /** The cache is keyed by variant as well as position; a 960 review must look in the 960 cache. */
    @Test
    void chess960GameIsReviewedFromThe960Cache() {
        long game = TestGames.ended960(jdbc, "RNBKQBNR", "b1c3 a7a6 d2d3 a6a5 c1e3 h7h6 d1a1 b7b6");
        queue.enqueueGame(game);
        drain();

        GameAnalysis analysis = service.analysis(game).orElseThrow();

        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT bool_and(chess960) FROM analysis_jobs WHERE game_id = ?", Boolean.class, game),
                "every position queued as a 960 position");
        assertEquals("DONE", analysis.status());
        assertTrue(analysis.evals().stream().allMatch(e -> e != null), "every position found in the 960 cache");
        MoveReview castle = analysis.moves().get(6);
        assertEquals("O-O-O", castle.san());
        assertEquals("d1a1", castle.uci());
        assertEquals("d1", castle.from());
        assertEquals("c1", castle.to(), "arrow goes to where the king lands, not onto the rook");
        assertNotEquals(MoveClassification.UNKNOWN, castle.classification());
    }

    @Test
    void commentsAttachToTheMoveTheyDescribe() {
        long game = TestGames.ended(jdbc, GAME);
        queue.enqueueGame(game);
        drain();
        // ply 4 is the position after Black's 2...f6, i.e. the fourth move.
        jdbc.update("INSERT INTO move_comments (game_id, ply, comment) VALUES (?, 4, 'Weakens the king.')", game);

        List<MoveReview> moves = service.analysis(game).orElseThrow().moves();

        assertEquals("f6", moves.get(3).san());
        assertEquals("Weakens the king.", moves.get(3).comment());
        assertTrue(moves.subList(0, 3).stream().allMatch(m -> m.comment() == null));
    }

    private static Set<String> fieldNames(JsonNode node) {
        return new HashSet<>(node.propertyNames());
    }

    /**
     * The JSON the replay viewer reads, as the app's own mapper writes it. Field names must match
     * GameAnalysis / MoveReview / Eval in frontend/src/types.ts, and nulls must be written out
     * (the frontend checks for null, not a missing field).
     */
    @Test
    void reviewJsonMatchesTheFrontendTypes() {
        List<String> epd = epds(GAME);
        analyzer.failFor = epd.get(1)::equals;  // one missing evaluation, so a null appears in evals
        long game = TestGames.ended(jdbc, GAME);
        queue.enqueueGame(game);
        drain();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(service.analysis(game).orElseThrow()));

        assertEquals(Set.of("status", "positionsTotal", "positionsDone", "evals", "moves"), fieldNames(json));
        assertEquals("DONE", json.get("status").asString());
        assertTrue(json.get("evals").get(1).isNull(), "missing evaluation written as null");
        assertEquals(Set.of("cp", "mate"), fieldNames(json.get("evals").get(0)));
        assertTrue(json.get("evals").get(0).get("mate").isNull());

        JsonNode move = json.get("moves").get(0);
        assertEquals(Set.of("ply", "san", "uci", "from", "to", "classification", "bestSan", "bestUci",
                "bestFrom", "bestTo", "line", "comment"), fieldNames(move));
        assertEquals(1, move.get("ply").asInt());
        assertEquals("UNKNOWN", move.get("classification").asString(), "enum written by name");
        assertTrue(move.get("line").isArray());
        assertTrue(move.get("comment").isNull());
    }
}

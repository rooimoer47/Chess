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
import pvt.phgg.chess.server.analysis.GameAnalysisService.LoadedGame;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The mistake memory against real Postgres and pgvector, with a stub embedder. */
@PostgresIntegrationTest
@Import(StubAnalyzerConfig.class)
class MistakeMemoryIntegrationTest {

    // 1.e4 e5 2.Nf3 Nc6 3.Bc4 Nf6 4.Ng5 Be7?, and 5.Nxf7 forks queen and rook. Black's ply 8.
    private static final String TWO_KNIGHTS = "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 f3g5 f8e7";
    // 1.e4 e5 2.Nf3 f6 3.Nxe5 fxe5?, and 4.Qh5+ forks king and pawn. Black's ply 6.
    private static final String DAMIANO = "e2e4 e7e5 g1f3 f7f6 f3e5 f6e5";

    @Autowired JdbcTemplate jdbc;
    @Autowired GameAnalysisService analysisService;

    private final StubEmbedder embedder = new StubEmbedder();
    private MistakeMemory memory;
    private long player;
    private long other;

    @BeforeEach
    void reset() {
        TestGames.clear(jdbc);
        jdbc.update("DELETE FROM users WHERE username LIKE 'memory_%'");
        player = user("memory_player");
        other = user("memory_other");
        memory = new MistakeMemory(jdbc, embedder);
    }

    private long user(String name) {
        return jdbc.queryForObject("INSERT INTO users (username, password_hash) VALUES (?, 'x') RETURNING id", Long.class, name);
    }

    // The game with the evaluations around its last move, which is Black's blunder.
    private long game(String moves, String replyLine, String endedAtSql, Long white, Long black) {
        long game = TestGames.insert(jdbc, moves, endedAtSql, white, black);
        List<ReplayedPosition> positions = GameReplay.replay(GameEngine.STANDARD_BACK_RANK, false, TestGames.moves(moves));
        int last = positions.size() - 1;
        eval(positions.get(last - 1).epd(), 60, "d7d5 e4d5");
        eval(positions.get(last).epd(), 600, replyLine);
        return game;
    }

    private long twoKnights(String endedAtSql, Long white, Long black) {
        return game(TWO_KNIGHTS, "g5f7 d7d5 f7d8", endedAtSql, white, black);
    }

    private long damiano(String endedAtSql, Long white, Long black) {
        return game(DAMIANO, "d1h5 e8e7 h5e5", endedAtSql, white, black);
    }

    private void eval(String epd, int cp, String pv) {
        jdbc.update("""
                INSERT INTO position_evals (epd, chess960, depth, eval_cp, best_uci, pv_uci, engine)
                VALUES (?, false, 16, ?, ?, ?, 'stub') ON CONFLICT DO NOTHING
                """, epd, cp, pv.split(" ")[0], pv);
    }

    private LoadedGame load(long game) {
        return analysisService.load(game).orElseThrow();
    }

    private Optional<String> recallLastMove(long game) {
        LoadedGame loaded = load(game);
        int index = loaded.replay().positions().size() - 2;
        return memory.recall(game, loaded, MoveFacts.describe(loaded, index, MoveClassification.BLUNDER, false));
    }

    @Test
    void storesAPlayersMistakeWithItsMotifAndDescription() {
        long game = twoKnights("now()", null, player);

        memory.record(game, load(game));
        memory.record(game, load(game));  // twice is once

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT user_id, ply, san, classification, array_to_string(motifs, ',') AS motifs, description, embedding FROM mistakes");
        assertEquals(player, row.get("user_id"));
        assertEquals(8, row.get("ply"));
        assertEquals("Be7", row.get("san"));
        assertEquals("BLUNDER", row.get("classification"));
        assertEquals("FORK", row.get("motifs"));
        assertEquals("Black ended up 7 pawns' worth of material behind. "
                + "Black allowed a fork: White's knight attacked Black's queen and rook at once. "
                + "Opening: Italian Game: Two Knights Defense, Knight Attack (C57).", row.get("description"));
        assertNull(row.get("embedding"), "embedded later");
    }

    @Test
    void aBotsMistakesAreNotStored() {
        long game = twoKnights("now()", player, null);  // the blunder is Black's, and Black is the bot

        memory.record(game, load(game));

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM mistakes", Integer.class));
    }

    @Test
    void recallsTheSameMotifAndTheSameMoveFromEarlierGames() {
        long first = damiano("now() - interval '3 days'", null, player);
        long second = twoKnights("now() - interval '2 days'", null, player);
        long third = twoKnights("now()", null, player);
        for (long game : List.of(first, second, third)) memory.record(game, load(game));

        assertEquals(Optional.empty(), recallLastMove(first), "nothing came before it");
        assertEquals(Optional.of("Black has allowed a fork in 1 earlier game."), recallLastMove(second),
                "the later game doesn't count");
        assertEquals(Optional.of("Black has played this same move in this same position in 1 earlier game. "
                + "Black has allowed a fork in 2 earlier games."), recallLastMove(third));
    }

    @Test
    void anotherPlayersMistakesDontCount() {
        long theirs = twoKnights("now() - interval '1 day'", null, other);
        long mine = twoKnights("now()", null, player);
        memory.record(theirs, load(theirs));
        memory.record(mine, load(mine));

        assertEquals(Optional.empty(), recallLastMove(mine));
    }

    @Test
    void aPlayersHistoryIsNotToldWhenTheOpponentReadsTheReviewToo() {
        long earlier = twoKnights("now() - interval '1 day'", null, player);
        long againstAPlayer = twoKnights("now()", other, player);
        memory.record(earlier, load(earlier));
        memory.record(againstAPlayer, load(againstAPlayer));

        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM mistakes WHERE user_id = ?", Integer.class, player),
                "stored all the same");
        assertEquals(Optional.empty(), recallLastMove(againstAPlayer));
    }

    @Test
    void embedsWhatIsWaitingAndAgainAfterAChangeOfModel() {
        long game = twoKnights("now()", null, player);
        memory.record(game, load(game));

        embedder.available = false;
        assertEquals(0, memory.embedPending(), "waits for the model");
        embedder.available = true;
        assertEquals(1, memory.embedPending());
        assertEquals("stub-embedder", jdbc.queryForObject("SELECT model FROM mistakes", String.class));
        assertEquals(StubEmbedder.DIMENSIONS, jdbc.queryForObject("SELECT vector_dims(embedding) FROM mistakes", Integer.class));
        assertEquals(0, memory.embedPending(), "nothing left");

        memory.record(game, load(game));
        assertEquals(0, memory.embedPending(), "an unchanged description keeps its embedding");

        embedder.model = "another-embedder";
        assertEquals(1, memory.embedPending());
        embedder.failing = true;
        embedder.model = "a-third";
        assertEquals(0, memory.embedPending(), "a failure is not passed on");
    }

    @Test
    void mistakesCanBeSearchedByWhatHappened() {
        long fork = twoKnights("now() - interval '1 day'", null, player);
        memory.record(fork, load(fork));
        // 1.e4 d5 2.Qg4?? as White.
        long hung = TestGames.insert(jdbc, "e2e4 d7d5 d1g4", "now()", player, null);
        List<ReplayedPosition> positions = GameReplay.replay(GameEngine.STANDARD_BACK_RANK, false, TestGames.moves("e2e4 d7d5 d1g4"));
        eval(positions.get(2).epd(), 70, "e4d5 g8f6");
        eval(positions.get(3).epd(), -960, "c8g4 e4e5");
        memory.record(hung, load(hung));
        memory.embedPending();

        String nearest = jdbc.queryForObject("""
                SELECT array_to_string(motifs, ',') FROM mistakes WHERE user_id = ?
                ORDER BY embedding <=> ?::vector LIMIT 1
                """, String.class, player, OpeningNotes.vector(StubEmbedder.vector("a queen left hanging and taken for nothing")));
        assertEquals("HANGING_PIECE", nearest);
    }
}

package pvt.phgg.chess.server.analysis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talks to a real Stockfish. Only runs when STOCKFISH_PATH points at the binary, e.g.
 * {@code STOCKFISH_PATH=/path/to/stockfish mvn test -Dtest=StockfishAnalyzerBinaryTest}.
 */
@EnabledIfEnvironmentVariable(named = "STOCKFISH_PATH", matches = ".+")
class StockfishAnalyzerBinaryTest {

    private static StockfishAnalyzer analyzer;

    @BeforeAll
    static void start() {
        analyzer = new StockfishAnalyzer(new AnalysisProperties(false, System.getenv("STOCKFISH_PATH"),
                12, 1, 16, 10, 6, 3, Duration.ofSeconds(1), Duration.ofSeconds(30)));
    }

    @AfterAll
    static void stop() {
        analyzer.destroy();
    }

    @Test
    void reportsItsName() throws AnalysisException {
        assertTrue(analyzer.isAvailable());
        assertTrue(analyzer.engineName().startsWith("Stockfish"), analyzer.engineName());
    }

    @Test
    void startPositionIsRoughlyLevel() throws AnalysisException {
        PositionEval eval = analyzer.analyze("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -", false);

        assertNotNull(eval.evalCp());
        assertTrue(Math.abs(eval.evalCp()) < 100, "eval " + eval.evalCp());
        assertNotNull(eval.bestUci());
        assertEquals(12, eval.depth());
    }

    @Test
    void scoreIsFromWhitesViewWhenBlackIsToMove() throws AnalysisException {
        // White is a queen up; Black to move.
        PositionEval eval = analyzer.analyze("rnb1kbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR b KQkq -", false);

        assertTrue(eval.evalCp() > 500, "eval " + eval.evalCp());
    }

    @Test
    void findsMateInOne() throws AnalysisException {
        PositionEval eval = analyzer.analyze("6k1/5ppp/8/8/8/8/8/R5K1 w - -", false);

        assertEquals(1, eval.mateIn());
        assertEquals("a1a8", eval.bestUci());
    }

    @Test
    void checkmatedAndStalematedPositions() throws AnalysisException {
        PositionEval mated = analyzer.analyze("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq -", false);
        assertEquals(0, mated.mateIn());
        assertNull(mated.bestUci());

        PositionEval stalemate = analyzer.analyze("7k/5Q2/6K1/8/8/8/8/8 b - -", false);
        assertEquals(0, stalemate.evalCp());
        assertNull(stalemate.bestUci());
    }

    @Test
    void chess960CastleComesBackAsKingOntoRook() throws AnalysisException {
        // White Kc1 with its queenside rook on b1 (castling right "B"). Castling leaves the king on c1
        // and puts the rook on d1, mating the boxed-in king on d8. The king blocks the rook's own
        // path, so castling is the only mate in one.
        PositionEval eval = analyzer.analyze("2rkr3/2p1p3/8/8/8/8/PP6/1RK5 w B -", true);

        assertEquals(1, eval.mateIn());
        assertEquals("c1b1", eval.bestUci(), "960 castling is the king moving onto its own rook");
        // Switching back to standard mode still works.
        assertNotNull(analyzer.analyze("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq -", false).bestUci());
    }
}

package pvt.phgg.chess.server.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Output lines are copied from Stockfish 19. Scores are stored from White's point of view. */
class StockfishAnalyzerParseTest {

    private static PositionEval parse(boolean blackToMove, String... lines) throws AnalysisException {
        return StockfishAnalyzer.parse(List.of(lines), blackToMove, 16, 6);
    }

    @Test
    void whiteToMoveKeepsTheSign() throws AnalysisException {
        PositionEval eval = parse(false,
                "info string NNUE evaluation using nn-1a298aa575a0.nnue",
                "info depth 15 seldepth 20 multipv 1 score cp 35 nodes 90000 nps 1000000 time 90 pv e2e4 e7e5",
                "info depth 16 seldepth 23 multipv 1 score cp 39 nodes 75655 nps 1050763 hashfull 22 tbhits 0 time 72 pv e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6",
                "bestmove e2e4 ponder c7c5");

        assertEquals(39, eval.evalCp());
        assertNull(eval.mateIn());
        assertEquals("e2e4", eval.bestUci());
        assertEquals("e2e4 c7c5 g1f3 d7d6 d2d4 c5d4", eval.pvUci(), "PV is cut to 6 plies");
        assertEquals(16, eval.depth());
    }

    @Test
    void blackToMoveIsNegated() throws AnalysisException {
        PositionEval eval = parse(true,
                "info depth 16 seldepth 25 multipv 1 score cp -32 nodes 208719 nps 1033262 hashfull 69 tbhits 0 time 202 pv c7c5 g1f3",
                "bestmove c7c5 ponder g1f3");

        assertEquals(32, eval.evalCp(), "Black is worse by 32, so White is better by 32");
        assertEquals("c7c5 g1f3", eval.pvUci());
    }

    @Test
    void mateScoresAreFromWhitesView() throws AnalysisException {
        assertEquals(1, parse(false,
                "info depth 16 seldepth 2 multipv 1 score mate 1 nodes 272 nps 272000 hashfull 0 tbhits 0 time 1 pv a1a8",
                "bestmove a1a8").mateIn());
        assertEquals(-3, parse(false,
                "info depth 16 seldepth 8 multipv 1 score mate -3 nodes 900 time 2 pv g1h1 a1a8",
                "bestmove g1h1").mateIn(), "White to move and mated in 3");
        assertEquals(2, parse(true,
                "info depth 16 seldepth 6 multipv 1 score mate -2 nodes 900 time 2 pv g8h8 a1a8",
                "bestmove g8h8").mateIn(), "Black to move and mated in 2 = White mates in 2");
    }

    @Test
    void checkmatedPosition() throws AnalysisException {
        PositionEval eval = parse(false, "info depth 0 score mate 0", "bestmove (none)");

        assertEquals(0, eval.mateIn());
        assertNull(eval.evalCp());
        assertNull(eval.bestUci());
        assertNull(eval.pvUci());
        assertEquals(16, eval.depth(), "Exact at any depth, so stored at the requested depth");
    }

    @Test
    void stalematePosition() throws AnalysisException {
        PositionEval eval = parse(true, "info depth 0 score cp 0", "bestmove (none)");

        assertEquals(0, eval.evalCp());
        assertNull(eval.bestUci());
        assertEquals(16, eval.depth());
    }

    @Test
    void boundScoresAndOtherLinesAreSkipped() throws AnalysisException {
        PositionEval eval = parse(false,
                "info depth 16 seldepth 20 multipv 1 score cp 50 nodes 1 time 1 pv d2d4",
                "info depth 16 seldepth 20 multipv 2 score cp 10 nodes 1 time 1 pv a2a3",
                "info depth 17 seldepth 21 multipv 1 score cp 90 lowerbound nodes 2 time 2 pv e2e4",
                "info depth 17 currmove e2e4 currmovenumber 1",
                "bestmove d2d4");

        assertEquals(50, eval.evalCp());
        assertEquals("d2d4", eval.pvUci());
    }

    @Test
    void missingBestMoveOrScoreIsAnError() {
        assertThrows(AnalysisException.class, () -> parse(false, "info depth 16 multipv 1 score cp 5 pv e2e4"));
        assertThrows(AnalysisException.class, () -> parse(false, "info string hello", "bestmove e2e4"));
    }
}

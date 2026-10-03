package pvt.phgg.chess.server.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static pvt.phgg.chess.server.analysis.MoveClassification.*;

class MoveClassifierTest {

    private static PositionEval cp(int centipawns, String best) {
        return new PositionEval(centipawns, null, best, null, 16);
    }

    private static PositionEval mate(int mateIn) {
        return new PositionEval(null, mateIn, mateIn == 0 ? null : "a1a8", null, 16);
    }

    // Centipawn score (White's view) at which White's win chance equals `chance`.
    private static int cpFor(double chance) {
        return (int) Math.round(Math.log((1 + chance) / (1 - chance)) / 0.00368208);
    }

    @Test
    void winChanceCurve() {
        assertEquals(0, MoveClassifier.winChance(0), 1e-9);
        assertEquals(0.18, MoveClassifier.winChance(100), 0.01);
        assertEquals(-0.18, MoveClassifier.winChance(-100), 0.01);
        assertTrue(MoveClassifier.winChance(2000) > 0.99);
    }

    @Test
    void playingTheEngineMoveIsBestWhateverTheDrop() {
        assertEquals(BEST, MoveClassifier.classify(cp(300, "e2e4"), cp(-300, null), true, "e2e4"));
    }

    @Test
    void thresholdsForWhite() {
        assertEquals(GOOD, MoveClassifier.classify(cp(0, "e2e4"), cp(cpFor(-0.09), null), true, "d2d4"));
        assertEquals(INACCURACY, MoveClassifier.classify(cp(0, "e2e4"), cp(cpFor(-0.11), null), true, "d2d4"));
        assertEquals(MISTAKE, MoveClassifier.classify(cp(0, "e2e4"), cp(cpFor(-0.21), null), true, "d2d4"));
        assertEquals(BLUNDER, MoveClassifier.classify(cp(0, "e2e4"), cp(cpFor(-0.31), null), true, "d2d4"));
    }

    @Test
    void blackMovesAreMeasuredForBlack() {
        // White's view rises from 0 to +0.25 win chance: bad for Black, who moved.
        assertEquals(MISTAKE, MoveClassifier.classify(cp(0, "e7e5"), cp(cpFor(0.25), null), false, "a7a6"));
        // White's view falls: good for Black, and a gain is clamped, never better than GOOD.
        assertEquals(GOOD, MoveClassifier.classify(cp(0, "e7e5"), cp(-200, null), false, "a7a6"));
    }

    @Test
    void smallSwingInAWonPositionIsNotAMistake() {
        assertEquals(GOOD, MoveClassifier.classify(cp(800, "a1a8"), cp(600, null), true, "h2h3"));
    }

    @Test
    void throwingAwayAMateIsABlunder() {
        assertEquals(BLUNDER, MoveClassifier.classify(mate(2), cp(0, null), true, "h2h3"));
    }

    @Test
    void walkingIntoAMateIsABlunder() {
        assertEquals(BLUNDER, MoveClassifier.classify(cp(0, "e2e4"), mate(-1), true, "f2f3"));
    }

    @Test
    void deliveringCheckmateIsBest() {
        // After White's move Black is to move and checkmated: mate_in 0.
        assertEquals(BEST, MoveClassifier.classify(mate(1), mate(0), true, "h1h8"));
    }

    @Test
    void checkmatedSideIsTheSideToMove() {
        assertEquals(-1, MoveClassifier.winChance(mate(0), true, true), 1e-9, "White to move and mated");
        assertEquals(1, MoveClassifier.winChance(mate(0), false, true), 1e-9, "Black to move and mated");
    }

    @Test
    void missingEvaluationIsUnknown() {
        assertEquals(UNKNOWN, MoveClassifier.classify(null, cp(0, null), true, "e2e4"));
        assertEquals(UNKNOWN, MoveClassifier.classify(cp(0, "e2e4"), null, true, "e2e4"));
    }

    @Test
    void blackDeliveringCheckmateIsBest() {
        // After Black's move White is to move and checkmated: mate_in 0.
        assertEquals(BEST, MoveClassifier.classify(mate(-1), mate(0), false, "d8h4"));
    }

    @Test
    void blackWalkingIntoMateIsABlunder() {
        assertEquals(BLUNDER, MoveClassifier.classify(cp(0, "e7e5"), mate(2), false, "f7f6"));
    }
}

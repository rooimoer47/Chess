package pvt.phgg.chess.server.analysis;

// Labels a move by how much it lowered the mover's chance of winning, judged from the cached
// evaluations of the positions before and after it. Win chance rather than raw centipawns, so going
// from +8 to +6 in a won position isn't called a mistake. Computed when read, never stored, so the
// thresholds can change without re-analysing anything.
public final class MoveClassifier {

    static final double INACCURACY = 0.10;
    static final double MISTAKE = 0.20;
    static final double BLUNDER = 0.30;

    private MoveClassifier() {
    }

    public static MoveClassification classify(PositionEval before, PositionEval after,
                                              boolean moverIsWhite, String playedUci) {
        if (before == null || after == null) {
            return MoveClassification.UNKNOWN;
        }
        // After the move the opponent is to move; mate_in 0 there means the mover just mated.
        if (after.mateIn() != null && after.mateIn() == 0) {
            return MoveClassification.BEST;
        }
        if (playedUci.equals(before.bestUci())) {
            return MoveClassification.BEST;
        }
        double drop = winChance(before, moverIsWhite, moverIsWhite) - winChance(after, !moverIsWhite, moverIsWhite);
        if (drop >= BLUNDER) return MoveClassification.BLUNDER;
        if (drop >= MISTAKE) return MoveClassification.MISTAKE;
        if (drop >= INACCURACY) return MoveClassification.INACCURACY;
        // Includes "drops" below zero: a move can't beat the engine's best, so a gain is search noise.
        return MoveClassification.GOOD;
    }

    // Win chance in -1..1 for `forWhite`'s side. A mate score counts as a certain result.
    static double winChance(PositionEval eval, boolean whiteToMove, boolean forWhite) {
        double white;
        if (eval.mateIn() != null) {
            int mate = eval.mateIn();
            if (mate != 0) {
                white = mate > 0 ? 1 : -1;
            } else {
                // mate_in 0: the side to move is checkmated.
                white = whiteToMove ? -1 : 1;
            }
        } else {
            white = winChance(eval.evalCp());
        }
        return forWhite ? white : -white;
    }

    // Lichess's centipawn-to-win-chance curve.
    static double winChance(int centipawns) {
        return 2 / (1 + Math.exp(-0.00368208 * centipawns)) - 1;
    }
}

package pvt.phgg.chess.server.analysis;

import pvt.phgg.chess.server.analysis.GameAnalysisService.LoadedGame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// Picks the moves worth a written comment. A small model on a shared CPU writes a few tokens a second,
// so commenting on every move of a long game would take many minutes. Comments go to the human
// players' inaccuracies, mistakes and blunders, the costliest first, plus the move where the game
// was decided, whoever made it. Every other move still gets its label and arrow.
//
// Not covered: "the only good move in a sharp position". Spotting it needs the engine's second-best
// move (MultiPV), which would double the analysis cost.
final class KeyMoves {

    // A move by its index: the position it was played from (0 = White's first move).
    record KeyMove(int index, MoveClassification classification, boolean decisive) {
    }

    // From then on the eventual winner's win chance (-1..1) stays at least this high: about +3.
    static final double DECIDED = 0.5;

    private static final Set<MoveClassification> ERRORS =
            EnumSet.of(MoveClassification.INACCURACY, MoveClassification.MISTAKE, MoveClassification.BLUNDER);

    private KeyMoves() {
    }

    // In game order.
    static List<KeyMove> select(LoadedGame game, int max) {
        int plies = game.replay().positions().size();
        List<PositionEval> evals = new ArrayList<>();
        for (int ply = 0; ply < plies; ply++) {
            evals.add(game.eval(ply));
        }
        // A game without a human side can't happen today, but would otherwise get no comments.
        boolean anyHuman = game.whiteIsHuman() || game.blackIsHuman();
        int decisive = decisiveMove(evals);

        record Candidate(KeyMove move, double drop) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i + 1 < plies; i++) {
            boolean white = i % 2 == 0;
            PositionEval before = evals.get(i);
            PositionEval after = evals.get(i + 1);
            String played = game.replay().positions().get(i).playedUci();
            MoveClassification label = MoveClassifier.classify(before, after, white, played);
            boolean human = !anyHuman || (white ? game.whiteIsHuman() : game.blackIsHuman());
            if (i == decisive || (human && ERRORS.contains(label))) {
                double drop = before == null || after == null ? 0 : MoveClassifier.drop(before, after, white);
                candidates.add(new Candidate(new KeyMove(i, label, i == decisive), drop));
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparing((Candidate c) -> !c.move().decisive())
                        .thenComparing(Comparator.comparingDouble(Candidate::drop).reversed()))
                .limit(max)
                .map(Candidate::move)
                .sorted(Comparator.comparingInt(KeyMove::index))
                .toList();
    }

    // The move after which the side that ends up ahead stays clearly winning to the end, or -1 when
    // the final position isn't clearly won by anyone (a draw, or a resignation in a level position).
    // Positions without an evaluation don't break the run.
    static int decisiveMove(List<PositionEval> evals) {
        Double end = null;
        for (int ply = evals.size() - 1; ply >= 0 && end == null; ply--) {
            if (evals.get(ply) != null) end = whiteWinChance(evals.get(ply), ply);
        }
        if (end == null || Math.abs(end) < DECIDED) return -1;
        double sign = Math.signum(end);
        int firstWon = -1;
        for (int ply = evals.size() - 1; ply >= 0; ply--) {
            PositionEval eval = evals.get(ply);
            if (eval == null) continue;
            if (sign * whiteWinChance(eval, ply) < DECIDED) break;
            firstWon = ply;
        }
        // firstWon is the first position of the winning run; the move that led to it is one earlier.
        return firstWon >= 1 ? firstWon - 1 : -1;
    }

    private static double whiteWinChance(PositionEval eval, int ply) {
        return MoveClassifier.winChance(eval, ply % 2 == 0, true);
    }
}

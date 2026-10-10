package pvt.phgg.chess.server.analysis;

import pvt.phgg.chess.BoardFacts;
import pvt.phgg.chess.BoardFacts.Threat;
import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.GameReplay.MoveDisplay;
import pvt.phgg.chess.GameReplay.ReplayedGame;
import pvt.phgg.chess.GameReplay.ReplayedPosition;
import pvt.phgg.chess.OpeningBook;
import pvt.phgg.chess.piece.PieceType;
import pvt.phgg.chess.server.analysis.GameAnalysisService.LoadedGame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Turns the board and the engine's verdict on one move into short sentences that a small LLM only has
// to rephrase: what the move took, what it left hanging, how the opponent can punish it, and what the
// engine wanted instead. Every move, square and piece the comment may mention comes from here.
final class MoveFacts {

    // Two threatened pieces is plenty; more is noise for a two-sentence comment.
    private static final int MAX_THREATS = 2;
    // A game still counts as in its opening for this many plies after its last named position. Long
    // enough for an opening trap to play out: they are sprung a few moves past the last name.
    private static final int OPENING_PLIES_AFTER_BOOK = 8;

    private MoveFacts() {
    }

    // `index` is the position the move was played from (0 = White's first move).
    static MoveContext describe(LoadedGame game, int index, MoveClassification classification, boolean decisive) {
        ReplayedGame replay = game.replay();
        ReplayedPosition from = replay.positions().get(index);
        boolean white = index % 2 == 0;
        String mover = side(white);
        String opponent = side(!white);
        String san = from.playedSan();
        String move = numbered(List.of(san), index);
        GameEngine board = replay.position(index);
        GameEngine boardAfter = replay.position(index + 1);
        PositionEval evalBefore = game.eval(index);
        PositionEval evalAfter = game.eval(index + 1);
        List<String> facts = new ArrayList<>();
        List<String> lineMoves = new ArrayList<>();

        MoveDisplay played = replay.describe(index, from.playedUci());
        String bestUci = evalBefore == null ? null : evalBefore.bestUci();
        boolean playedBest = from.playedUci().equals(bestUci);
        Integer mateAllowed = san.endsWith("#") ? null : mateFor(evalAfter, !white);
        Integer mateMissed = playedBest ? null : mateFor(evalBefore, white);
        // When a mate is at stake, material and loose pieces are beside the point.
        boolean aboutMaterial = mateAllowed == null && mateMissed == null;

        if (san.contains("x")) {
            // En passant leaves the target square empty; the pawn taken stood beside it.
            String taken = BoardFacts.pieceAt(board, played.to()).map(p -> name(p.type())).orElse("pawn");
            facts.add(move + " captures " + opponent + "'s " + taken + " on " + played.to() + ".");
        }
        if (san.endsWith("#")) {
            facts.add(move + " is checkmate.");
        } else if (san.endsWith("+")) {
            facts.add(move + " gives check.");
        }

        if (aboutMaterial) {
            int balance = BoardFacts.material(board, white) - BoardFacts.material(board, !white);
            facts.add(balance == 0 ? "Before the move, material is level."
                    : "Before the move, " + mover + " is " + (balance > 0 ? "ahead" : "behind") + " by "
                    + points(Math.abs(balance)) + " of material.");
            for (Threat threat : BoardFacts.threats(boardAfter).stream().limit(MAX_THREATS).toList()) {
                facts.add("After " + move + ", " + mover + "'s " + name(threat.piece().type()) + " on "
                        + threat.piece().square() + " can be taken by " + opponent + "'s "
                        + name(threat.attacker().type()) + " on " + threat.attacker().square()
                        + (threat.defended() ? ", which is worth less." : " and is not defended."));
            }
        }

        if (mateAllowed != null) {
            facts.add(move + " allows " + opponent + " to force checkmate in " + mateAllowed + ".");
        }
        if (evalAfter != null && evalAfter.pvUci() != null && !san.endsWith("#")) {
            List<String> reply = Arrays.asList(evalAfter.pvUci().split(" "));
            List<String> replySans = replay.sanLine(index + 1, reply);
            lineMoves.addAll(replySans);
            if (!replySans.isEmpty()) {
                facts.add(opponent + "'s best reply is " + numbered(replySans, index + 1) + ".");
                if (aboutMaterial) {
                    // From before the move, so what the move itself took counts too.
                    List<String> ucis = new ArrayList<>(List.of(from.playedUci()));
                    ucis.addAll(reply.subList(0, replySans.size()));
                    List<String> sans = new ArrayList<>(List.of(san));
                    sans.addAll(replySans);
                    Outcome outcome = outcome(board, ucis, sans, index);
                    outcome.describe("Along " + move + " and that line", white).ifPresent(facts::add);
                    int net = outcome.net(white);
                    if (net < 0) {
                        facts.add("Once the captures are over, " + mover + " ends up " + points(-net)
                                + " of material behind where it was before " + move + ".");
                    } else if (net > 0) {
                        // The engine still rates the move badly, so the material it wins doesn't make up
                        // for something else, typically an attack. Say so rather than let it read as a gain.
                        facts.add("Once the captures are over, " + mover + " ends up " + points(net)
                                + " of material ahead, so what makes " + move + " bad is not material but the position that follows.");
                    }
                }
            }
        }

        if (playedBest) {
            facts.add(move + " was the engine's best move.");
        } else if (bestUci != null) {
            MoveDisplay best = describeOrNull(replay, index, bestUci);
            if (best != null) {
                if (mateMissed != null) {
                    facts.add(mover + " had a forced checkmate in " + mateMissed + " starting with " + best.san() + ".");
                }
                List<String> ucis = evalBefore.pvUci() == null ? List.of(bestUci)
                        : Arrays.asList(evalBefore.pvUci().split(" "));
                List<String> sans = replay.sanLine(index, ucis);
                lineMoves.addAll(sans);
                facts.add("The engine's best move was " + best.san()
                        + (sans.size() > 1 ? ", with the line " + numbered(sans, index) : "") + ".");
                if (aboutMaterial) {
                    Outcome outcome = outcome(board, ucis.subList(0, sans.size()), sans, index);
                    if (outcome.net(white) > 0) {
                        outcome.describe("Along that line", white).ifPresent(facts::add);
                        facts.add("That line wins " + points(outcome.net(white)) + " of material for " + mover + ".");
                    }
                }
            }
        }

        return new MoveContext(index + 1, move, san, mover, classification, decisive, opening(replay, index), null,
                evaluation(evalBefore, white), evaluation(evalAfter, !white), facts, lineMoves);
    }

    // The opening the move was played in, counting the position the move itself reaches. Null when
    // the game has no named opening or left it more than a few moves ago.
    private static String opening(ReplayedGame replay, int index) {
        List<String> epds = replay.positions().stream().map(ReplayedPosition::epd).toList();
        OpeningBook book = OpeningBook.standard();
        if (index >= book.lastNamedPly(epds) + OPENING_PLIES_AFTER_BOOK) return null;
        return book.at(epds, index + 1).map(o -> o.name() + " (" + o.eco() + ")").orElse(null);
    }

    // "12...Nf6 Nxe5 13.Bc4", numbered from the position at `ply`.
    static String numbered(List<String> sans, int ply) {
        StringBuilder line = new StringBuilder();
        for (int k = 0; k < sans.size(); k++) {
            int p = ply + k;
            if (k > 0) line.append(' ');
            if (p % 2 == 0) {
                line.append(p / 2 + 1).append('.');
            } else if (k == 0) {
                line.append(p / 2 + 1).append("...");
            }
            line.append(sans.get(k));
        }
        return line.toString();
    }

    // An evaluation in words, from White's point of view, for the position with `whiteToMove`.
    static String evaluation(PositionEval eval, boolean whiteToMove) {
        if (eval == null) return "unknown";
        if (eval.mateIn() != null) {
            int mate = eval.mateIn();
            if (mate == 0) return side(whiteToMove) + " is checkmated";
            return side(mate > 0) + " can force checkmate in " + Math.abs(mate);
        }
        int cp = eval.evalCp();
        String pawns = String.format(Locale.ROOT, "%+.1f", cp / 100.0);
        int size = Math.abs(cp);
        String ahead = side(cp > 0);
        String words = size < 50 ? "about equal"
                : size < 150 ? ahead + " is slightly better"
                : size < 300 ? ahead + " is clearly better"
                : ahead + " is winning";
        return pawns + " (" + words + ")";
    }

    // What each side captures along an engine line, and the material balance once any exchange the
    // line was cut off in the middle of is finished (BoardFacts.settle). net is from White's side.
    record Outcome(List<String> whiteTakes, List<String> blackTakes, int whiteNet) {

        int net(boolean forWhite) {
            return forWhite ? whiteNet : -whiteNet;
        }

        // "Along that line, White takes the queen on d8, and Black takes the knight on d5."
        Optional<String> describe(String opening, boolean moverIsWhite) {
            List<String> parts = new ArrayList<>();
            List<String> first = moverIsWhite ? whiteTakes : blackTakes;
            List<String> second = moverIsWhite ? blackTakes : whiteTakes;
            if (!first.isEmpty()) parts.add(side(moverIsWhite) + " takes " + and(first));
            if (!second.isEmpty()) parts.add(side(!moverIsWhite) + " takes " + and(second));
            return parts.isEmpty() ? Optional.empty() : Optional.of(opening + ", " + String.join(", and ", parts) + ".");
        }
    }

    // Settling plays out at most this many extra captures.
    private static final int SETTLE_PLIES = 6;

    // `ucis` and `sans` are the same line, played from `start`, which is the position at `ply`.
    private static Outcome outcome(GameEngine start, List<String> ucis, List<String> sans, int ply) {
        List<GameEngine> positions = BoardFacts.playLine(start, ucis);
        List<String> whiteTakes = new ArrayList<>();
        List<String> blackTakes = new ArrayList<>();
        for (int k = 0; k < positions.size() && k < sans.size(); k++) {
            if (!sans.get(k).contains("x")) continue;
            GameEngine before = k == 0 ? start : positions.get(k - 1);
            String square = ucis.get(k).substring(2, 4);
            String piece = BoardFacts.pieceAt(before, square).map(p -> name(p.type())).orElse("pawn");
            ((ply + k) % 2 == 0 ? whiteTakes : blackTakes).add("the " + piece + " on " + square);
        }
        GameEngine end = BoardFacts.settle(positions.isEmpty() ? start : positions.getLast(), SETTLE_PLIES);
        return new Outcome(whiteTakes, blackTakes, balance(end, true) - balance(start, true));
    }

    private static String and(List<String> items) {
        if (items.size() == 1) return items.getFirst();
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
    }

    private static int balance(GameEngine position, boolean forWhite) {
        return BoardFacts.material(position, forWhite) - BoardFacts.material(position, !forWhite);
    }

    // Mate in N for `forWhite`'s side, or null if the evaluation isn't a mate for that side.
    private static Integer mateFor(PositionEval eval, boolean forWhite) {
        if (eval == null || eval.mateIn() == null || eval.mateIn() == 0) return null;
        return (eval.mateIn() > 0) == forWhite ? Math.abs(eval.mateIn()) : null;
    }

    private static MoveDisplay describeOrNull(ReplayedGame replay, int index, String uci) {
        try {
            return replay.describe(index, uci);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String side(boolean white) {
        return white ? "White" : "Black";
    }

    // Material in pawns, the way players count it: a knight or bishop is 3, a rook 5, a queen 9.
    private static String points(int pawns) {
        return pawns == 1 ? "a pawn's worth" : pawns + " pawns' worth";
    }

    private static String name(PieceType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}

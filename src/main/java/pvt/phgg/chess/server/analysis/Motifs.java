package pvt.phgg.chess.server.analysis;

import pvt.phgg.chess.BoardFacts;
import pvt.phgg.chess.BoardFacts.PieceOn;
import pvt.phgg.chess.BoardFacts.Pin;
import pvt.phgg.chess.BoardFacts.Threat;
import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.piece.PieceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Names the tactic behind a mistake when an engine line shows one of a few common motifs: a piece
// left hanging, a fork, a pin, a back-rank mate. Worked out from the board alone, so the same tactic
// always gets the same tag and the same wording, which is what lets mistakes be counted and compared
// across games. Each motif found is also a fact for the comment.
//
// A motif is looked for in a line of engine moves, where one side (the attacker) carries it out
// against the other: in the reply to the move played (the move allowed it), or in the engine's best
// line (the move missed it). The rules are strict on purpose. A tag left off costs little; a wrong
// one is repeated by the LLM as fact.
final class Motifs {

    enum Motif {
        HANGING_PIECE, FORK, PIN, BACK_RANK
    }

    // `missed`: the mover could have played it. Otherwise the move let the opponent play it.
    record Tag(Motif motif, boolean missed) {
    }

    record Found(Tag tag, String fact) {
    }

    // A fork or pin counts when it comes up within this many of the attacker's moves. Later than
    // that it belongs to the line, not to the move being explained.
    private static final int ATTACKER_MOVES = 2;

    private Motifs() {
    }

    // `ucis` and `sans` are the same line, played from `start`, which is the position at `ply`. `first`
    // is the attacker's first move in it.
    private record Line(GameEngine start, List<GameEngine> positions, List<String> ucis, List<String> sans,
                        int ply, int first) {

        int size() {
            return Math.min(positions.size(), sans.size());
        }

        GameEngine before(int k) {
            return k == 0 ? start : positions.get(k - 1);
        }

        GameEngine after(int k) {
            return positions.get(k);
        }

        String from(int k) {
            return ucis.get(k).substring(0, 2);
        }

        // Not for a castle: in Chess960 that is written as the king moving onto its rook.
        String to(int k) {
            return ucis.get(k).substring(2, 4);
        }

        String san(int k) {
            return sans.get(k);
        }

        boolean castles(int k) {
            return san(k).startsWith("O-O");
        }

        // "12...Nf6"
        String move(int k) {
            return MoveFacts.numbered(List.of(san(k)), ply + k);
        }

        boolean attackerWhite() {
            return before(first).isWhiteTurn();
        }

        boolean missed() {
            return first == 0;
        }
    }

    // The motifs the move played let the opponent carry out. The line is the move itself followed by
    // the engine's reply to it, played from the position before the move. With `material` false a
    // mate is at stake, and only the motifs that explain a mate are looked for.
    static List<Found> allowed(GameEngine before, List<String> ucis, List<String> sans, int ply, boolean material) {
        return find(new Line(before, BoardFacts.playLine(before, ucis), ucis, sans, ply, 1), material);
    }

    // The motifs in the engine's best line, which the mover could have played instead.
    static List<Found> missed(GameEngine before, List<String> ucis, List<String> sans, int ply, boolean material) {
        return find(new Line(before, BoardFacts.playLine(before, ucis), ucis, sans, ply, 0), material);
    }

    // At most one fact per motif, in the order of the Motif enum.
    private static List<Found> find(Line line, boolean material) {
        List<Found> found = new ArrayList<>();
        int soon = Math.min(line.size(), line.first() + 2 * ATTACKER_MOVES);
        if (material) {
            Optional<Pin> broken = brokenPin(line);
            add(found, line, Motif.HANGING_PIECE, hangingPiece(line, broken));
            for (int k = line.first(); k < soon; k += 2) {
                add(found, line, Motif.FORK, fork(line, k));
            }
            add(found, line, Motif.PIN, broken.map(pin -> pinned(pin, "was") + ", so after " + line.move(0) + " the "
                    + MoveFacts.name(pin.pinner().type()) + " takes the " + MoveFacts.name(pin.behind().type()) + "."));
            for (int k = line.first(); k < soon; k += 2) {
                add(found, line, Motif.PIN, newPin(line, k));
            }
        }
        for (int k = line.first(); k < soon; k += 2) {
            add(found, line, Motif.PIN, pinnedDefender(line, k));
        }
        for (int k = line.first(); k < line.size(); k += 2) {
            add(found, line, Motif.BACK_RANK, backRankMate(line, k));
        }
        found.sort(Comparator.comparing(f -> f.tag().motif()));
        return found;
    }

    private static void add(List<Found> found, Line line, Motif motif, Optional<String> fact) {
        if (fact.isEmpty() || found.stream().anyMatch(f -> f.tag().motif() == motif)) return;
        String label = switch (motif) {
            case HANGING_PIECE -> "hanging piece";
            case FORK -> "fork";
            case PIN -> "pin";
            case BACK_RANK -> "back-rank mate";
        };
        label = line.missed() ? "Missed " + label : label.substring(0, 1).toUpperCase() + label.substring(1);
        found.add(new Found(new Tag(motif, line.missed()), label + ": " + fact.get()));
    }

    // The attacker's first move takes a piece that could be won outright. Not a pawn, and not the
    // piece behind a pin that was just broken (that is the pin's fact). Nor when something worth as
    // much is taken right before or right after it: that is an exchange or a combination, as when
    // the Elephant Trap's knight is taken at the price of a queen.
    private static Optional<String> hangingPiece(Line line, Optional<Pin> broken) {
        int k = line.first();
        if (k >= line.size() || !line.san(k).contains("x") || line.castles(k)) return Optional.empty();
        GameEngine position = line.before(k);
        String square = line.to(k);
        Optional<Threat> threat = BoardFacts.threats(position).stream()
                .filter(t -> t.piece().square().equals(square) && t.piece().type() != PieceType.PAWN)
                .findFirst();
        Optional<PieceOn> taker = BoardFacts.pieceAt(position, line.from(k));
        if (threat.isEmpty() || taker.isEmpty()) return Optional.empty();
        PieceOn piece = threat.get().piece();
        boolean defended = threat.get().defended();
        if (defended && BoardFacts.value(taker.get().type()) >= BoardFacts.value(piece.type())) return Optional.empty();
        if (broken.isPresent() && broken.get().behind().square().equals(square)) return Optional.empty();
        if (k > 0 && !line.castles(k - 1) && line.to(k - 1).equals(square)) {
            int taken = BoardFacts.pieceAt(line.before(k - 1), square).map(p -> BoardFacts.value(p.type())).orElse(0);
            if (taken >= BoardFacts.value(piece.type())) return Optional.empty();
        }
        if (k + 1 < line.size() && line.san(k + 1).contains("x") && !line.castles(k + 1)) {
            int takenBack = BoardFacts.pieceAt(line.after(k), line.to(k + 1)).map(p -> BoardFacts.value(p.type())).orElse(0);
            if (takenBack >= BoardFacts.value(piece.type())) return Optional.empty();
        }
        String how = defended ? "by " + owned(taker.get()) + ", which is worth less."
                : "for nothing by " + owned(taker.get()) + ".";
        return Optional.of(line.missed()
                ? owned(piece) + " could be taken " + how
                : line.move(0) + " leaves " + owned(piece) + " to be taken " + how);
    }

    // The attacker's move puts a piece where it attacks two or more things that can each be won: the
    // king, a piece worth more than the attacking one, or one that is not defended. The forking piece
    // must not be simply taken, and if the line goes on the attacker has to take one of them.
    private static Optional<String> fork(Line line, int k) {
        if (line.castles(k)) return Optional.empty();
        GameEngine position = line.after(k);
        String square = line.to(k);
        Optional<PieceOn> forker = BoardFacts.pieceAt(position, square);
        if (forker.isEmpty() || forker.get().type() == PieceType.KING) return Optional.empty();
        if (canBeWon(position, square)) return Optional.empty();
        int worth = BoardFacts.value(forker.get().type());
        List<PieceOn> targets = BoardFacts.attackedFrom(position, square).stream()
                .filter(t -> t.type() == PieceType.KING || BoardFacts.value(t.type()) > worth
                        || !BoardFacts.isDefended(position, t.square()))
                .sorted(Comparator.comparing((PieceOn t) -> t.type() != PieceType.KING)
                        .thenComparing(t -> -BoardFacts.value(t.type())))
                .toList();
        if (targets.size() < 2 || targets.stream().allMatch(t -> t.type() == PieceType.PAWN)) return Optional.empty();
        if (k + 2 < line.size()) {
            boolean cashesIn = line.san(k + 2).contains("x") && !line.castles(k + 2)
                    && targets.stream().anyMatch(t -> t.square().equals(line.to(k + 2)));
            if (!cashesIn) return Optional.empty();
        }
        String theirs = MoveFacts.side(targets.getFirst().white()) + "'s "
                + MoveFacts.and(targets.stream().map(t -> MoveFacts.name(t.type()) + " on " + t.square()).toList());
        return Optional.of("with " + line.move(k) + " " + owned(forker.get())
                + (line.missed() ? " would attack " : " attacks ") + theirs + " at once.");
    }

    // The move played took a piece out of a pin, and the reply takes what stood behind it.
    private static Optional<Pin> brokenPin(Line line) {
        if (line.missed() || line.size() < 2 || line.castles(0)) return Optional.empty();
        return BoardFacts.pins(line.start(), !line.attackerWhite()).stream()
                .filter(pin -> pin.pinned().square().equals(line.from(0))
                        && line.ucis().get(1).startsWith(pin.pinner().square() + pin.behind().square()))
                .findFirst();
    }

    // The attacker's move pins a piece. Also looked for after the answer to it, when that puts a
    // more valuable piece behind one already on the line (a king that takes, say). A piece that
    // steps into the line itself doesn't count: blocking a check that way is mostly harmless. Nor do
    // pawns, nor a piece that moves along the line of the pin and could take the pinning piece. What
    // stands behind has to be worth winning: the king, a piece worth more than the pinning one, or
    // one that is not defended.
    private static Optional<String> newPin(Line line, int k) {
        boolean victimWhite = !line.attackerWhite();
        List<Pin> old = BoardFacts.pins(line.before(k), victimWhite);
        for (int at = k; at <= k + 1 && at < line.size(); at++) {
            GameEngine position = line.after(at);
            for (Pin pin : BoardFacts.pins(position, victimWhite)) {
                boolean isNew = old.stream().noneMatch(o -> o.pinned().square().equals(pin.pinned().square())
                        && o.behind().square().equals(pin.behind().square()));
                boolean steppedIn = at > k && !line.castles(at) && line.to(at).equals(pin.pinned().square());
                // The pinning piece's own safety can only be told while the other side is to move.
                if (!isNew || steppedIn || !holds(position, pin)
                        || (at == k && canBeWon(position, pin.pinner().square()))) continue;
                String moves = at == k ? line.move(k) : MoveFacts.numbered(line.sans().subList(k, at + 1), line.ply() + k);
                return Optional.of("after " + moves + " " + pinned(pin, line.missed() ? "would be" : "is") + ".");
            }
        }
        return Optional.empty();
    }

    private static boolean holds(GameEngine position, Pin pin) {
        PieceType pinned = pin.pinned().type();
        boolean diagonal = pin.pinner().square().charAt(0) != pin.pinned().square().charAt(0)
                && pin.pinner().square().charAt(1) != pin.pinned().square().charAt(1);
        boolean alongTheLine = pinned == PieceType.QUEEN || pinned == (diagonal ? PieceType.BISHOP : PieceType.ROOK);
        if (pinned == PieceType.PAWN || alongTheLine) return false;
        return pin.behind().type() == PieceType.KING
                || BoardFacts.value(pin.behind().type()) > BoardFacts.value(pin.pinner().type())
                || !BoardFacts.isDefended(position, pin.behind().square());
    }

    // The attacker's move takes something or gives check on a square that only a pinned piece
    // guards: the piece that would take back is pinned to its king, and nothing else can.
    private static Optional<String> pinnedDefender(Line line, int k) {
        String san = line.san(k);
        if (line.castles(k) || !(san.contains("x") || san.endsWith("+") || san.endsWith("#"))) return Optional.empty();
        GameEngine position = line.after(k);
        String square = line.to(k);
        boolean victimWhite = !line.attackerWhite();
        boolean taken = BoardFacts.pieces(position, victimWhite).stream()
                .anyMatch(piece -> BoardFacts.canTake(position, piece.square(), square));
        if (taken) return Optional.empty();
        return BoardFacts.pins(position, victimWhite).stream()
                .filter(pin -> pin.behind().type() == PieceType.KING)
                .filter(pin -> BoardFacts.attackedFrom(position, pin.pinned().square()).stream()
                        .anyMatch(target -> target.square().equals(square)))
                .findFirst()
                .map(pin -> pinned(pin, line.missed() ? "would be" : "is") + ", so it "
                        + (line.missed() ? "could not" : "cannot") + " take on " + square + " after " + line.move(k) + ".");
    }

    // The attacker's rook or queen mates on the back rank, with the king kept there by its own
    // pieces: every square in front of it holds one.
    private static Optional<String> backRankMate(Line line, int k) {
        if (!line.san(k).endsWith("#") || line.castles(k)) return Optional.empty();
        GameEngine position = line.after(k);
        boolean victimWhite = !line.attackerWhite();
        char backRank = victimWhite ? '1' : '8';
        char inFront = victimWhite ? '2' : '7';
        Optional<PieceOn> mater = BoardFacts.pieceAt(position, line.to(k));
        Optional<PieceOn> king = BoardFacts.pieces(position, victimWhite).stream()
                .filter(piece -> piece.type() == PieceType.KING).findFirst();
        if (mater.isEmpty() || king.isEmpty()) return Optional.empty();
        if (mater.get().type() != PieceType.ROOK && mater.get().type() != PieceType.QUEEN) return Optional.empty();
        if (mater.get().square().charAt(1) != backRank || king.get().square().charAt(1) != backRank) return Optional.empty();
        char kingFile = king.get().square().charAt(0);
        for (char file = (char) Math.max('a', kingFile - 1); file <= Math.min('h', kingFile + 1); file++) {
            Optional<PieceOn> blocker = BoardFacts.pieceAt(position, "" + file + inFront);
            if (blocker.isEmpty() || blocker.get().white() != victimWhite) return Optional.empty();
        }
        return Optional.of(line.move(k) + (line.missed() ? " would be" : " is") + " checkmate because "
                + owned(king.get()) + " is shut in on the back rank by its own pieces.");
    }

    // Whether the side to move can win the piece on `square`, which belongs to the side that just moved.
    private static boolean canBeWon(GameEngine position, String square) {
        return BoardFacts.threats(position).stream().anyMatch(t -> t.piece().square().equals(square));
    }

    // "White's knight on f3 is pinned to the queen on d1 by Black's bishop on g4"
    private static String pinned(Pin pin, String verb) {
        return owned(pin.pinned()) + " " + verb + " pinned to the " + MoveFacts.name(pin.behind().type()) + " on "
                + pin.behind().square() + " by " + owned(pin.pinner());
    }

    // "White's knight on f3"
    private static String owned(PieceOn piece) {
        return MoveFacts.side(piece.white()) + "'s " + MoveFacts.name(piece.type()) + " on " + piece.square();
    }
}

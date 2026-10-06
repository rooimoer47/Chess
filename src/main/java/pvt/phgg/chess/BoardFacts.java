package pvt.phgg.chess;

import pvt.phgg.chess.piece.APiece;
import pvt.phgg.chess.piece.King;
import pvt.phgg.chess.piece.Pawn;
import pvt.phgg.chess.piece.PieceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Plain facts about a position that the game-review comments are built from: material, what stands
// where, and which pieces can be taken for free. Worked out here so the LLM only has to put them
// into words, never read them off a board itself.
public final class BoardFacts {

    // A piece and the square it stands on, like "e4".
    public record PieceOn(PieceType type, boolean white, String square) {
    }

    // A piece of the side that just moved which the side to move can win: attacked and not defended,
    // or attacked by something worth less. `attacker` is the cheapest piece that can take it.
    public record Threat(PieceOn piece, PieceOn attacker, boolean defended) {
    }

    private BoardFacts() {
    }

    // Standard piece values in pawns; the king counts nothing.
    public static int value(PieceType type) {
        return switch (type) {
            case PAWN -> 1;
            case KNIGHT, BISHOP -> 3;
            case ROOK -> 5;
            case QUEEN -> 9;
            case KING -> 0;
        };
    }

    // Total value of one side's pieces, in pawns.
    public static int material(GameEngine position, boolean white) {
        int total = 0;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                APiece piece = position.getPiece(row, col);
                if (piece.isPositionOccupied() && piece.isWhite() == white) {
                    total += value(piece.getPieceType());
                }
            }
        }
        return total;
    }

    public static Optional<PieceOn> pieceAt(GameEngine position, String square) {
        Position at = UciMoveCodec.parseSquare(square);
        APiece piece = position.getPiece(at.getRow(), at.getCol());
        return piece.isPositionOccupied()
                ? Optional.of(new PieceOn(piece.getPieceType(), piece.isWhite(), square))
                : Optional.empty();
    }

    // The positions after each move of `uciMoves`, played from a copy of `position` (which is left
    // as it was). Stops at the first move that can't be played, so a bad engine line only shortens.
    public static List<GameEngine> playLine(GameEngine position, List<String> uciMoves) {
        List<GameEngine> after = new ArrayList<>();
        GameEngine current = position;
        for (String uci : uciMoves) {
            try {
                current = SanEncoder.apply(current, uci).after();
            } catch (IllegalArgumentException e) {
                break;
            }
            after.add(current);
        }
        return after;
    }

    // Where an engine line cut off mid-exchange would end up: the side to move keeps taking the most
    // valuable piece it can win (see threats) with its cheapest attacker, then the other side, until
    // nothing more can be won or `maxPlies` captures are made. Only for counting material at the end
    // of a line; it is not a search.
    public static GameEngine settle(GameEngine position, int maxPlies) {
        GameEngine current = position;
        for (int ply = 0; ply < maxPlies; ply++) {
            List<Threat> threats = threats(current);
            if (threats.isEmpty()) break;
            Threat target = threats.getFirst();
            String uci = target.attacker().square() + target.piece().square();
            if (target.attacker().type() == PieceType.PAWN && (uci.charAt(3) == '1' || uci.charAt(3) == '8')) {
                uci += "q";
            }
            try {
                current = SanEncoder.apply(current, uci).after();
            } catch (IllegalArgumentException e) {
                break;
            }
        }
        return current;
    }

    // Pieces of the side that just moved (not the side to move) that the side to move can win right
    // away, most valuable first. Kings are left out: a king that is attacked is in check.
    public static List<Threat> threats(GameEngine position) {
        boolean victimWhite = !position.isWhiteTurn();
        List<Threat> threats = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                APiece piece = position.getPiece(row, col);
                if (!piece.isPositionOccupied() || piece.isWhite() != victimWhite || piece.isKing()) continue;
                Position square = new Position(row, col);
                Optional<PieceOn> attacker = cheapestAttacker(position, square);
                if (attacker.isEmpty()) continue;
                boolean defended = isDefended(position, square, victimWhite);
                if (!defended || value(attacker.get().type()) < value(piece.getPieceType())) {
                    threats.add(new Threat(on(piece, square), attacker.get(), defended));
                }
            }
        }
        threats.sort(Comparator.comparingInt((Threat t) -> value(t.piece().type())).reversed());
        return threats;
    }

    // The cheapest piece of the side to move with a legal capture on `square`.
    private static Optional<PieceOn> cheapestAttacker(GameEngine position, Position square) {
        PieceOn cheapest = null;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                APiece piece = position.getPiece(row, col);
                if (!piece.isPositionOccupied() || piece.isWhite() != position.isWhiteTurn()) continue;
                Position from = new Position(row, col);
                boolean captures = position.getLegalMoves(from).stream()
                        .anyMatch(to -> to.equals(square) && !to.isCastle());
                if (captures && (cheapest == null || value(piece.getPieceType()) < value(cheapest.type()))) {
                    cheapest = on(piece, from);
                }
            }
        }
        return Optional.ofNullable(cheapest);
    }

    // Whether a piece of `white`'s side could recapture on `square`. Pieces only "attack" squares
    // holding an enemy, so an enemy pawn is put there to ask the question. Pins are ignored: a pinned
    // defender still counts, which is how players usually talk about it too.
    private static boolean isDefended(GameEngine position, Position square, boolean white) {
        APiece[][] board = position.boardCopy();
        board[square.getRow()][square.getCol()] = new Pawn(square, !white);
        BoardState state = new BoardState();
        for (APiece[] row : board) {
            for (APiece piece : row) {
                if (!piece.isPositionOccupied() || piece.isWhite() != white) continue;
                List<Position> reach = piece.isKing()
                        ? ((King) piece).getKingMovements(board, state)
                        : piece.getValidPositions(board, state);
                if (reach.stream().anyMatch(p -> p.equals(square))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static PieceOn on(APiece piece, Position square) {
        return new PieceOn(piece.getPieceType(), piece.isWhite(), UciMoveCodec.square(square.getRow(), square.getCol()));
    }
}

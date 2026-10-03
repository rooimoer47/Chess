package pvt.phgg.chess;

import pvt.phgg.chess.piece.King;

// Moves in UCI long algebraic notation (`e2e4`, `e7e8q`), the format Stockfish reads and writes.
//
// Castling is the one move where UCI depends on the variant. With UCI_Chess960 off, a castle is the
// king's two-square step (`e1g1`); with it on, it is the king moving onto its own rook (`e1h1`).
// Stored moves can't be mapped directly: in a Chess960 game a human's castle is stored as
// king-onto-rook but a bot's as the king's c/g-file target. So castles are encoded from the move as
// the engine resolved it, not from the squares the caller passed in.
public final class UciMoveCodec {

    public record UciMove(Position from, Position to, PromotionChoice promotion) {
    }

    private UciMoveCodec() {
    }

    // UCI for the move `engine` has just applied from `from` (and promoted with `promotion`, if any).
    static String encodeLastMove(GameEngine engine, Position from, PromotionChoice promotion, boolean chess960) {
        Position target = engine.getLastMoveTarget();
        String to;
        if (target.isCastle() && chess960) {
            King king = (King) engine.getPiece(target.getRow(), target.getCol());
            int rookFile = target.getCol() == 6 ? king.getKingsideRookFile() : king.getQueensideRookFile();
            to = square(target.getRow(), rookFile);
        } else {
            to = square(target.getRow(), target.getCol());
        }
        return square(from.getRow(), from.getCol()) + to + (promotion == null ? "" : promotionLetter(promotion));
    }

    // Parses a UCI move as Stockfish writes it. A Chess960 castle comes back as king-onto-rook,
    // which GameEngine.applyMove also accepts.
    public static UciMove decode(String uci) {
        if (uci == null || (uci.length() != 4 && uci.length() != 5)) {
            throw new IllegalArgumentException("Not a UCI move: " + uci);
        }
        PromotionChoice promotion = uci.length() == 5 ? promotionChoice(uci.charAt(4)) : null;
        return new UciMove(parseSquare(uci.substring(0, 2)), parseSquare(uci.substring(2, 4)), promotion);
    }

    public static String square(int row, int col) {
        return "" + (char) ('a' + col) + (char) ('1' + row);
    }

    public static Position parseSquare(String square) {
        if (square.length() != 2) {
            throw new IllegalArgumentException("Not a square: " + square);
        }
        int col = square.charAt(0) - 'a';
        int row = square.charAt(1) - '1';
        if (col < 0 || col > 7 || row < 0 || row > 7) {
            throw new IllegalArgumentException("Not a square: " + square);
        }
        return new Position(row, col);
    }

    private static char promotionLetter(PromotionChoice choice) {
        return switch (choice) {
            case QUEEN  -> 'q';
            case ROOK   -> 'r';
            case BISHOP -> 'b';
            case KNIGHT -> 'n';
        };
    }

    private static PromotionChoice promotionChoice(char letter) {
        return switch (letter) {
            case 'q' -> PromotionChoice.QUEEN;
            case 'r' -> PromotionChoice.ROOK;
            case 'b' -> PromotionChoice.BISHOP;
            case 'n' -> PromotionChoice.KNIGHT;
            default -> throw new IllegalArgumentException("Not a promotion piece: " + letter);
        };
    }
}

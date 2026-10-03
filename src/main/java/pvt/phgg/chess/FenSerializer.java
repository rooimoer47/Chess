package pvt.phgg.chess;

import pvt.phgg.chess.piece.APiece;
import pvt.phgg.chess.piece.King;
import pvt.phgg.chess.piece.Pawn;

// Writes a GameEngine position as FEN, for Stockfish and the analysis cache.
//
// Board coordinates: row 0 is White's back rank (rank 1), column 0 is the a-file.
//
// Chess960 games write Shredder-FEN castling rights (the castling rook's file letter, e.g. "HAha")
// instead of "KQkq", which Stockfish reads when UCI_Chess960 is on.
public final class FenSerializer {

    private static final int BOARD_SIZE = 8;

    private FenSerializer() {
    }

    // `ply` is the number of half-moves played to reach this position (0 = start position). The
    // engine doesn't track the full-move number, but every game starts with White to move, so it
    // follows from the ply.
    public static String toFen(GameEngine engine, int ply, boolean chess960) {
        return toEpd(engine, chess960) + " " + engine.getHalfMoveClock() + " " + (ply / 2 + 1);
    }

    // FEN without the half-move clock and full-move number: identifies a position regardless of how
    // it was reached, so it serves as the analysis cache key.
    public static String toEpd(GameEngine engine, boolean chess960) {
        return placement(engine)
                + " " + (engine.isWhiteTurn() ? "w" : "b")
                + " " + castlingRights(engine, chess960)
                + " " + enPassantSquare(engine);
    }

    private static String placement(GameEngine engine) {
        StringBuilder sb = new StringBuilder();
        for (int row = BOARD_SIZE - 1; row >= 0; row--) {
            int empty = 0;
            for (int col = 0; col < BOARD_SIZE; col++) {
                APiece piece = engine.getPiece(row, col);
                if (!piece.isPositionOccupied()) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    sb.append(empty);
                    empty = 0;
                }
                sb.append(letter(piece));
            }
            if (empty > 0) sb.append(empty);
            if (row > 0) sb.append('/');
        }
        return sb.toString();
    }

    private static char letter(APiece piece) {
        char c = switch (piece.getPieceType()) {
            case PAWN   -> 'p';
            case KNIGHT -> 'n';
            case BISHOP -> 'b';
            case ROOK   -> 'r';
            case QUEEN  -> 'q';
            case KING   -> 'k';
        };
        return piece.isWhite() ? Character.toUpperCase(c) : c;
    }

    // Order matches Stockfish's own output: White kingside, White queenside, Black kingside, Black
    // queenside.
    private static String castlingRights(GameEngine engine, boolean chess960) {
        StringBuilder sb = new StringBuilder();
        for (boolean white : new boolean[]{true, false}) {
            King king = unmovedKing(engine, white);
            if (king == null) continue;
            int row = white ? 0 : BOARD_SIZE - 1;
            appendRight(sb, engine, row, king.getKingsideRookFile(), white, chess960 ? fileLetter(king.getKingsideRookFile()) : 'k');
            appendRight(sb, engine, row, king.getQueensideRookFile(), white, chess960 ? fileLetter(king.getQueensideRookFile()) : 'q');
        }
        return sb.isEmpty() ? "-" : sb.toString();
    }

    private static void appendRight(StringBuilder sb, GameEngine engine, int row, int rookFile, boolean white, char letter) {
        if (rookFile < 0 || rookFile >= BOARD_SIZE) return;
        APiece rook = engine.getPiece(row, rookFile);
        if (rook.isRook() && rook.isWhite() == white && rook.isOriginalPosition()) {
            sb.append(white ? Character.toUpperCase(letter) : letter);
        }
    }

    private static char fileLetter(int col) {
        return (char) ('a' + col);
    }

    private static King unmovedKing(GameEngine engine, boolean white) {
        int row = white ? 0 : BOARD_SIZE - 1;
        for (int col = 0; col < BOARD_SIZE; col++) {
            APiece piece = engine.getPiece(row, col);
            if (piece.isKing() && piece.isWhite() == white && piece.isOriginalPosition()) {
                return (King) piece;
            }
        }
        return null;
    }

    // Only written when the side to move can actually capture en passant. FEN allows it after any
    // double push, but then the same position would get two different cache keys depending on
    // whether the last move happened to be a double push.
    private static String enPassantSquare(GameEngine engine) {
        boolean moverWhite = engine.isWhiteTurn();
        // The pawn that just double-pushed belongs to the side not to move, and sits on its fourth rank.
        int jumpedRow = moverWhite ? 4 : 3;
        int targetRow = moverWhite ? 5 : 2;
        for (int col = 0; col < BOARD_SIZE; col++) {
            APiece piece = engine.getPiece(jumpedRow, col);
            if (piece.isPawn() && piece.isWhite() != moverWhite && ((Pawn) piece).isJumped()
                    && canCaptureEnPassant(engine, jumpedRow, col, targetRow)) {
                return UciMoveCodec.square(targetRow, col);
            }
        }
        return "-";
    }

    private static boolean canCaptureEnPassant(GameEngine engine, int row, int col, int targetRow) {
        for (int side : new int[]{col - 1, col + 1}) {
            if (side < 0 || side >= BOARD_SIZE) continue;
            for (Position move : engine.getLegalMoves(new Position(row, side))) {
                if (move.isEnPassant() && move.getRow() == targetRow && move.getCol() == col) {
                    return true;
                }
            }
        }
        return false;
    }
}

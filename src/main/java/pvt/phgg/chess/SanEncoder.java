package pvt.phgg.chess;

import pvt.phgg.chess.UciMoveCodec.UciMove;
import pvt.phgg.chess.piece.APiece;

import java.util.ArrayList;
import java.util.List;

// Standard algebraic notation (Nf3, exd5, O-O, e8=Q, Qxf7#) for showing moves to people.
final class SanEncoder {

    // A move applied to a copy of a position: its SAN, where it went as the engine resolved it (a
    // castle's target is the king's c/g-file square, not the rook), and the position after it.
    record AppliedMove(String san, Position from, Position to, GameEngine after) {
    }

    private SanEncoder() {
    }

    // Applies `uci` to a copy of `position`, which is left unchanged. Throws if the move isn't legal.
    static AppliedMove apply(GameEngine position, String uci) {
        UciMove move = UciMoveCodec.decode(uci);
        Position from = move.from();
        APiece piece = position.getPiece(from.getRow(), from.getCol());
        if (!piece.isPositionOccupied() || piece.isWhite() != position.isWhiteTurn()) {
            throw new IllegalArgumentException("No piece of the side to move on " + uci.substring(0, 2));
        }
        APiece target = position.getPiece(move.to().getRow(), move.to().getCol());
        // In Chess960 a castle is the king moving onto its own rook, so only an enemy piece is a capture.
        boolean capturesPiece = target.isPositionOccupied() && target.isWhite() != piece.isWhite();
        String disambiguation = piece.isPawn() || piece.isKing() ? "" : disambiguation(position, piece, from, move.to());

        GameEngine after = new GameEngine(position);
        MoveResult result = after.applyMove(from, move.to());
        if (result.type() == MoveResult.Type.INVALID) {
            throw new IllegalArgumentException("Illegal move " + uci);
        }
        if (result.type() == MoveResult.Type.PROMOTION_NEEDED) {
            if (move.promotion() == null) {
                throw new IllegalArgumentException("Promotion without a piece: " + uci);
            }
            after.applyPromotion(after.getLastMoveTarget(), move.promotion());
        }
        Position resolved = after.getLastMoveTarget();

        StringBuilder san = new StringBuilder();
        if (resolved.isCastle()) {
            san.append(resolved.getCol() == 6 ? "O-O" : "O-O-O");
        } else if (piece.isPawn()) {
            if (capturesPiece || resolved.isEnPassant()) {
                san.append(file(from)).append('x');
            }
            san.append(UciMoveCodec.square(resolved.getRow(), resolved.getCol()));
            if (move.promotion() != null) {
                san.append('=').append(letter(move.promotion()));
            }
        } else {
            san.append(letter(piece)).append(disambiguation);
            if (capturesPiece) san.append('x');
            san.append(UciMoveCodec.square(resolved.getRow(), resolved.getCol()));
        }

        GameStatus status = after.getStatus();
        if (status == GameStatus.CHECKMATE) san.append('#');
        else if (status == GameStatus.CHECK) san.append('+');

        Position to = new Position(resolved.getRow(), resolved.getCol());
        return new AppliedMove(san.toString(), new Position(from.getRow(), from.getCol()), to, after);
    }

    // SAN for a line of UCI moves played from `position`. Stops at the first move that can't be
    // played, so a bad engine line shortens rather than fails.
    static List<String> line(GameEngine position, List<String> uciMoves) {
        List<String> sans = new ArrayList<>();
        GameEngine current = position;
        for (String uci : uciMoves) {
            AppliedMove applied;
            try {
                applied = apply(current, uci);
            } catch (IllegalArgumentException e) {
                break;
            }
            sans.add(applied.san());
            current = applied.after();
        }
        return sans;
    }

    // When another piece of the same kind could also move to `to`: the file if that tells them apart,
    // else the rank, else both.
    private static String disambiguation(GameEngine position, APiece piece, Position from, Position to) {
        boolean ambiguous = false;
        boolean sameFile = false;
        boolean sameRank = false;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (row == from.getRow() && col == from.getCol()) continue;
                APiece other = position.getPiece(row, col);
                if (!other.isPositionOccupied() || other.isWhite() != piece.isWhite()
                        || other.getPieceType() != piece.getPieceType()) continue;
                boolean reaches = position.getLegalMoves(new Position(row, col)).stream()
                        .anyMatch(p -> p.equals(to) && !p.isCastle());
                if (reaches) {
                    ambiguous = true;
                    sameFile |= col == from.getCol();
                    sameRank |= row == from.getRow();
                }
            }
        }
        if (!ambiguous) return "";
        if (!sameFile) return String.valueOf(file(from));
        if (!sameRank) return String.valueOf((char) ('1' + from.getRow()));
        return UciMoveCodec.square(from.getRow(), from.getCol());
    }

    private static char file(Position p) {
        return (char) ('a' + p.getCol());
    }

    private static char letter(APiece piece) {
        return switch (piece.getPieceType()) {
            case KNIGHT -> 'N';
            case BISHOP -> 'B';
            case ROOK   -> 'R';
            case QUEEN  -> 'Q';
            case KING   -> 'K';
            case PAWN   -> throw new IllegalArgumentException("Pawns have no letter");
        };
    }

    private static char letter(PromotionChoice choice) {
        return switch (choice) {
            case QUEEN  -> 'Q';
            case ROOK   -> 'R';
            case BISHOP -> 'B';
            case KNIGHT -> 'N';
        };
    }
}

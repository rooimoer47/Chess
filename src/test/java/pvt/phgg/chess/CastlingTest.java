package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CastlingTest {

    // Coordinate system: row 0 = white back rank (rank 1), row 7 = black back rank (rank 8).
    // col 0 = file a, col 7 = file h.
    // Kingside castle:  king e1(0,4) → g1(0,6), rook h1(0,7) → f1(0,5)
    // Queenside castle: king e1(0,4) → c1(0,2), rook a1(0,0) → d1(0,3)

    private static Position p(int row, int col) {
        return new Position(row, col);
    }

    private static APiece[][] emptyBoard() {
        APiece[][] b = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                b[r][c] = new EmptySquare(new Position(r, c));
        return b;
    }

    private static boolean hasMoveTo(List<Position> moves, int row, int col) {
        return moves.stream().anyMatch(m -> m.getRow() == row && m.getCol() == col);
    }

    // --- Castling succeeds ---

    @Test
    void whiteCastlesKingside() {
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);   // White King e1
        board[0][7] = new Rook(new Position(0, 7), true);   // White Rook h1
        board[7][4] = new King(new Position(7, 4), false);  // Black King e8 (required)
        GameEngine engine = new GameEngine(board, true);

        MoveResult result = engine.applyMove(p(0, 4), p(0, 6)); // O-O

        assertTrue(result.isValid(), "Kingside castle should be valid");
        assertTrue(engine.getPiece(0, 6).isKing(),  "King should be on g1");
        assertTrue(engine.getPiece(0, 5).isRook(),  "Rook should be on f1");
        assertFalse(engine.getPiece(0, 4).isPositionOccupied(), "e1 should be empty");
        assertFalse(engine.getPiece(0, 7).isPositionOccupied(), "h1 should be empty");
    }

    @Test
    void whiteCastlesQueenside() {
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[0][0] = new Rook(new Position(0, 0), true);   // White Rook a1
        board[7][4] = new King(new Position(7, 4), false);
        GameEngine engine = new GameEngine(board, true);

        MoveResult result = engine.applyMove(p(0, 4), p(0, 2)); // O-O-O

        assertTrue(result.isValid(), "Queenside castle should be valid");
        assertTrue(engine.getPiece(0, 2).isKing(),  "King should be on c1");
        assertTrue(engine.getPiece(0, 3).isRook(),  "Rook should be on d1");
        assertFalse(engine.getPiece(0, 4).isPositionOccupied(), "e1 should be empty");
        assertFalse(engine.getPiece(0, 0).isPositionOccupied(), "a1 should be empty");
    }

    @Test
    void blackCastlesKingside() {
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[7][4] = new King(new Position(7, 4), false);  // Black King e8
        board[7][7] = new Rook(new Position(7, 7), false);  // Black Rook h8
        GameEngine engine = new GameEngine(board, false); // black to move

        MoveResult result = engine.applyMove(p(7, 4), p(7, 6)); // O-O

        assertTrue(result.isValid(), "Black kingside castle should be valid");
        assertTrue(engine.getPiece(7, 6).isKing(), "Black king should be on g8");
        assertTrue(engine.getPiece(7, 5).isRook(), "Black rook should be on f8");
        assertFalse(engine.getPiece(7, 4).isPositionOccupied(), "e8 should be empty");
        assertFalse(engine.getPiece(7, 7).isPositionOccupied(), "h8 should be empty");
    }

    // --- Castling blocked ---

    @Test
    void castlingBlockedWhenKingHasMoved() {
        APiece[][] board = emptyBoard();
        King king = new King(new Position(0, 4), true);
        king.moved(); // simulate prior move
        board[0][4] = king;
        board[0][7] = new Rook(new Position(0, 7), true);
        board[7][4] = new King(new Position(7, 4), false);
        GameEngine engine = new GameEngine(board, true);

        List<Position> moves = engine.getLegalMoves(p(0, 4));
        assertFalse(hasMoveTo(moves, 0, 6), "Kingside castle should be unavailable after king has moved");
        assertFalse(hasMoveTo(moves, 0, 2), "Queenside castle should be unavailable after king has moved");
    }

    @Test
    void castlingBlockedWhenRookHasMoved() {
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        Rook rook = new Rook(new Position(0, 7), true);
        rook.moved(); // simulate prior move
        board[0][7] = rook;
        board[7][4] = new King(new Position(7, 4), false);
        GameEngine engine = new GameEngine(board, true);

        List<Position> moves = engine.getLegalMoves(p(0, 4));
        assertFalse(hasMoveTo(moves, 0, 6), "Kingside castle should be unavailable after rook has moved");
    }

    @Test
    void castlingBlockedWhenPassingThroughAttackedSquare() {
        // Black rook on f-file attacks f1 (0,5) — the kingside transit square.
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[0][7] = new Rook(new Position(0, 7), true);
        board[7][4] = new King(new Position(7, 4), false);
        board[6][5] = new Rook(new Position(6, 5), false); // Black rook f7, attacks f1
        GameEngine engine = new GameEngine(board, true);

        List<Position> moves = engine.getLegalMoves(p(0, 4));
        assertFalse(hasMoveTo(moves, 0, 6), "Kingside castle should be blocked when f1 is attacked");
    }

    @Test
    void castlingBlockedWhenInCheck() {
        // Black rook on e-file gives check to white king — castling not allowed while in check.
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[0][7] = new Rook(new Position(0, 7), true);
        board[7][0] = new King(new Position(7, 0), false); // Black King a8 (out of the way)
        board[5][4] = new Rook(new Position(5, 4), false); // Black rook e6, attacks e1
        GameEngine engine = new GameEngine(board, true);

        List<Position> moves = engine.getLegalMoves(p(0, 4));
        assertFalse(hasMoveTo(moves, 0, 6), "Castling should be blocked when king is in check");
        assertFalse(hasMoveTo(moves, 0, 2), "Castling should be blocked when king is in check");
    }
}

package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckTest {

    // Coordinate system: row 0 = white back rank (rank 1), row 7 = black back rank (rank 8).
    // col 0 = file a, col 7 = file h.

    private static Position p(int row, int col) { return new Position(row, col); }

    private static APiece[][] emptyBoard() {
        APiece[][] b = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                b[r][c] = new EmptySquare(new Position(r, c));
        return b;
    }

    @Test
    void moveDeliversCheck() {
        // White rook moves from e2 to e7, giving check to the black king on e8.
        APiece[][] board = emptyBoard();
        board[0][7] = new King(new Position(0, 7), true);   // White King h1
        board[1][4] = new Rook(new Position(1, 4), true);   // White Rook e2
        board[7][4] = new King(new Position(7, 4), false);  // Black King e8

        GameEngine engine = new GameEngine(board, true);

        MoveResult result = engine.applyMove(p(1, 4), p(6, 4)); // Re2-e7

        assertEquals(MoveResult.Type.CHECK, result.type(), "Rook move to e7 should give check to the black king on e8");
        assertEquals(GameStatus.CHECK, engine.getStatus());
    }

    @Test
    void pinnedPieceCannotMoveOffPinLine() {
        // White bishop on e4 is pinned along the e-file by a black rook on e8.
        // Any diagonal move by the bishop would expose the white king on e1 to the rook.
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);    // White King e1
        board[3][4] = new Bishop(new Position(3, 4), true);  // White Bishop e4 (pinned)
        board[7][0] = new King(new Position(7, 0), false);   // Black King a8
        board[7][4] = new Rook(new Position(7, 4), false);   // Black Rook e8 (pins along e-file)

        GameEngine engine = new GameEngine(board, true);

        List<Position> moves = engine.getLegalMoves(p(3, 4));
        assertTrue(moves.isEmpty(), "Bishop pinned along the e-file should have no legal moves");
    }

    @Test
    void pinnedRookCanMoveAlongPinLine() {
        // White rook on e4 is pinned along the e-file by a black rook on e8.
        // Unlike a bishop, the rook can move along the pin line (toward or away from the pinner).
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);   // White King e1
        board[3][4] = new Rook(new Position(3, 4), true);   // White Rook e4 (pinned along e-file)
        board[7][0] = new King(new Position(7, 0), false);  // Black King a8
        board[7][4] = new Rook(new Position(7, 4), false);  // Black Rook e8

        GameEngine engine = new GameEngine(board, true);

        List<Position> moves = engine.getLegalMoves(p(3, 4));
        assertFalse(moves.isEmpty(), "Pinned rook should still be able to move along the pin line");
        assertTrue(moves.stream().allMatch(m -> m.getCol() == 4),
                "Every legal move of the pinned rook must stay on the e-file");
    }

    @Test
    void moveLeavingKingInCheckIsIllegal() {
        // White rook on e4 is the only piece between the white king on e1 and a black rook on e8.
        // Moving the white rook off the e-file exposes the king — must be rejected.
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);   // White King e1
        board[3][4] = new Rook(new Position(3, 4), true);   // White Rook e4 (shielding)
        board[7][0] = new King(new Position(7, 0), false);  // Black King a8
        board[7][4] = new Rook(new Position(7, 4), false);  // Black Rook e8

        GameEngine engine = new GameEngine(board, true);

        MoveResult result = engine.applyMove(p(3, 4), p(3, 3)); // Re4-d4 — steps off pin line
        assertEquals(MoveResult.Type.INVALID, result.type(),
                "Moving the shielding rook off the pin line must be rejected");
    }
}

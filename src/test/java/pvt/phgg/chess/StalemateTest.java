package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StalemateTest {

    // Coordinate system: row 0 = white back rank (rank 1), row 7 = black back rank (rank 8).
    // col 0 = file a, col 7 = file h.

    @Test
    void stalemateDetected() {
        // Black King a8 (7,0) — no legal moves, not in check.
        // White Queen b6 (5,1) controls a7 (diagonal) and b8 (same file).
        // White King c6 (5,2) controls b7 (diagonal).
        APiece[][] board = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                board[r][c] = new EmptySquare(new Position(r, c));

        board[7][0] = new King(new Position(7, 0), false);  // Black King a8
        board[5][1] = new Queen(new Position(5, 1), true);  // White Queen b6
        board[5][2] = new King(new Position(5, 2), true);   // White King c6

        GameEngine engine = new GameEngine(board, false); // black to move

        assertEquals(GameStatus.STALEMATE, engine.getStatus());
    }

    @Test
    void notStalemateWhenMoveAvailable() {
        // Black King h8 (7,7) — three free squares (g8, g7, h7), not attacked by anything.
        // White Queen b6 (5,1) and White King c6 (5,2) are on the opposite side of the board.
        APiece[][] board = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                board[r][c] = new EmptySquare(new Position(r, c));

        board[7][7] = new King(new Position(7, 7), false);  // Black King h8
        board[5][1] = new Queen(new Position(5, 1), true);  // White Queen b6
        board[5][2] = new King(new Position(5, 2), true);   // White King c6

        GameEngine engine = new GameEngine(board, false);

        assertEquals(GameStatus.IN_PROGRESS, engine.getStatus());
    }
}

package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import static org.junit.jupiter.api.Assertions.*;

class FiftyMoveRuleTest {

    // Coordinate system: row 0 = white back rank (rank 1), row 7 = black back rank (rank 8).

    private static Position p(int row, int col) {
        return new Position(row, col);
    }

    private static APiece[][] baseBoard() {
        APiece[][] board = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                board[r][c] = new EmptySquare(new Position(r, c));
        // White King e1, Black King e8, rooks far from both kings (no check possible).
        board[0][4] = new King(new Position(0, 4), true);   // White King e1
        board[7][4] = new King(new Position(7, 4), false);  // Black King e8
        board[0][0] = new Rook(new Position(0, 0), true);   // White Rook a1
        board[7][0] = new Rook(new Position(7, 0), false);  // Black Rook a8
        return board;
    }

    @Test
    void fiftyMoveRuleTriggersOnHundredthHalfMove() {
        // Start with clock at 98 — two more non-pawn, non-capture moves reach 100.
        GameEngine engine = new GameEngine(baseBoard(), true, 98);

        MoveResult first = engine.applyMove(p(0, 0), p(0, 1)); // White Ra1→b1 (clock 99)
        assertNotEquals(MoveResult.Type.DRAW, first.getType(), "Clock at 99 should not yet be a draw");

        MoveResult second = engine.applyMove(p(7, 0), p(7, 1)); // Black Ra8→b8 (clock 100)
        assertEquals(MoveResult.Type.DRAW, second.getType(), "Clock at 100 should be a draw");
        assertEquals(GameStatus.FIFTY_MOVE_RULE, engine.getStatus());
    }

    @Test
    void clockResetsOnCapture() {
        // Start at clock 99 — a capture on the next move resets the clock to 0, not 100.
        APiece[][] board = baseBoard();
        board[0][1] = new Rook(new Position(0, 1), false); // Black Rook b1 (target for capture)
        GameEngine engine = new GameEngine(board, true, 99);

        MoveResult result = engine.applyMove(p(0, 0), p(0, 1)); // White Ra1xb1 (capture → clock resets)
        assertNotEquals(MoveResult.Type.DRAW, result.getType(), "Capture should reset the clock, not trigger fifty-move rule");
        assertNotEquals(GameStatus.FIFTY_MOVE_RULE, engine.getStatus());
    }

    @Test
    void clockResetsOnPawnMove() {
        // Start at clock 99 — a pawn move resets the clock to 0, not 100.
        APiece[][] board = baseBoard();
        board[1][3] = new Pawn(new Position(1, 3), true); // White Pawn d2
        GameEngine engine = new GameEngine(board, true, 99);

        MoveResult result = engine.applyMove(p(1, 3), p(2, 3)); // White d2→d3 (pawn move → clock resets)
        assertNotEquals(MoveResult.Type.DRAW, result.getType(), "Pawn move should reset the clock, not trigger fifty-move rule");
        assertNotEquals(GameStatus.FIFTY_MOVE_RULE, engine.getStatus());
    }
}

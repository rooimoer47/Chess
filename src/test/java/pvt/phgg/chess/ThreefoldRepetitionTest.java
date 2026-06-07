package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import static org.junit.jupiter.api.Assertions.*;

class ThreefoldRepetitionTest {

    // Coordinate system: row 0 = white back rank (rank 1), row 7 = black back rank (rank 8).
    // col 0 = file a, col 7 = file h.

    private static Position p(int row, int col) {
        return new Position(row, col);
    }

    private static void move(GameEngine engine, int fromRow, int fromCol, int toRow, int toCol) {
        MoveResult result = engine.applyMove(p(fromRow, fromCol), p(toRow, toCol));
        assertNotEquals(MoveResult.Type.INVALID, result.type(),
            String.format("Setup move (%d,%d)->(%d,%d) should be valid", fromRow, fromCol, toRow, toCol));
    }

    /**
     * Minimal position: White King e1 (0,4), Black King e8 (7,4),
     * White Knight g1 (0,6), Black Knight g8 (7,6).
     *
     * The knights bounce: WN g1↔f3, BN g8↔f6.
     * After both knights have moved once (moves 1-4) their originalPosition flag is false
     * and the repeating cycle begins. Three full cycles of that stable state trigger
     * THREEFOLD_REPETITION on move 12.
     */
    @Test
    void threefoldRepetitionDetected() {
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);   // White King e1
        board[7][4] = new King(new Position(7, 4), false);  // Black King e8
        board[0][6] = new Knight(new Position(0, 6), true); // White Knight g1
        board[7][6] = new Knight(new Position(7, 6), false);// Black Knight g8

        GameEngine engine = new GameEngine(board, true);

        // Warm-up: moves 1-4 get both knights' originalPosition to false.
        move(engine, 0, 6, 2, 5); // 1. WN g1→f3
        move(engine, 7, 6, 5, 5); // 2. BN g8→f6
        move(engine, 2, 5, 0, 6); // 3. WN f3→g1
        move(engine, 5, 5, 7, 6); // 4. BN f6→g8  — stable state D (first occurrence)

        // Cycle 2 (moves 5-8): state D seen twice.
        move(engine, 0, 6, 2, 5); // 5
        move(engine, 7, 6, 5, 5); // 6
        move(engine, 2, 5, 0, 6); // 7
        move(engine, 5, 5, 7, 6); // 8  — state D second occurrence

        // Cycle 3 (moves 9-11): third occurrence on move 12.
        move(engine, 0, 6, 2, 5); // 9
        move(engine, 7, 6, 5, 5); // 10
        move(engine, 2, 5, 0, 6); // 11

        MoveResult result = engine.applyMove(p(5, 5), p(7, 6)); // 12 — state D third occurrence
        assertEquals(MoveResult.Type.DRAW, result.type(), "Third repetition should be a draw");
        assertEquals(GameStatus.THREEFOLD_REPETITION, engine.getStatus());
    }

    @Test
    void noRepetitionAfterTwoOccurrences() {
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[7][4] = new King(new Position(7, 4), false);
        board[0][6] = new Knight(new Position(0, 6), true);
        board[7][6] = new Knight(new Position(7, 6), false);

        GameEngine engine = new GameEngine(board, true);

        // Same warm-up + one full cycle (only two occurrences of state D).
        move(engine, 0, 6, 2, 5);
        move(engine, 7, 6, 5, 5);
        move(engine, 2, 5, 0, 6);
        move(engine, 5, 5, 7, 6); // state D — 1st occurrence

        move(engine, 0, 6, 2, 5);
        move(engine, 7, 6, 5, 5);
        move(engine, 2, 5, 0, 6);

        MoveResult result = engine.applyMove(p(5, 5), p(7, 6)); // state D — 2nd occurrence
        assertNotEquals(MoveResult.Type.DRAW, result.type(), "Two occurrences should not be a draw yet");
        assertNotEquals(GameStatus.THREEFOLD_REPETITION, engine.getStatus());
    }

    private static APiece[][] emptyBoard() {
        APiece[][] board = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                board[r][c] = new EmptySquare(new Position(r, c));
        return board;
    }
}

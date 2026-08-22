package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pawn advance rules — in particular the initial two-square move, which is only legal when both the
 * square in front of the pawn and the destination are empty. Coordinate system: row 0 = white back
 * rank (rank 1), row 7 = black back rank; white pawns advance toward higher rows.
 */
class PawnMoveTest {

    private static Position p(int row, int col) { return new Position(row, col); }

    private static APiece[][] blankBoard() {
        APiece[][] board = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                board[r][c] = new EmptySquare(p(r, c));
        // Kings tucked in far corners so nothing under test accidentally gives check.
        board[0][0] = new King(p(0, 0), true);
        board[7][7] = new King(p(7, 7), false);
        return board;
    }

    private static boolean hasMoveTo(List<Position> moves, int row, int col) {
        return moves.stream().anyMatch(m -> m.getRow() == row && m.getCol() == col);
    }

    @Test
    void pawn_cannotDoubleStepOverBlockingPieceInFront() {
        // White pawn on its start square (d2) with a piece directly in front (d3): it must not be
        // able to leap over the blocker to d4.
        APiece[][] board = blankBoard();
        board[1][3] = new Pawn(p(1, 3), true);   // white pawn d2 (start square)
        board[2][3] = new Pawn(p(2, 3), true);   // blocker directly in front on d3
        GameEngine engine = new GameEngine(board, true);

        List<Position> moves = engine.getLegalMoves(p(1, 3));
        assertFalse(hasMoveTo(moves, 3, 3), "Pawn must not jump over the blocking piece to d4");
        assertFalse(hasMoveTo(moves, 2, 3), "Pawn cannot advance into the occupied square d3 either");
    }

    @Test
    void blackPawn_cannotDoubleStepOverBlockingPieceInFront() {
        // Symmetric case for black: pawn on d7 (start) blocked on d6 must not reach d5.
        APiece[][] board = blankBoard();
        board[6][3] = new Pawn(p(6, 3), false);  // black pawn d7 (start square)
        board[5][3] = new Pawn(p(5, 3), false);  // blocker directly in front on d6
        GameEngine engine = new GameEngine(board, false);

        List<Position> moves = engine.getLegalMoves(p(6, 3));
        assertFalse(hasMoveTo(moves, 4, 3), "Black pawn must not jump over the blocking piece to d5");
        assertFalse(hasMoveTo(moves, 5, 3), "Black pawn cannot advance into the occupied square d6 either");
    }

    @Test
    void pawn_doubleStep_allowedWhenPathIsClear() {
        // Control: with nothing in front, the initial two-square move is offered.
        APiece[][] board = blankBoard();
        board[1][3] = new Pawn(p(1, 3), true);
        GameEngine engine = new GameEngine(board, true);

        List<Position> moves = engine.getLegalMoves(p(1, 3));
        assertTrue(hasMoveTo(moves, 2, 3), "Single step should be available");
        assertTrue(hasMoveTo(moves, 3, 3), "Double step should be available when the path is clear");
    }
}

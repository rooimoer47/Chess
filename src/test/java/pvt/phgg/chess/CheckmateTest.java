package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import static org.junit.jupiter.api.Assertions.*;

class CheckmateTest {

    // Coordinate system: row 0 = white back rank (rank 1), row 7 = black back rank (rank 8).
    // col 0 = file a, col 7 = file h.

    private static Position p(int row, int col) {
        return new Position(row, col);
    }

    private static void move(GameEngine engine, int fr, int fc, int tr, int tc) {
        MoveResult result = engine.applyMove(p(fr, fc), p(tr, tc));
        assertNotEquals(MoveResult.Type.INVALID, result.type(),
            String.format("Setup move (%d,%d)->(%d,%d) should be valid", fr, fc, tr, tc));
    }

    /**
     * Fool's Mate — the fastest possible checkmate (two moves each):
     *   1. f3  e5
     *   2. g4  Qh4#
     *
     * After Qh4: the queen on h4 checks along the h4-e1 diagonal. The king
     * cannot escape because d1/d2/e2 are blocked by own pieces and f2 is
     * still attacked by the queen.
     */
    @Test
    void foolsMate() {
        GameEngine engine = new GameEngine();

        move(engine, 1, 5, 2, 5); // 1. f2→f3
        move(engine, 6, 4, 4, 4); // 1... e7→e5
        move(engine, 1, 6, 3, 6); // 2. g2→g4
        MoveResult result = engine.applyMove(p(7, 3), p(3, 7)); // 2... Qd8→h4#

        assertEquals(MoveResult.Type.CHECKMATE, result.type(), "Qh4 should deliver checkmate");
        assertEquals(GameStatus.CHECKMATE, engine.getStatus());
    }

    /**
     * Back-rank rook mate: White King g6, White Rook a8, Black King h8 (to move).
     * The rook delivers check along rank 8; the king cannot escape because:
     *   - g8 is covered by the rook (same rank)
     *   - h7 and g7 are both covered by the white king on g6
     */
    @Test
    void backRankRookMate() {
        APiece[][] board = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                board[r][c] = new EmptySquare(new Position(r, c));

        board[5][6] = new King(new Position(5, 6), true);  // White King g6
        board[7][0] = new Rook(new Position(7, 0), true);  // White Rook a8
        board[7][7] = new King(new Position(7, 7), false); // Black King h8

        GameEngine engine = new GameEngine(board, false); // black to move

        assertEquals(GameStatus.CHECKMATE, engine.getStatus());
    }
}

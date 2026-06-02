package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import static org.junit.jupiter.api.Assertions.*;

class InsufficientMaterialTest {

    // Coordinate system: row 0 = white back rank (rank 1), row 7 = black back rank (rank 8).
    // Square color: (row + col) % 2 == 0 → light, 1 → dark.

    private static APiece[][] board(APiece... pieces) {
        APiece[][] b = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                b[r][c] = new EmptySquare(new Position(r, c));
        for (APiece p : pieces)
            b[p.getCurrentPosition().getRow()][p.getCurrentPosition().getCol()] = p;
        return b;
    }

    private static King   wk(int r, int c) { return new King  (new Position(r, c), true);  }
    private static King   bk(int r, int c) { return new King  (new Position(r, c), false); }
    private static Bishop wb(int r, int c) { return new Bishop(new Position(r, c), true);  }
    private static Bishop bb(int r, int c) { return new Bishop(new Position(r, c), false); }
    private static Knight wn(int r, int c) { return new Knight(new Position(r, c), true);  }
    private static Knight bn(int r, int c) { return new Knight(new Position(r, c), false); }
    private static Rook   wr(int r, int c) { return new Rook  (new Position(r, c), true);  }

    // --- Insufficient material cases ---

    @Test
    void kingVsKing() {
        GameEngine engine = new GameEngine(board(wk(0,4), bk(7,4)), true);
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, engine.getStatus());
    }

    @Test
    void kingVsKingAndBishop() {
        GameEngine engine = new GameEngine(board(wk(0,4), bk(7,4), bb(7,2)), true);
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, engine.getStatus());
    }

    @Test
    void kingAndBishopVsKing() {
        GameEngine engine = new GameEngine(board(wk(0,4), wb(0,2), bk(7,4)), true);
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, engine.getStatus());
    }

    @Test
    void kingVsKingAndKnight() {
        GameEngine engine = new GameEngine(board(wk(0,4), bk(7,4), bn(7,1)), true);
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, engine.getStatus());
    }

    @Test
    void kingAndKnightVsKing() {
        GameEngine engine = new GameEngine(board(wk(0,4), wn(0,1), bk(7,4)), true);
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, engine.getStatus());
    }

    @Test
    void kingAndBishopVsKingAndBishopSameColor() {
        // White bishop on a light square (0,0): (0+0)%2=0
        // Black bishop on a light square (2,0): (2+0)%2=0 — same color
        GameEngine engine = new GameEngine(board(wk(0,4), wb(0,0), bk(7,4), bb(2,0)), true);
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, engine.getStatus());
    }

    // --- NOT insufficient material ---

    @Test
    void kingAndBishopVsKingAndBishopOppositeColor() {
        // White bishop on a light square (0,0): (0+0)%2=0
        // Black bishop on a dark square  (1,0): (1+0)%2=1 — different color
        GameEngine engine = new GameEngine(board(wk(0,4), wb(0,0), bk(7,4), bb(1,0)), true);
        assertNotEquals(GameStatus.INSUFFICIENT_MATERIAL, engine.getStatus());
    }

    @Test
    void kingAndRookVsKing() {
        // A rook is sufficient to force checkmate
        GameEngine engine = new GameEngine(board(wk(0,4), wr(0,0), bk(7,4)), true);
        assertNotEquals(GameStatus.INSUFFICIENT_MATERIAL, engine.getStatus());
    }
}

package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import static org.junit.jupiter.api.Assertions.*;

class PawnPromotionTest {

    // Coordinate system: row 0 = white back rank (rank 1), row 7 = black back rank (rank 8).
    // White pawns advance toward row 7; black pawns advance toward row 0.

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

    @Test
    void whitePawnPromotionRequiresChoice() {
        // White pawn on the seventh rank (one step from promotion).
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[7][4] = new King(new Position(7, 4), false);
        board[6][3] = new Pawn(new Position(6, 3), true); // White pawn d7

        GameEngine engine = new GameEngine(board, true);

        MoveResult result = engine.applyMove(p(6, 3), p(7, 3)); // d7→d8
        assertEquals(MoveResult.Type.PROMOTION_NEEDED, result.getType(),
            "Moving pawn to the last rank should require a promotion choice");
    }

    @Test
    void whitePawnPromotesToQueen() {
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[7][4] = new King(new Position(7, 4), false);
        board[6][3] = new Pawn(new Position(6, 3), true);

        GameEngine engine = new GameEngine(board, true);
        engine.applyMove(p(6, 3), p(7, 3));
        engine.applyPromotion(p(7, 3), PromotionChoice.QUEEN);

        APiece promoted = engine.getPiece(7, 3);
        assertTrue(promoted.isPositionOccupied(), "Promoted piece should occupy the square");
        assertTrue(promoted.isWhite(), "Promoted piece should be white");
        assertEquals(PieceType.QUEEN, promoted.getPieceType(), "Piece should be a queen");
    }

    @Test
    void whitePawnPromotesToRook() {
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[7][4] = new King(new Position(7, 4), false);
        board[6][3] = new Pawn(new Position(6, 3), true);

        GameEngine engine = new GameEngine(board, true);
        engine.applyMove(p(6, 3), p(7, 3));
        engine.applyPromotion(p(7, 3), PromotionChoice.ROOK);

        assertEquals(PieceType.ROOK, engine.getPiece(7, 3).getPieceType());
    }

    @Test
    void whitePawnPromotesToBishop() {
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[7][4] = new King(new Position(7, 4), false);
        board[6][3] = new Pawn(new Position(6, 3), true);

        GameEngine engine = new GameEngine(board, true);
        engine.applyMove(p(6, 3), p(7, 3));
        engine.applyPromotion(p(7, 3), PromotionChoice.BISHOP);

        assertEquals(PieceType.BISHOP, engine.getPiece(7, 3).getPieceType());
    }

    @Test
    void whitePawnPromotesToKnight() {
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[7][4] = new King(new Position(7, 4), false);
        board[6][3] = new Pawn(new Position(6, 3), true);

        GameEngine engine = new GameEngine(board, true);
        engine.applyMove(p(6, 3), p(7, 3));
        engine.applyPromotion(p(7, 3), PromotionChoice.KNIGHT);

        assertEquals(PieceType.KNIGHT, engine.getPiece(7, 3).getPieceType());
    }

    @Test
    void blackPawnPromotesToQueen() {
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[7][4] = new King(new Position(7, 4), false);
        board[1][3] = new Pawn(new Position(1, 3), false); // Black pawn d2

        GameEngine engine = new GameEngine(board, false); // black to move

        MoveResult move = engine.applyMove(p(1, 3), p(0, 3)); // d2→d1
        assertEquals(MoveResult.Type.PROMOTION_NEEDED, move.getType());

        engine.applyPromotion(p(0, 3), PromotionChoice.QUEEN);

        APiece promoted = engine.getPiece(0, 3);
        assertFalse(promoted.isWhite(), "Promoted piece should be black");
        assertEquals(PieceType.QUEEN, promoted.getPieceType());
    }

    @Test
    void promotionWithCapture() {
        // White pawn on d7 captures a black rook on e8, landing on the last rank.
        APiece[][] board = emptyBoard();
        board[0][4] = new King(new Position(0, 4), true);
        board[7][4] = new King(new Position(7, 4), false);
        board[6][3] = new Pawn(new Position(6, 3), true);  // White pawn d7
        board[7][4] = new King(new Position(7, 4), false); // reuse — king stays
        board[7][2] = new Rook(new Position(7, 2), false); // Black rook c8 (diagonal capture target)

        GameEngine engine = new GameEngine(board, true);

        MoveResult move = engine.applyMove(p(6, 3), p(7, 2)); // d7xc8
        assertEquals(MoveResult.Type.PROMOTION_NEEDED, move.getType(),
            "Capture onto last rank should also require promotion");
        assertTrue(move.isCaptureOccurred(), "A capture should have been recorded");

        engine.applyPromotion(p(7, 2), PromotionChoice.QUEEN);
        assertEquals(PieceType.QUEEN, engine.getPiece(7, 2).getPieceType());
        assertFalse(engine.getPiece(6, 3).isPositionOccupied(), "Original pawn square should be empty");
    }
}

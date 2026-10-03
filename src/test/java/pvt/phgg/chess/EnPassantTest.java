package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import static org.junit.jupiter.api.Assertions.*;

class EnPassantTest {

    // Coordinate system: row 0 = white back rank, row 7 = black back rank.
    // White pawns start on row 1 and advance toward row 7.
    // Black pawns start on row 6 and advance toward row 0.

    private static Position p(int row, int col) {
        return new Position(row, col);
    }

    private static void move(GameEngine engine, int fromRow, int fromCol, int toRow, int toCol) {
        MoveResult result = engine.applyMove(p(fromRow, fromCol), p(toRow, toCol));
        assertNotEquals(MoveResult.Type.INVALID, result.type(),
            String.format("Setup move (%d,%d)->(%d,%d) should be valid", fromRow, fromCol, toRow, toCol));
    }

    @Test
    void whiteCapturesEnPassantLeft() {
        // White e-pawn advances to e5; black d-pawn double-pushes to d5; white plays exd6 e.p.
        GameEngine engine = new GameEngine();
        move(engine, 1, 4, 3, 4);  // white e2-e4
        move(engine, 7, 1, 5, 2);  // black Nb8-c6 (neutral)
        move(engine, 3, 4, 4, 4);  // white e4-e5
        move(engine, 6, 3, 4, 3);  // black d7-d5 (double push — en passant window opens)

        MoveResult result = engine.applyMove(p(4, 4), p(5, 3)); // white e5xd6 e.p.

        assertTrue(result.isValid(), "En passant capture should be valid");
        assertTrue(engine.getPiece(5, 3).isPositionOccupied(), "White pawn should be on (5,3)");
        assertTrue(engine.getPiece(5, 3).isWhite(), "Piece at (5,3) should be white");
        assertFalse(engine.getPiece(4, 3).isPositionOccupied(), "Captured black pawn at (4,3) should be gone");
        assertFalse(engine.getPiece(4, 4).isPositionOccupied(), "White pawn's origin square (4,4) should be empty");
    }

    @Test
    void whiteCapturesEnPassantRight() {
        // White e-pawn advances to e5; black f-pawn double-pushes to f5; white plays exf6 e.p.
        GameEngine engine = new GameEngine();
        move(engine, 1, 4, 3, 4);  // white e2-e4
        move(engine, 7, 1, 5, 2);  // black Nb8-c6 (neutral)
        move(engine, 3, 4, 4, 4);  // white e4-e5
        move(engine, 6, 5, 4, 5);  // black f7-f5 (double push — en passant window opens)

        MoveResult result = engine.applyMove(p(4, 4), p(5, 5)); // white e5xf6 e.p.

        assertTrue(result.isValid(), "En passant capture should be valid");
        assertTrue(engine.getPiece(5, 5).isPositionOccupied(), "White pawn should be on (5,5)");
        assertTrue(engine.getPiece(5, 5).isWhite(), "Piece at (5,5) should be white");
        assertFalse(engine.getPiece(4, 5).isPositionOccupied(), "Captured black pawn at (4,5) should be gone");
        assertFalse(engine.getPiece(4, 4).isPositionOccupied(), "White pawn's origin square (4,4) should be empty");
    }

    @Test
    void blackCapturesEnPassant() {
        // Black e-pawn advances to e4; white d-pawn double-pushes to d4; black plays exd3 e.p.
        GameEngine engine = new GameEngine();
        move(engine, 1, 0, 2, 0);  // white a2-a3 (neutral)
        move(engine, 6, 4, 4, 4);  // black e7-e5
        move(engine, 2, 0, 3, 0);  // white a3-a4 (neutral)
        move(engine, 4, 4, 3, 4);  // black e5-e4
        move(engine, 1, 3, 3, 3);  // white d2-d4 (double push — en passant window opens)

        MoveResult result = engine.applyMove(p(3, 4), p(2, 3)); // black e4xd3 e.p.

        assertTrue(result.isValid(), "Black en passant capture should be valid");
        assertTrue(engine.getPiece(2, 3).isPositionOccupied(), "Black pawn should be on (2,3)");
        assertFalse(engine.getPiece(2, 3).isWhite(), "Piece at (2,3) should be black");
        assertFalse(engine.getPiece(3, 3).isPositionOccupied(), "Captured white pawn at (3,3) should be gone");
        assertFalse(engine.getPiece(3, 4).isPositionOccupied(), "Black pawn's origin square (3,4) should be empty");
    }

    @Test
    void enPassantWindowExpires() {
        // Same setup as whiteCapturesEnPassantLeft, but white plays a different move instead.
        // On white's next turn the en passant opportunity should be gone.
        GameEngine engine = new GameEngine();
        move(engine, 1, 4, 3, 4);  // white e2-e4
        move(engine, 7, 1, 5, 2);  // black Nb8-c6
        move(engine, 3, 4, 4, 4);  // white e4-e5
        move(engine, 6, 3, 4, 3);  // black d7-d5 (en passant window opens for white)
        move(engine, 1, 0, 2, 0);  // white plays a2-a3 instead of capturing en passant
        move(engine, 5, 2, 3, 1);  // black Nc6-b4 (any black move)

        MoveResult result = engine.applyMove(p(4, 4), p(5, 3)); // white tries e5xd6 e.p. — too late

        assertEquals(MoveResult.Type.INVALID, result.type(), "En passant should be invalid after the window expires");
    }

    // --- En passant and check: the captured pawn is not on the target square, so the check test
    // must remove it from the board as well. ---

    private static APiece[][] emptyBoard() {
        APiece[][] b = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                b[r][c] = new EmptySquare(new Position(r, c));
        return b;
    }

    private static <T extends APiece> T place(APiece[][] b, T piece, boolean hasMoved) {
        if (hasMoved) piece.moved();
        b[piece.getCurrentPosition().getRow()][piece.getCurrentPosition().getCol()] = piece;
        return piece;
    }

    @Test
    void enPassantCanCaptureTheCheckingPawn() {
        // White Ke4, Pe5; Black Kh8, Pd7. Black plays d7-d5, checking the king on e4. Taking the
        // checking pawn en passant (exd6) is a legal way out of check.
        APiece[][] b = emptyBoard();
        place(b, new King(p(3, 4), true), true);
        place(b, new Pawn(p(4, 4), true), true);
        place(b, new King(p(7, 7), false), true);
        place(b, new Pawn(p(6, 3), false), false);
        GameEngine engine = new GameEngine(b, false);
        move(engine, 6, 3, 4, 3);  // black d7-d5+
        assertEquals(GameStatus.CHECK, engine.getStatus());

        MoveResult result = engine.applyMove(p(4, 4), p(5, 3)); // white e5xd6 e.p.

        assertTrue(result.isValid(), "En passant that removes the checking pawn should be legal");
        assertFalse(engine.getPiece(4, 3).isPositionOccupied(), "Captured pawn on d5 should be gone");
        assertNotEquals(GameStatus.CHECK, engine.getStatus());
    }

    @Test
    void enPassantCannotExposeTheKingAlongTheRank() {
        // White Ka5, Pb5; Black Rh5, Kh8, Pc7. After c7-c5 the two pawns are all that stand between
        // the rook and the king. bxc6 e.p. would take both off the rank, so it is illegal.
        APiece[][] b = emptyBoard();
        place(b, new King(p(4, 0), true), true);
        place(b, new Pawn(p(4, 1), true), true);
        place(b, new Rook(p(4, 7), false), true);
        place(b, new King(p(7, 7), false), true);
        place(b, new Pawn(p(6, 2), false), false);
        GameEngine engine = new GameEngine(b, false);
        move(engine, 6, 2, 4, 2);  // black c7-c5

        assertTrue(engine.getLegalMoves(p(4, 1)).stream().noneMatch(Position::isEnPassant),
                "bxc6 e.p. should not be offered");
        assertEquals(MoveResult.Type.INVALID, engine.applyMove(p(4, 1), p(5, 2)).type());
    }
}

package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Castling in non-standard (Chess960) starting geometry. Under FIDE Chess960 rules the king always
 * lands on the c-file (queenside) or g-file (kingside), and the rook on the d-file or f-file,
 * regardless of where either piece started. These tests build hand-picked layouts on row 0 (white)
 * that exercise the tricky geometry the standard-chess code never sees.
 */
class Chess960CastlingTest {

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

    /** Places a white king that knows its rook home files, plus a distant black king. */
    private static void placeKings(APiece[][] b, int whiteKingCol, int queensideRookFile, int kingsideRookFile) {
        King king = new King(new Position(0, whiteKingCol), true);
        king.setRookFiles(queensideRookFile, kingsideRookFile);
        b[0][whiteKingCol] = king;
        b[7][0] = new King(new Position(7, 0), false); // out of the way
    }

    private static boolean hasMoveTo(List<Position> moves, int row, int col) {
        return moves.stream().anyMatch(m -> m.getRow() == row && m.getCol() == col);
    }

    // --- King adjacent to its castling rook ---

    @Test
    void kingsideCastleWithKingAdjacentToRook() {
        // King on f1 (col 5), kingside rook on g1 (col 6): they effectively swap sides.
        // Castle by moving the king onto its own rook (the unambiguous Chess960 gesture).
        APiece[][] b = emptyBoard();
        placeKings(b, 5, -1, 6);
        b[0][6] = new Rook(new Position(0, 6), true);
        GameEngine engine = new GameEngine(b, true);

        MoveResult result = engine.applyMove(p(0, 5), p(0, 6)); // king f1 onto rook g1

        assertTrue(result.isValid(), "Castle should be legal with king adjacent to rook");
        assertTrue(engine.getPiece(0, 6).isKing(), "King lands on g1");
        assertTrue(engine.getPiece(0, 5).isRook(), "Rook lands on f1");
    }

    // --- Rook's destination coincides with the king's start square ---

    @Test
    void queensideCastleWhereRookDestinationIsKingStart() {
        // King on d1 (col 3 = rook's destination), queenside rook on a1 (col 0).
        APiece[][] b = emptyBoard();
        placeKings(b, 3, 0, -1);
        b[0][0] = new Rook(new Position(0, 0), true);
        GameEngine engine = new GameEngine(b, true);

        MoveResult result = engine.applyMove(p(0, 3), p(0, 0)); // king d1 onto rook a1

        assertTrue(result.isValid(), "Castle should be legal when rook's target is the king's old square");
        assertTrue(engine.getPiece(0, 2).isKing(), "King lands on c1");
        assertTrue(engine.getPiece(0, 3).isRook(), "Rook lands on d1 (king vacated it)");
        assertFalse(engine.getPiece(0, 0).isPositionOccupied(), "a1 empty");
    }

    // --- Rook's start square coincides with the king's destination square ---

    @Test
    void kingsideCastleWhereRookStartIsKingDestination() {
        // Kingside rook on g1 (col 6 = king's destination); king on e1 (col 4).
        APiece[][] b = emptyBoard();
        placeKings(b, 4, -1, 6);
        b[0][6] = new Rook(new Position(0, 6), true);
        GameEngine engine = new GameEngine(b, true);

        MoveResult result = engine.applyMove(p(0, 4), p(0, 6)); // king e1 -> g1

        assertTrue(result.isValid(), "Castle should be legal when rook starts on the king's target");
        assertTrue(engine.getPiece(0, 6).isKing(), "King lands on g1");
        assertTrue(engine.getPiece(0, 5).isRook(), "Rook lands on f1");
    }

    // --- King already starts on the destination file (king does not move) ---

    @Test
    void kingsideCastleWhereKingAlreadyOnGFile() {
        // King on g1 (col 6 = its own destination), kingside rook on h1 (col 7).
        // The king stays put; only the rook jumps over it to f1.
        APiece[][] b = emptyBoard();
        placeKings(b, 6, -1, 7);
        b[0][7] = new Rook(new Position(0, 7), true);
        GameEngine engine = new GameEngine(b, true);

        assertTrue(hasMoveTo(engine.getLegalMoves(p(0, 6)), 0, 6), "Castle offered from g1");

        MoveResult result = engine.applyMove(p(0, 6), p(0, 6)); // degenerate king move: stays on g1

        assertTrue(result.isValid(), "Castle should be legal even when the king does not move");
        assertTrue(engine.getPiece(0, 6).isKing(), "King remains on g1");
        assertTrue(engine.getPiece(0, 5).isRook(), "Rook lands on f1");
        assertFalse(engine.getPiece(0, 7).isPositionOccupied(), "h1 empty");
    }

    // --- Rook already starts on the destination file ---

    @Test
    void queensideCastleWhereRookAlreadyOnDFile() {
        // Queenside rook on d1 (col 3 = its own destination); king on e1 (col 4).
        APiece[][] b = emptyBoard();
        placeKings(b, 4, 3, 7);
        b[0][3] = new Rook(new Position(0, 3), true);
        b[0][7] = new Rook(new Position(0, 7), true);
        GameEngine engine = new GameEngine(b, true);

        MoveResult result = engine.applyMove(p(0, 4), p(0, 2)); // king e1 -> c1

        assertTrue(result.isValid(), "Castle should be legal when the rook already sits on its target");
        assertTrue(engine.getPiece(0, 2).isKing(), "King lands on c1");
        assertTrue(engine.getPiece(0, 3).isRook(), "Rook remains on d1");
    }

    // --- Negative cases in 960 geometry ---

    @Test
    void castleBlockedByPieceBetweenKingAndDestination() {
        // King on b1 (col 1), queenside rook on a1 (col 0); a bishop sits on c1 (the king's target).
        APiece[][] b = emptyBoard();
        placeKings(b, 1, 0, -1);
        b[0][0] = new Rook(new Position(0, 0), true);
        b[0][2] = new Bishop(new Position(0, 2), true); // blocks c1
        GameEngine engine = new GameEngine(b, true);

        assertFalse(hasMoveTo(engine.getLegalMoves(p(0, 1)), 0, 2),
                "Castle blocked when a piece occupies the king's path");
    }

    @Test
    void castleBlockedThroughAttackedSquare() {
        // King on f1 (col 5) castling kingside to g1; black rook on d8 attacks g-file? No —
        // put a black rook on g8 attacking g1 (the king's destination).
        APiece[][] b = emptyBoard();
        placeKings(b, 5, -1, 7);
        b[0][7] = new Rook(new Position(0, 7), true);
        b[7][6] = new Rook(new Position(7, 6), false); // black rook g8, attacks g1 (king's target)
        GameEngine engine = new GameEngine(b, true);

        assertFalse(hasMoveTo(engine.getLegalMoves(p(0, 5)), 0, 6),
                "Castle blocked when the king's destination is attacked");
    }

    @Test
    void castleBlockedWhenRookHasMoved() {
        APiece[][] b = emptyBoard();
        placeKings(b, 4, -1, 7);
        Rook rook = new Rook(new Position(0, 7), true);
        rook.moved();
        b[0][7] = rook;
        GameEngine engine = new GameEngine(b, true);

        assertFalse(hasMoveTo(engine.getLegalMoves(p(0, 4)), 0, 6),
                "Castle unavailable after the rook has moved");
    }
}

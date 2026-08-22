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

    // --- Castling rook vacating a square that blocks an attack (Chess960-specific) ---

    @Test
    void queensideCastleIllegalWhenRookDepartureUncoversCheckOnDestination() {
        // King e1, white queenside rook on b1 (rook home files 1/7). A black rook on a1 is blocked
        // from the king by the white rook on b1, so white is NOT currently in check. Castling
        // queenside moves that rook off b1 (to d1), uncovering the a1-rook's attack on c1 — the
        // king's destination. The castle must be illegal (the king would end in check).
        APiece[][] b = emptyBoard();
        King king = new King(new Position(0, 4), true);
        king.setRookFiles(1, 7);
        b[0][4] = king;
        b[0][1] = new Rook(new Position(0, 1), true);   // white queenside rook b1
        b[0][0] = new Rook(new Position(0, 0), false);  // black rook a1, directly behind it
        b[7][7] = new King(new Position(7, 7), false);  // black king out of the way
        GameEngine engine = new GameEngine(b, true);

        assertFalse(hasMoveTo(engine.getLegalMoves(p(0, 4)), 0, 2),
                "Queenside castle must be illegal — the rook leaving b1 uncovers a check on c1");

        // The king-onto-rook gesture must be rejected too (king must not land on c1).
        engine.applyMove(p(0, 4), p(0, 1));
        assertFalse(engine.getPiece(0, 2).isKing(), "King must not have castled into check");
    }

    @Test
    void castleLegalWhenRankAttackerIsBlockedByANonCastlingPiece() {
        // Positive control for the fix: a black rook on a1 is shielded from the king's path by a
        // white knight on b1 that does NOT move. Since only the castling rook is removed when
        // checking safety, the knight still blocks and kingside castling stays legal.
        APiece[][] b = emptyBoard();
        King king = new King(new Position(0, 4), true);
        king.setRookFiles(1, 7);
        b[0][4] = king;
        b[0][7] = new Rook(new Position(0, 7), true);   // kingside rook h1 — the castling rook
        b[0][1] = new Knight(new Position(0, 1), true); // non-castling blocker on b1 (stays put)
        b[0][0] = new Rook(new Position(0, 0), false);  // black rook a1, blocked by the knight
        b[7][7] = new King(new Position(7, 7), false);
        GameEngine engine = new GameEngine(b, true);

        assertTrue(hasMoveTo(engine.getLegalMoves(p(0, 4)), 0, 6),
                "Kingside castle stays legal — the a1 rook is blocked by a piece that does not move");
        MoveResult result = engine.applyMove(p(0, 4), p(0, 7)); // king onto kingside rook
        assertTrue(result.isValid());
        assertTrue(engine.getPiece(0, 6).isKing(), "King lands on g1");
        assertTrue(engine.getPiece(0, 5).isRook(), "Rook lands on f1");
    }

    // --- Black castling in Chess960 geometry ---

    @Test
    void blackChess960KingsideCastle() {
        // Black king on f8 (col 5) with its kingside rook on h8 (col 7): king → g8, rook → f8.
        APiece[][] b = emptyBoard();
        b[0][0] = new King(new Position(0, 0), true); // white king out of the way
        King king = new King(new Position(7, 5), false);
        king.setRookFiles(0, 7);
        b[7][5] = king;
        b[7][7] = new Rook(new Position(7, 7), false);
        GameEngine engine = new GameEngine(b, false); // black to move

        MoveResult result = engine.applyMove(p(7, 5), p(7, 7)); // king f8 onto rook h8

        assertTrue(result.isValid(), "Black should be able to castle kingside in a 960 layout");
        assertTrue(engine.getPiece(7, 6).isKing(), "Black king lands on g8");
        assertTrue(engine.getPiece(7, 5).isRook(), "Black rook lands on f8");
        assertFalse(engine.getPiece(7, 7).isPositionOccupied(), "h8 empty");
    }
}

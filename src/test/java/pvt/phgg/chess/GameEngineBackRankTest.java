package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.PieceType;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineBackRankTest {

    @Test
    void standardConstructorMatchesStandardBackRank() {
        GameEngine standard = new GameEngine();
        GameEngine explicit = new GameEngine(GameEngine.STANDARD_BACK_RANK);
        for (int col = 0; col < 8; col++) {
            assertEquals(standard.getPiece(0, col).getPieceType(),
                    explicit.getPiece(0, col).getPieceType(),
                    "White back rank should match at file " + col);
            assertEquals(standard.getPiece(7, col).getPieceType(),
                    explicit.getPiece(7, col).getPieceType(),
                    "Black back rank should match at file " + col);
        }
    }

    @Test
    void backRankIsPlacedPieceForPiece() {
        String backRank = "BBQNNRKR";
        GameEngine engine = new GameEngine(backRank);
        for (int col = 0; col < 8; col++) {
            PieceType expected = typeOf(backRank.charAt(col));
            assertEquals(expected, engine.getPiece(0, col).getPieceType(),
                    "White " + expected + " expected at file " + col);
            assertTrue(engine.getPiece(0, col).isWhite());
            assertEquals(expected, engine.getPiece(7, col).getPieceType(),
                    "Black " + expected + " expected at file " + col);
            assertFalse(engine.getPiece(7, col).isWhite());
        }
    }

    @Test
    void pawnsAndEmptyRanksAreSetUpRegardlessOfBackRank() {
        GameEngine engine = new GameEngine("BBQNNRKR");
        for (int col = 0; col < 8; col++) {
            assertEquals(PieceType.PAWN, engine.getPiece(1, col).getPieceType(), "White pawn row");
            assertEquals(PieceType.PAWN, engine.getPiece(6, col).getPieceType(), "Black pawn row");
            for (int row = 2; row < 6; row++) {
                assertFalse(engine.getPiece(row, col).isPositionOccupied(), "Middle ranks empty");
            }
        }
    }

    @Test
    void invalidBackRankIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GameEngine("RNBQKBN"));   // too short
        assertThrows(IllegalArgumentException.class, () -> new GameEngine("RNBQKBNRX")); // too long
        assertThrows(IllegalArgumentException.class, () -> new GameEngine("RNBQKBNX"));  // bad char
    }

    private static PieceType typeOf(char c) {
        return switch (c) {
            case 'R' -> PieceType.ROOK;
            case 'N' -> PieceType.KNIGHT;
            case 'B' -> PieceType.BISHOP;
            case 'Q' -> PieceType.QUEEN;
            case 'K' -> PieceType.KING;
            default  -> throw new IllegalArgumentException("bad char");
        };
    }
}

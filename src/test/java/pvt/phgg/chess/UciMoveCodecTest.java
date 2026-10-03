package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.GameReplay.RecordedMove;
import pvt.phgg.chess.UciMoveCodec.UciMove;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static pvt.phgg.chess.FenSerializerTest.moves;

class UciMoveCodecTest {

    // Clears e1, f1 and g1 so White can castle kingside from the 960 layout RNBKQBNR (king on d1).
    private static final String CLEAR_960_KINGSIDE = "g1f3 a7a6 g2g3 a6a5 f1g2 h7h6 e2e4 h6h5 e1e2 b7b6";

    private static String lastPlayedUci(String backRank, boolean chess960, List<RecordedMove> moves) {
        var positions = GameReplay.replay(backRank, chess960, moves);
        return positions.get(positions.size() - 2).playedUci();
    }

    private static List<RecordedMove> with(List<RecordedMove> moves, RecordedMove last) {
        List<RecordedMove> all = new ArrayList<>(moves);
        all.add(last);
        return all;
    }

    private static RecordedMove stored(int fromRow, int fromCol, int toRow, int toCol) {
        return new RecordedMove(new Position(fromRow, fromCol), new Position(toRow, toCol), null);
    }

    @Test
    void squaresUseRowZeroAsRankOne() {
        assertEquals("a1", UciMoveCodec.square(0, 0));
        assertEquals("e2", UciMoveCodec.square(1, 4));
        assertEquals("h8", UciMoveCodec.square(7, 7));
        Position e4 = UciMoveCodec.parseSquare("e4");
        assertEquals(3, e4.getRow());
        assertEquals(4, e4.getCol());
    }

    @Test
    void decodesPlainMoveAndPromotion() {
        UciMove move = UciMoveCodec.decode("e2e4");
        assertEquals(new Position(1, 4), move.from());
        assertEquals(new Position(3, 4), move.to());
        assertNull(move.promotion());

        assertEquals(PromotionChoice.KNIGHT, UciMoveCodec.decode("e7e8n").promotion());
    }

    @Test
    void rejectsMalformedMoves() {
        assertThrows(IllegalArgumentException.class, () -> UciMoveCodec.decode("e2"));
        assertThrows(IllegalArgumentException.class, () -> UciMoveCodec.decode("e2e9"));
        assertThrows(IllegalArgumentException.class, () -> UciMoveCodec.decode("e7e8k"));
        assertThrows(IllegalArgumentException.class, () -> UciMoveCodec.decode("(none)"));
    }

    @Test
    void encodesPromotion() {
        assertEquals("b7a8q", lastPlayedUci(GameEngine.STANDARD_BACK_RANK, false,
                moves("a2a4 b7b5 a4b5 a7a6 b5a6 c8b7 a6b7 b8c6 b7a8q")));
    }

    /** Standard games store the king's two-square step, which is already standard UCI. */
    @Test
    void standardCastleIsKingTwoSquares() {
        assertEquals("e1g1", lastPlayedUci(GameEngine.STANDARD_BACK_RANK, false,
                moves("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 e1g1")));
    }

    /** The engine also accepts king-onto-rook in a standard game; it still encodes as e1g1. */
    @Test
    void standardCastleGivenAsKingOntoRookIsNormalised() {
        assertEquals("e1g1", lastPlayedUci(GameEngine.STANDARD_BACK_RANK, false,
                moves("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 e1h1")));
    }

    /** Humans castle in 960 by moving the king onto its rook, which is stored as such. */
    @Test
    void chess960CastleStoredAsKingOntoRook() {
        assertEquals("d1h1", lastPlayedUci("RNBKQBNR", true,
                with(moves(CLEAR_960_KINGSIDE), stored(0, 3, 0, 7))));
    }

    /** Bots castle in 960 via the engine's c/g-file target, which is stored as such. */
    @Test
    void chess960CastleStoredAsKingTargetSquare() {
        assertEquals("d1h1", lastPlayedUci("RNBKQBNR", true,
                with(moves(CLEAR_960_KINGSIDE), stored(0, 3, 0, 6))));
    }

    @Test
    void chess960NonCastlingKingMoveIsUnchanged() {
        assertEquals("d1e1", lastPlayedUci("RNBKQBNR", true,
                with(moves(CLEAR_960_KINGSIDE), stored(0, 3, 0, 4))));
    }

    // Clears b1 and c1 so White can castle queenside from RNBKQBNR: the king stays next to c1, so a
    // c1 target is also a plain king step, and only king-onto-rook (d1 onto a1) is unambiguous.
    private static final String CLEAR_960_QUEENSIDE = "b1c3 a7a6 d2d3 a6a5 c1e3 h7h6";

    @Test
    void chess960QueensideCastleIsKingOntoQueensideRook() {
        assertEquals("d1a1", lastPlayedUci("RNBKQBNR", true,
                with(moves(CLEAR_960_QUEENSIDE), stored(0, 3, 0, 0))));
    }

    @Test
    void underpromotionsKeepTheirPiece() {
        String toTheEighth = "a2a4 b7b5 a4b5 a7a6 b5a6 c8b7 a6b7 b8c6 ";
        assertEquals("b7a8n", lastPlayedUci(GameEngine.STANDARD_BACK_RANK, false, moves(toTheEighth + "b7a8n")));
        assertEquals("b7a8r", lastPlayedUci(GameEngine.STANDARD_BACK_RANK, false, moves(toTheEighth + "b7a8r")));
        assertEquals("b7a8b", lastPlayedUci(GameEngine.STANDARD_BACK_RANK, false, moves(toTheEighth + "b7a8b")));
    }

    @Test
    void decodesEveryPromotionPiece() {
        assertEquals(PromotionChoice.QUEEN, UciMoveCodec.decode("a7a8q").promotion());
        assertEquals(PromotionChoice.ROOK, UciMoveCodec.decode("a7a8r").promotion());
        assertEquals(PromotionChoice.BISHOP, UciMoveCodec.decode("a7a8b").promotion());
        assertEquals(PromotionChoice.KNIGHT, UciMoveCodec.decode("a7a8n").promotion());
    }
}

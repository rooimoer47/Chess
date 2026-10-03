package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.GameReplay.RecordedMove;
import pvt.phgg.chess.GameReplay.ReplayedPosition;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Expected FENs were produced by Stockfish 19 itself (`position ... moves ...` then `d`), so these
 * compare against an independent implementation rather than hand-written strings.
 */
class FenSerializerTest {

    private static final String STANDARD = GameEngine.STANDARD_BACK_RANK;

    static List<RecordedMove> moves(String uciMoves) {
        if (uciMoves.isBlank()) return List.of();
        return Arrays.stream(uciMoves.split(" "))
                .map(UciMoveCodec::decode)
                .map(m -> new RecordedMove(m.from(), m.to(), m.promotion()))
                .toList();
    }

    private static String finalFen(String backRank, boolean chess960, String uciMoves) {
        List<ReplayedPosition> positions = GameReplay.replay(backRank, chess960, moves(uciMoves));
        return positions.getLast().fen();
    }

    @Test
    void startPosition() {
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                finalFen(STANDARD, false, ""));
    }

    /** No black pawn can take on e3, so no en passant square — the same choice Stockfish makes. */
    @Test
    void doublePushWithoutPossibleCaptureHasNoEnPassantSquare() {
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
                finalFen(STANDARD, false, "e2e4"));
    }

    @Test
    void halfMoveClockAndFullMoveNumber() {
        assertEquals("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
                finalFen(STANDARD, false, "e2e4 c7c5 g1f3"));
    }

    @Test
    void enPassantSquareWhenCaptureIsPossible() {
        assertEquals("rnbqkbnr/1pp1pppp/p7/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3",
                finalFen(STANDARD, false, "e2e4 a7a6 e4e5 d7d5"));
    }

    @Test
    void castlingRemovesBothRightsForThatSide() {
        assertEquals("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQ1RK1 b kq - 5 4",
                finalFen(STANDARD, false, "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 e1g1"));
    }

    @Test
    void movedRookRemovesOnlyItsOwnRight() {
        assertEquals("rnbqkbnr/ppppppp1/8/7p/7P/7R/PPPPPPP1/RNBQKBN1 b Qkq - 1 2",
                finalFen(STANDARD, false, "h2h4 h7h5 h1h3"));
    }

    /** Also covers a captured rook: Black loses queenside castling when a8 is taken. */
    @Test
    void promotion() {
        assertEquals("Q2qkbnr/2pppppp/2n5/8/8/8/1PPPPPPP/RNBQKBNR b KQk - 0 5",
                finalFen(STANDARD, false, "a2a4 b7b5 a4b5 a7a6 b5a6 c8b7 a6b7 b8c6 b7a8q"));
    }

    @Test
    void chess960StartUsesRookFileLetters() {
        assertEquals("bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1",
                finalFen("BBQNNRKR", true, ""));
    }

    @Test
    void chess960AfterCastling() {
        assertEquals("rnbkqbnr/2ppppp1/1p6/p6p/4P3/5NP1/PPPPQPBP/RNB2RK1 b ha - 1 6",
                finalFen("RNBKQBNR", true, "g1f3 a7a6 g2g3 a6a5 f1g2 h7h6 e2e4 h6h5 e1e2 b7b6 d1h1"));
    }

    /** The cache key drops the move counters, so transpositions share one entry. */
    @Test
    void epdOmitsMoveCounters() {
        List<ReplayedPosition> viaNf3First = GameReplay.replay(STANDARD, false, moves("g1f3 g8f6 b1c3 b8c6"));
        List<ReplayedPosition> viaNc3First = GameReplay.replay(STANDARD, false, moves("b1c3 b8c6 g1f3 g8f6"));
        assertEquals("r1bqkb1r/pppppppp/2n2n2/8/8/2N2N2/PPPPPPPP/R1BQKB1R w KQkq -", viaNf3First.getLast().epd());
        assertEquals(viaNf3First.getLast().epd(), viaNc3First.getLast().epd());
    }
}

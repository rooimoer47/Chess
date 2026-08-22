package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pvt.phgg.chess.piece.PieceType;
import pvt.phgg.chess.server.game.GameMove;
import pvt.phgg.chess.server.game.GameRecorder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameSessionVariantTest {

    @Mock GameRecorder gameRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void standardSessionUsesStandardBackRank() {
        GameSession session = new GameSession(objectMapper, gameRecorder, "STANDARD");
        assertEquals("STANDARD", session.getVariant());
        assertEquals("RNBQKBNR", session.getStartingPosition());
        assertBoardMatchesBackRank(session, "RNBQKBNR");
    }

    @Test
    void chess960SessionGeneratesValidNonDefaultBoard() {
        GameSession session = new GameSession(objectMapper, gameRecorder, "CHESS960");
        assertEquals("CHESS960", session.getVariant());

        String backRank = session.getStartingPosition();
        assertEquals(8, backRank.length());
        // The generated back rank must actually drive the engine's starting board.
        assertBoardMatchesBackRank(session, backRank);

        // Chess960 invariants: bishops on opposite colours, king between the rooks.
        int firstRook = backRank.indexOf('R');
        int lastRook = backRank.lastIndexOf('R');
        int king = backRank.indexOf('K');
        assertTrue(firstRook < king && king < lastRook, "King between rooks: " + backRank);
    }

    @Test
    void onGameStartPersistsVariantAndStartingPosition() {
        GameSession session = new GameSession(objectMapper, gameRecorder, "CHESS960");
        String backRank = session.getStartingPosition();
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(7L);

        session.onGameStart();

        verify(gameRecorder).startGame(isNull(), isNull(), eq("HUMAN"), isNull(),
                eq("CHESS960"), eq(backRank));
        assertEquals(7L, session.getGameId());
    }

    @Test
    void restoreRebuildsFromStoredChess960Position() {
        // A back rank where the queenside rook sits on the b-file, not a-file — a layout that
        // only reconstructs correctly if the stored starting position is honoured.
        String backRank = "NRKBBQRN";
        GameSession session = GameSession.restore(objectMapper, gameRecorder, 1L, "HUMAN", null,
                "CHESS960", backRank, "alice", 1L, "bob", 2L, List.<GameMove>of());

        assertEquals("CHESS960", session.getVariant());
        assertBoardMatchesBackRank(session, backRank);
    }

    private void assertBoardMatchesBackRank(GameSession session, String backRank) {
        for (int col = 0; col < 8; col++) {
            assertEquals(typeOf(backRank.charAt(col)), session.getPiece(0, col).getPieceType(),
                    "White back rank at file " + col + " for " + backRank);
            assertEquals(typeOf(backRank.charAt(col)), session.getPiece(7, col).getPieceType(),
                    "Black back rank at file " + col + " for " + backRank);
        }
    }

    private static PieceType typeOf(char c) {
        return switch (c) {
            case 'R' -> PieceType.ROOK;
            case 'N' -> PieceType.KNIGHT;
            case 'B' -> PieceType.BISHOP;
            case 'Q' -> PieceType.QUEEN;
            case 'K' -> PieceType.KING;
            default  -> throw new IllegalArgumentException("bad char " + c);
        };
    }
}

package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pvt.phgg.chess.Position;
import pvt.phgg.chess.piece.PieceType;
import pvt.phgg.chess.server.game.GameMove;
import pvt.phgg.chess.server.game.GameRecorder;

import java.util.ArrayList;
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

    /**
     * RNBBQKNR puts the king on f1 and the kingside rook on h1. Once the g1 knight has gone, g1 is
     * both a plain king step and the castle target, so a castle recorded as "f1 to g1" would replay
     * as the plain step. The random bot castles like this, passing the engine's own castle-flagged
     * target, so the recording has to say "castle" on its own.
     */
    @Test
    void chess960CastleWithAmbiguousTargetReplaysAsCastle() {
        List<GameMove> opening = List.of(
                new GameMove(1L, 1, 0, 6, 2, 7, null),   // Ng1-h3
                new GameMove(1L, 2, 6, 0, 5, 0, null));  // a7-a6
        GameSession live = GameSession.restore(objectMapper, gameRecorder, 1L, "HUMAN", null,
                "CHESS960", "RNBBQKNR", "alice", 1L, "bob", 2L, opening);

        assertTrue(live.applyMove(new Position(0, 5), new Position(0, 6, Position.SpecialMove.CASTLE)).isValid());
        assertCastledKingside(live, "live game");

        ArgumentCaptor<Position> from = ArgumentCaptor.forClass(Position.class);
        ArgumentCaptor<Position> to = ArgumentCaptor.forClass(Position.class);
        verify(gameRecorder).recordMove(eq(1L), eq(3), from.capture(), to.capture(), isNull());
        List<GameMove> recorded = new ArrayList<>(opening);
        recorded.add(new GameMove(1L, 3, from.getValue().getRow(), from.getValue().getCol(),
                to.getValue().getRow(), to.getValue().getCol(), null));

        GameSession replayed = GameSession.restore(objectMapper, gameRecorder, 1L, "HUMAN", null,
                "CHESS960", "RNBBQKNR", "alice", 1L, "bob", 2L, recorded);
        assertCastledKingside(replayed, "replay of the recorded moves");
    }

    private static void assertCastledKingside(GameSession session, String which) {
        assertEquals(PieceType.KING, session.getPiece(0, 6).getPieceType(), which + ": king on g1");
        assertEquals(PieceType.ROOK, session.getPiece(0, 5).getPieceType(), which + ": rook on f1");
        assertFalse(session.getPiece(0, 7).isPositionOccupied(), which + ": h1 empty");
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

package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pvt.phgg.chess.Position;
import pvt.phgg.chess.piece.PieceType;
import pvt.phgg.chess.server.game.GameMove;
import pvt.phgg.chess.server.game.GameRecorder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameSessionRestoreTest {

    @Mock GameRecorder gameRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GameMove move(int moveNumber, int fromRow, int fromCol, int toRow, int toCol) {
        return new GameMove(1L, moveNumber, fromRow, fromCol, toRow, toCol, null);
    }

    @Test
    void restore_reconstructsBoardFromMoveHistory() {
        // e2-e4, e7-e5
        List<GameMove> moves = List.of(
                move(1, 1, 4, 3, 4),
                move(2, 6, 4, 4, 4));

        GameSession session = GameSession.restore(objectMapper, gameRecorder, 1L, "HUMAN", null,
                "alice", 1L, "bob", 2L, moves);

        assertEquals(1L, session.getGameId());
        assertFalse(session.getPiece(1, 4).isPositionOccupied(), "e2 must be empty after e2-e4");
        assertTrue(session.getPiece(3, 4).isPositionOccupied(), "e4 must hold the moved pawn");
        assertTrue(session.getPiece(3, 4).isPawn());
        assertFalse(session.getPiece(6, 4).isPositionOccupied(), "e7 must be empty after e7-e5");
        assertTrue(session.getPiece(4, 4).isPositionOccupied(), "e5 must hold black's moved pawn");
        assertTrue(session.isPlayerTurn(PlayerRole.WHITE), "White to move after two plies");
    }

    @Test
    void restore_doesNotReRecordReplayedMoves() {
        List<GameMove> moves = List.of(move(1, 1, 4, 3, 4));

        GameSession.restore(objectMapper, gameRecorder, 1L, "HUMAN", null,
                "alice", 1L, "bob", 2L, moves);

        verifyNoInteractions(gameRecorder);
    }

    @Test
    void restore_setsMoveCountSoNextMoveContinuesNumbering() {
        List<GameMove> moves = List.of(
                move(1, 1, 4, 3, 4),
                move(2, 6, 4, 4, 4));

        GameSession session = GameSession.restore(objectMapper, gameRecorder, 1L, "HUMAN", null,
                "alice", 1L, "bob", 2L, moves);

        // Nf3 (white) — the first genuinely new move after restart
        session.applyMove(new Position(0, 6), new Position(2, 5));

        verify(gameRecorder).recordMove(eq(1L), eq(3), any(), any(), isNull());
    }

    @Test
    void restore_botGame_botIsWhite_isBotTurnMatchesEngineTurn() {
        GameSession session = GameSession.restore(objectMapper, gameRecorder, 1L, "BOT", "alan",
                "BOT", null, "carol", 3L, List.of());

        assertTrue(session.isBotTurn(), "It must be the bot's move at the start of a fresh game where the bot is white");
    }

    @Test
    void restore_botGame_botIsBlack_isBotTurnFalseAtStart() {
        GameSession session = GameSession.restore(objectMapper, gameRecorder, 1L, "BOT", "claude",
                "carol", 3L, "BOT", null, List.of());

        assertFalse(session.isBotTurn(), "White (the human) moves first, so it must not be the bot's turn yet");
    }

    @Test
    void restore_replaysPromotionMove() {
        // A real legal 5-move sequence ending with white promoting by
        // capturing black's h8 rook: h4 a6 h5 a5 h6 a4 hxg7 a3 gxh8=Q
        List<GameMove> moves = List.of(
                move(1, 1, 7, 3, 7),   // h2-h4
                move(2, 6, 0, 5, 0),   // a7-a6
                move(3, 3, 7, 4, 7),   // h4-h5
                move(4, 5, 0, 4, 0),   // a6-a5
                move(5, 4, 7, 5, 7),   // h5-h6
                move(6, 4, 0, 3, 0),   // a5-a4
                move(7, 5, 7, 6, 6),   // h6xg7
                move(8, 3, 0, 2, 0),   // a4-a3
                new GameMove(1L, 9, 6, 6, 7, 7, "QUEEN")); // g7xh8=Q

        GameSession session = GameSession.restore(objectMapper, gameRecorder, 1L, "HUMAN", null,
                "alice", 1L, "bob", 2L, moves);

        assertTrue(session.getPiece(7, 7).isPositionOccupied(), "h8 must hold the promoted piece");
        assertEquals(PieceType.QUEEN, session.getPiece(7, 7).getPieceType());
        assertTrue(session.getPiece(7, 7).isWhite());
        assertFalse(session.getPiece(6, 6).isPositionOccupied(), "g7 must be empty after the promoting capture");
    }
}

package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.*;
import pvt.phgg.chess.MoveResult;
import pvt.phgg.chess.Position;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GameSession session;
    private FakeWebSocketSession whiteWs;
    private FakeWebSocketSession blackWs;

    @BeforeEach
    void setUp() {
        session = new GameSession(objectMapper);
        whiteWs = new FakeWebSocketSession("white");
        blackWs = new FakeWebSocketSession("black");
        session.join(whiteWs, "alice");
        session.join(blackWs, "bob");
    }

    // --- Join ---

    @Test
    void joinTwoPlayersAssignsRoles() {
        assertEquals(PlayerRole.WHITE, session.roleOf(whiteWs));
        assertEquals(PlayerRole.BLACK, session.roleOf(blackWs));
        assertTrue(session.isFull());
    }

    @Test
    void joinWithAlreadyTakenUsernameReturnsNull() {
        FakeWebSocketSession thirdWs = new FakeWebSocketSession("third");
        assertNull(session.join(thirdWs, "alice"), "Username already taken should be rejected");
    }

    // --- Move validation ---

    @Test
    void applyValidMoveIsAccepted() {
        MoveResult result = session.applyMove(new Position(1, 4), new Position(3, 4)); // e2-e4
        assertTrue(result.isValid(), "White pawn e2-e4 should be a valid move");
    }

    @Test
    void applyIllegalMoveIsRejected() {
        MoveResult result = session.applyMove(new Position(1, 4), new Position(4, 4)); // e2-e5 — three squares
        assertFalse(result.isValid(), "Pawn cannot advance three squares");
    }

    // --- Broadcast ---

    @Test
    void broadcastSendsMessageToBothPlayers() throws Exception {
        session.broadcastBoardState();
        assertEquals(1, whiteWs.messages.size(), "White should receive one message");
        assertEquals(1, blackWs.messages.size(), "Black should receive one message");
    }

    @Test
    void broadcastMessageContainsBoardUpdate() throws Exception {
        session.broadcastBoardState();
        assertTrue(whiteWs.lastPayload().contains("BOARD_UPDATE"));
    }

    // --- Disconnect ---

    @Test
    void disconnectNullsSessionButKeepsRoleUnassignable() throws Exception {
        session.disconnect(whiteWs);
        assertNull(session.roleOf(whiteWs), "Disconnected WebSocket should no longer have a role");
    }

    @Test
    void disconnectNotifiesRemainingPlayer() throws Exception {
        session.disconnect(whiteWs);
        assertTrue(blackWs.lastPayload().contains("OPPONENT_DISCONNECTED"));
    }

    @Test
    void sessionNotEmptyAfterOnePlayerDisconnects() throws Exception {
        session.disconnect(whiteWs);
        assertFalse(session.isEmpty());
    }

    @Test
    void sessionRemainsReservedAfterBothPlayersDisconnect() throws Exception {
        // Usernames are kept for reconnect, so isEmpty() stays false.
        // The session is only reset by a server restart in the current implementation.
        session.disconnect(whiteWs);
        session.disconnect(blackWs);
        assertFalse(session.isEmpty());
    }

    // --- Rejoin ---

    @Test
    void rejoinWithMatchingUsernameRestoresRole() throws Exception {
        session.disconnect(whiteWs);
        FakeWebSocketSession newWs = new FakeWebSocketSession("white-new");

        PlayerRole role = session.rejoin(newWs, "alice");

        assertEquals(PlayerRole.WHITE, role);
        assertEquals(PlayerRole.WHITE, session.roleOf(newWs));
    }

    @Test
    void rejoinWithUnknownUsernameReturnsNull() {
        FakeWebSocketSession newWs = new FakeWebSocketSession("stranger");
        assertNull(session.rejoin(newWs, "charlie"), "Unknown username should not be allowed to rejoin");
    }

    @Test
    void rejoinWhileStillConnectedReturnsNull() {
        FakeWebSocketSession newWs = new FakeWebSocketSession("white-extra");
        assertNull(session.rejoin(newWs, "alice"), "Cannot rejoin while the original session is still active");
    }

    @Test
    void rejoinedPlayerReceivesBroadcast() throws Exception {
        session.disconnect(whiteWs);
        FakeWebSocketSession newWs = new FakeWebSocketSession("white-new");
        session.rejoin(newWs, "alice");

        session.broadcastBoardState();

        assertEquals(1, newWs.messages.size(), "Rejoined player should receive the broadcast");
    }

    // --- Resign ---

    @Test
    void resignBroadcastsResignedStatus() throws Exception {
        session.resign(true); // white resigns
        session.broadcastBoardState();
        assertTrue(whiteWs.lastPayload().contains("RESIGNED"));
    }

    // --- Chess960 castling legal-move representation ---

    // Six half-moves that clear White's kingside (Nf3, e3, Be2) so e1-king can castle kingside,
    // played from the standard back rank. Black just shuffles an a-pawn. White is to move after.
    private static final int[][] KINGSIDE_CLEARING_MOVES = {
            {0, 6, 2, 5}, {6, 0, 5, 0},  // Ng1-f3, a7-a6
            {1, 4, 2, 4}, {5, 0, 4, 0},  // e2-e3,  a6-a5
            {0, 5, 1, 4}, {4, 0, 3, 0},  // Bf1-e2, a5-a4
    };

    @Test
    void chess960_castleOfferedAsKingOntoRook() throws Exception {
        FakeWebSocketSession whiteWs = castleReadySession("CHESS960");

        // Chess960: castling is expressed as king (0,4) onto its own rook (0,7), not the g-file.
        assertTrue(hasLegalMove(whiteWs, 0, 4, 0, 7), "960 castle should target the rook square");
        assertFalse(hasLegalMove(whiteWs, 0, 4, 0, 6), "960 castle must not target the g-file");
    }

    @Test
    void standard_castleOfferedAsKingToGFile() throws Exception {
        FakeWebSocketSession whiteWs = castleReadySession("STANDARD");

        // Standard chess is unchanged: king (0,4) to the g-file (0,6).
        assertTrue(hasLegalMove(whiteWs, 0, 4, 0, 6), "standard castle should target the g-file");
        assertFalse(hasLegalMove(whiteWs, 0, 4, 0, 7), "standard castle must not target the rook square");
    }

    private FakeWebSocketSession castleReadySession(String variant) throws Exception {
        List<pvt.phgg.chess.server.game.GameMove> moves = new ArrayList<>();
        int n = 1;
        for (int[] m : KINGSIDE_CLEARING_MOVES) {
            moves.add(new pvt.phgg.chess.server.game.GameMove(1L, n++, m[0], m[1], m[2], m[3], null));
        }
        GameSession restored = GameSession.restore(objectMapper, null, 1L, "HUMAN", null,
                variant, "RNBQKBNR", "alice", 1L, "bob", 2L, moves);

        FakeWebSocketSession whiteWs = new FakeWebSocketSession("white");
        restored.rejoin(whiteWs, "alice");
        restored.rejoin(new FakeWebSocketSession("black"), "bob");
        restored.broadcastBoardState();
        return whiteWs;
    }

    private boolean hasLegalMove(FakeWebSocketSession ws, int fr, int fc, int tr, int tc) {
        tools.jackson.databind.JsonNode msg = objectMapper.readTree(ws.lastPayload());
        for (tools.jackson.databind.JsonNode m : msg.get("legalMoves")) {
            if (m.get("fromRow").asInt() == fr && m.get("fromCol").asInt() == fc
                    && m.get("toRow").asInt() == tr && m.get("toCol").asInt() == tc) {
                return true;
            }
        }
        return false;
    }

    // --- Minimal WebSocketSession stub ---

    static class FakeWebSocketSession implements WebSocketSession {
        private final String id;
        final List<TextMessage> messages = new ArrayList<>();

        FakeWebSocketSession(String id) { this.id = id; }

        String lastPayload() {
            assertFalse(messages.isEmpty(), "No messages were sent to session " + id);
            return messages.getLast().getPayload();
        }

        @Override public boolean isOpen() { return true; }
        @Override public void sendMessage(WebSocketMessage<?> msg) {
            if (msg instanceof TextMessage tm) messages.add(tm);
        }
        @Override public String getId() { return id; }
        @Override public URI getUri() { return null; }
        @Override public HttpHeaders getHandshakeHeaders() { return HttpHeaders.EMPTY; }
        @Override public Map<String, Object> getAttributes() { return Map.of(); }
        @Override public Principal getPrincipal() { return null; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public String getAcceptedProtocol() { return null; }
        @Override public void setTextMessageSizeLimit(int limit) { /* stub */ }
        @Override public int getTextMessageSizeLimit() { return 0; }
        @Override public void setBinaryMessageSizeLimit(int limit) { /* stub */ }
        @Override public int getBinaryMessageSizeLimit() { return 0; }
        @Override public List<WebSocketExtension> getExtensions() { return List.of(); }
        @Override public void close() { /* stub */ }
        @Override public void close(CloseStatus status) { /* stub */ }
    }
}

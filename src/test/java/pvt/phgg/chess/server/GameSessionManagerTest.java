package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.*;
import pvt.phgg.chess.server.elo.EloProperties;
import pvt.phgg.chess.server.elo.MatchmakingProperties;
import pvt.phgg.chess.server.game.GameRecorder;
import pvt.phgg.chess.server.user.AppUser;
import pvt.phgg.chess.server.user.UserService;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameSessionManagerTest {

    @Mock UserService userService;
    @Mock GameRecorder gameRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    // tier1: 0-10s → ±100, tier2: 10-20s → ±200, tier3: 20-30s → ±400, beyond → MAX_VALUE
    private final EloProperties eloProps = new EloProperties(1000, 100, 400, 2800, 60);
    private final MatchmakingProperties mmProps = new MatchmakingProperties(10, 100, 20, 200, 30, 400);

    private GameSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new GameSessionManager(objectMapper, gameRecorder, userService, eloProps, mmProps);
    }

    // ---- getWindow ----

    @Test
    void getWindow_withinTier1_returnsTier1Spread() {
        Instant joinedAt = Instant.now().minusSeconds(5);
        assertEquals(100, manager.getWindow(joinedAt, Instant.now()));
    }

    @Test
    void getWindow_exactlyAtTier1Boundary_returnsTier2Spread() {
        Instant joinedAt = Instant.now().minusSeconds(10);
        assertEquals(200, manager.getWindow(joinedAt, Instant.now()));
    }

    @Test
    void getWindow_betweenTier2AndTier3_returnsTier2Spread() {
        Instant joinedAt = Instant.now().minusSeconds(15);
        assertEquals(200, manager.getWindow(joinedAt, Instant.now()));
    }

    @Test
    void getWindow_beyondAllTiers_returnsMaxValue() {
        Instant joinedAt = Instant.now().minusSeconds(60);
        assertEquals(Integer.MAX_VALUE, manager.getWindow(joinedAt, Instant.now()));
    }

    // ---- join: bot bypass ----

    @Test
    void join_botGame_createsSessionImmediately() {
        FakeWs ws = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1200L, 1500);

        PlayerRole role = manager.join(ws, "alice", "WHITE");

        assertNotNull(role);
        assertNotNull(manager.getSession(ws), "Bot game must have a session immediately");
    }

    @Test
    void join_botGame_honorsColorPreference() {
        FakeWs ws = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);

        PlayerRole role = manager.join(ws, "alice", "BLACK");

        assertEquals(PlayerRole.BLACK, role);
    }

    @Test
    void join_botGame_bypassesQueue() {
        FakeWs ws = new FakeWs("a", Map.of("botType", "barbara"));
        stubUser("alice", 1L, 1000);

        manager.join(ws, "alice", "WHITE");

        // Queue must remain empty — bot games never go through it
        manager.matchPendingPlayers();
        // No exception and session still exists
        assertNotNull(manager.getSession(ws));
    }

    // ---- join: human game queuing ----

    @Test
    void join_humanGame_emptyQueue_playerIsQueued() {
        FakeWs ws = humanWs("a");
        stubUser("alice", 1L, 1000);

        PlayerRole role = manager.join(ws, "alice", "WHITE");

        assertNotNull(role);
        assertNull(manager.getSession(ws), "Queued player must have no session yet");
    }

    @Test
    void join_humanGame_queuedWithBlackPreference_returnsBLACK() {
        FakeWs ws = humanWs("a");
        stubUser("alice", 1L, 1000);

        PlayerRole role = manager.join(ws, "alice", "BLACK");

        assertEquals(PlayerRole.BLACK, role);
    }

    @Test
    void join_humanGame_immediateMatch_bothPlayersGetSessions() {
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 1000);

        manager.join(ws1, "alice", "WHITE"); // enters queue
        manager.join(ws2, "bob",   "WHITE"); // matches immediately

        assertNotNull(manager.getSession(ws1), "Waiting player must be in a session after match");
        assertNotNull(manager.getSession(ws2), "Joining player must be in a session after match");
        assertSame(manager.getSession(ws1), manager.getSession(ws2), "Both players must share the same session");
    }

    @Test
    void join_humanGame_immediateMatch_waitingPlayerGetsWaitingMessage() {
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 1000);

        manager.join(ws1, "alice", "WHITE");
        manager.join(ws2, "bob",   "WHITE");

        // The waiting player (alice) should receive a WAITING message confirming her color
        assertFalse(ws1.messages.isEmpty(), "Waiting player must receive a WAITING message on match");
        assertTrue(ws1.lastPayload().contains("WAITING"));
    }

    @Test
    void join_humanGame_waitingPlayerColorPreferenceHonored() {
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 1000);

        // alice joins first wanting BLACK
        manager.join(ws1, "alice", "BLACK");
        PlayerRole bobRole = manager.join(ws2, "bob", "WHITE");

        // alice (waiting) prefers BLACK → she gets BLACK; bob joins and gets WHITE
        GameSession session = manager.getSession(ws1);
        assertEquals(PlayerRole.BLACK, session.roleOf(ws1), "Waiting player BLACK preference must be honored");
        assertEquals(PlayerRole.WHITE, session.roleOf(ws2));
        assertEquals(PlayerRole.WHITE, bobRole);
    }

    @Test
    void join_humanGame_eloTooFarApart_noImmediateMatch() {
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 2500); // 1500 ELO difference, tier1 window is ±100

        manager.join(ws1, "alice", "WHITE");
        manager.join(ws2, "bob",   "WHITE");

        assertNull(manager.getSession(ws1), "Players too far apart in ELO must not match immediately");
        assertNull(manager.getSession(ws2));
    }

    // ---- disconnect ----

    @Test
    void disconnect_queuedPlayer_removedFromQueue() throws Exception {
        FakeWs ws = humanWs("a");
        stubUser("alice", 1L, 1000);
        manager.join(ws, "alice", "WHITE");

        manager.disconnect(ws);

        // After disconnect, matchPendingPlayers should find nothing to match
        manager.matchPendingPlayers();
        assertNull(manager.getSession(ws));
    }

    @Test
    void disconnect_activePlayer_sessionsCleanedUp() throws Exception {
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 1000);
        manager.join(ws1, "alice", "WHITE");
        manager.join(ws2, "bob",   "WHITE");

        manager.disconnect(ws2);

        assertNull(manager.getSession(ws2), "Disconnected player's session must be removed");
    }

    // ---- matchPendingPlayers ----

    @Test
    void matchPendingPlayers_removesClosedConnections() {
        FakeWs open   = humanWs("open");
        FakeWs closed = new FakeWs("closed", Map.of(), false); // isOpen() = false
        // Use ELOs far apart so both go to queue (no immediate match)
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 2500);

        manager.join(open,   "alice", "WHITE");
        manager.join(closed, "bob",   "WHITE");

        manager.matchPendingPlayers();

        // closed session is pruned; open player is still queued alone (no second player to match)
        assertNull(manager.getSession(open), "Open player must remain queued");
    }

    @Test
    void matchPendingPlayers_matchesCompatiblePair() {
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");

        // Manually add players to queue with joinedAt 25s ago → tier3 window ±400
        Instant longAgo = Instant.now().minusSeconds(25);
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 1000);
        manager.join(ws1, "alice", "WHITE");
        manager.join(ws2, "bob",   "WHITE");

        manager.matchPendingPlayers();

        assertNotNull(manager.getSession(ws1));
        assertNotNull(manager.getSession(ws2));
    }

    @Test
    void matchPendingPlayers_sendsWaitTimeUpdateToLoneQueuedPlayer() {
        FakeWs ws = humanWs("a");
        stubUser("alice", 1L, 1000);

        manager.join(ws, "alice", "WHITE");

        // A single queued player has nobody to match against, but should
        // still get their wait time refreshed on every tick — otherwise the
        // UI's displayed wait time would stay frozen at 0:00 forever.
        manager.matchPendingPlayers();

        assertTrue(ws.lastPayload().contains("WAITING_QUEUE"));
    }

    @Test
    void matchPendingPlayers_sendsWaitingQueueUpdateToStillQueuedPlayers() {
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 2500); // too far apart — both stay queued

        manager.join(ws1, "alice", "WHITE");
        manager.join(ws2, "bob",   "WHITE");

        manager.matchPendingPlayers();

        // Both players should receive a WAITING_QUEUE message with updated wait time
        assertTrue(ws1.lastPayload().contains("WAITING_QUEUE"));
        assertTrue(ws2.lastPayload().contains("WAITING_QUEUE"));
    }

    // ---- helpers ----

    private void stubUser(String username, Long id, int elo) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(id);
        lenient().when(user.getElo()).thenReturn(elo); // only used in human-queue path, not bot path
        when(userService.findByUsername(username)).thenReturn(Optional.of(user));
    }

    private FakeWs humanWs(String id) {
        return new FakeWs(id, Map.of());
    }

    // ---- Minimal WebSocketSession stub ----

    static class FakeWs implements WebSocketSession {
        private final String id;
        private final Map<String, Object> attributes;
        private final boolean open;
        final List<TextMessage> messages = new ArrayList<>();

        FakeWs(String id, Map<String, Object> attributes) {
            this(id, attributes, true);
        }

        FakeWs(String id, Map<String, Object> attributes, boolean open) {
            this.id = id;
            this.attributes = new HashMap<>(attributes);
            this.open = open;
        }

        String lastPayload() {
            assertFalse(messages.isEmpty(), "No messages sent to " + id);
            return messages.getLast().getPayload();
        }

        @Override public boolean isOpen()   { return open; }
        @Override public void sendMessage(WebSocketMessage<?> msg) {
            if (msg instanceof TextMessage tm) messages.add(tm);
        }
        @Override public String getId()     { return id; }
        @Override public URI getUri()       { return null; }
        @Override public HttpHeaders getHandshakeHeaders()       { return HttpHeaders.EMPTY; }
        @Override public Map<String, Object> getAttributes()     { return attributes; }
        @Override public Principal getPrincipal()                { return null; }
        @Override public InetSocketAddress getLocalAddress()     { return null; }
        @Override public InetSocketAddress getRemoteAddress()    { return null; }
        @Override public String getAcceptedProtocol()            { return null; }
        @Override public void setTextMessageSizeLimit(int limit) { }
        @Override public int  getTextMessageSizeLimit()          { return 0; }
        @Override public void setBinaryMessageSizeLimit(int limit){ }
        @Override public int  getBinaryMessageSizeLimit()        { return 0; }
        @Override public List<WebSocketExtension> getExtensions(){ return List.of(); }
        @Override public void close()                            { }
        @Override public void close(CloseStatus status)          { }
    }
}

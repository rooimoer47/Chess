package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.*;
import pvt.phgg.chess.server.dto.ActiveGameSummary;
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
    void join_humanGame_ignoresStaleClosedQueueEntry() {
        // Simulates a player who disconnected before their close event was
        // processed and their queue entry pruned — a real join must not get
        // paired with this dead connection instead of a genuine opponent.
        FakeWs ghost = new FakeWs("ghost", Map.of(), false); // isOpen() = false
        FakeWs real = humanWs("real");
        stubUser("ghost", 1L, 1000);
        stubUser("alice", 2L, 1000);

        manager.join(ghost, "ghost", "WHITE");
        PlayerRole role = manager.join(real, "alice", "WHITE");

        assertNull(manager.getSession(real), "Must not be matched against a stale/closed queue entry");
        assertNotNull(role);
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

    // ---- gameId indexing (Phase 1 of docs/RECONNECT_PLAN.md) ----

    @Test
    void startGame_indexesSessionByGameId() {
        FakeWs ws = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(42L);
        manager.join(ws, "alice", "WHITE");
        GameSession session = manager.getSession(ws);

        manager.startGame(session);

        assertEquals(42L, session.getGameId());
    }

    @Test
    void rejoinByGameId_botGame_survivesSocketClose() throws Exception {
        // This is the root-cause regression test: a bot game has only one
        // WebSocketSession pointing at it. Closing it must not make the
        // session unreachable — rejoin-by-gameId must still find it.
        FakeWs ws = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(42L);
        manager.join(ws, "alice", "WHITE");
        GameSession session = manager.getSession(ws);
        manager.startGame(session);

        manager.disconnect(ws);
        assertNull(manager.getSession(ws), "Old socket must no longer be mapped");

        FakeWs newWs = new FakeWs("a2", Map.of());
        PlayerRole role = manager.rejoin(newWs, "alice", 42L);

        assertEquals(PlayerRole.WHITE, role, "Bot game must be reachable again via its gameId");
        assertSame(session, manager.getSession(newWs));
    }

    @Test
    void rejoinByGameId_disambiguatesConcurrentSessionsForSameUser() throws Exception {
        // Reproduces the ambiguity bug the username-only rejoin has once a
        // user can hold more than one live session at a time.
        FakeWs ws1 = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(1L, 2L);
        manager.join(ws1, "alice", "WHITE");
        manager.startGame(manager.getSession(ws1));
        manager.disconnect(ws1);

        FakeWs ws2 = new FakeWs("b", Map.of("botType", "barbara"));
        manager.join(ws2, "alice", "WHITE");
        manager.startGame(manager.getSession(ws2));
        manager.disconnect(ws2);

        FakeWs rejoinWs = new FakeWs("c", Map.of());
        manager.rejoin(rejoinWs, "alice", 1L);

        assertEquals(1L, manager.getSession(rejoinWs).getGameId());
    }

    @Test
    void rejoinByGameId_unknownGameId_returnsNull() {
        FakeWs ws = new FakeWs("a", Map.of());
        assertNull(manager.rejoin(ws, "alice", 999L));
    }

    @Test
    void rejoinByGameId_nullGameId_fallsBackToUsernameScan() throws Exception {
        // PvP only: the opponent's still-open socket keeps the session in
        // activeSessions, so the old username scan can find it. (A bot
        // game's sole socket is gone the moment it disconnects — see
        // rejoinByGameId_botGame_survivesSocketClose for why that case
        // requires the gameId, not the fallback.)
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 1000);
        manager.join(ws1, "alice", "WHITE");
        manager.join(ws2, "bob",   "WHITE");
        manager.disconnect(ws1);

        FakeWs newWs = new FakeWs("a2", Map.of());
        PlayerRole role = manager.rejoin(newWs, "alice", null);

        assertEquals(PlayerRole.WHITE, role);
    }

    @Test
    void pruneIfOver_removesFinishedGameFromIndex() {
        FakeWs ws = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(42L);
        manager.join(ws, "alice", "WHITE");
        GameSession session = manager.getSession(ws);
        manager.startGame(session);
        session.resign(true);

        manager.pruneIfOver(session);

        assertNull(manager.rejoin(new FakeWs("a2", Map.of()), "alice", 42L),
                "A resigned game must no longer be reachable by gameId");
    }

    @Test
    void pruneIfOver_leavesInProgressGameIndexed() throws Exception {
        FakeWs ws = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(42L);
        manager.join(ws, "alice", "WHITE");
        GameSession session = manager.getSession(ws);
        manager.startGame(session);
        manager.disconnect(ws);

        manager.pruneIfOver(session);

        assertNotNull(manager.rejoin(new FakeWs("a2", Map.of()), "alice", 42L));
    }

    // ---- PvP disconnect timeout (Phase 2 of docs/RECONNECT_PLAN.md) ----

    @Test
    void expireDisconnectedPvpGames_endsGameAfterGracePeriod() throws Exception {
        GameSessionManager zeroGraceManager = managerWithGraceSeconds(0);
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(99L);
        zeroGraceManager.join(ws1, "alice", "WHITE");
        zeroGraceManager.join(ws2, "bob",   "WHITE");
        GameSession session = zeroGraceManager.getSession(ws1);
        zeroGraceManager.startGame(session);

        zeroGraceManager.disconnect(ws1); // alice (white) disconnects
        zeroGraceManager.expireDisconnectedPvpGames();

        verify(gameRecorder).endGame(99L, "TIMEOUT", "BLACK");
        assertTrue(session.isGameOver());
        assertNull(zeroGraceManager.rejoin(new FakeWs("a2", Map.of()), "alice", 99L),
                "Timed-out game must no longer be reachable");
    }

    @Test
    void expireDisconnectedPvpGames_withinGracePeriod_doesNothing() throws Exception {
        FakeWs ws1 = humanWs("a"); // default manager grace is 60s
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(99L);
        manager.join(ws1, "alice", "WHITE");
        manager.join(ws2, "bob",   "WHITE");
        GameSession session = manager.getSession(ws1);
        manager.startGame(session);

        manager.disconnect(ws1);
        manager.expireDisconnectedPvpGames();

        assertFalse(session.isGameOver());
        verify(gameRecorder, never()).endGame(anyLong(), any(), any());
    }

    @Test
    void expireDisconnectedPvpGames_botGame_neverExpires() throws Exception {
        GameSessionManager zeroGraceManager = managerWithGraceSeconds(0);
        FakeWs ws = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(7L);
        zeroGraceManager.join(ws, "alice", "WHITE");
        zeroGraceManager.joinBot(ws, "alan");
        GameSession session = zeroGraceManager.getSession(ws);
        zeroGraceManager.startGame(session);

        zeroGraceManager.disconnect(ws);
        zeroGraceManager.expireDisconnectedPvpGames();

        assertFalse(session.isGameOver(), "Bot games must never auto-lose from disconnect");
        assertNotNull(zeroGraceManager.rejoin(new FakeWs("a2", Map.of()), "alice", 7L),
                "Bot game must still be resumable");
    }

    @Test
    void expireDisconnectedPvpGames_reconnectBeforeGraceCancelsTimeout() throws Exception {
        GameSessionManager zeroGraceManager = managerWithGraceSeconds(0);
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(99L);
        zeroGraceManager.join(ws1, "alice", "WHITE");
        zeroGraceManager.join(ws2, "bob",   "WHITE");
        GameSession session = zeroGraceManager.getSession(ws1);
        zeroGraceManager.startGame(session);
        zeroGraceManager.disconnect(ws1);

        zeroGraceManager.rejoin(new FakeWs("a2", Map.of()), "alice", 99L);
        zeroGraceManager.expireDisconnectedPvpGames();

        assertFalse(session.isGameOver(), "Reconnecting must cancel the pending timeout");
    }

    private GameSessionManager managerWithGraceSeconds(int seconds) {
        EloProperties props = new EloProperties(1000, 100, 400, 2800, seconds);
        return new GameSessionManager(objectMapper, gameRecorder, userService, props, mmProps);
    }

    // ---- activeGamesFor (Phase 3 of docs/RECONNECT_PLAN.md) ----

    @Test
    void activeGamesFor_returnsSummaryForBotGame() {
        FakeWs ws = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(42L);
        manager.join(ws, "alice", "WHITE");
        manager.joinBot(ws, "alan");
        manager.startGame(manager.getSession(ws));

        List<ActiveGameSummary> games = manager.activeGamesFor("alice");

        assertEquals(1, games.size());
        ActiveGameSummary summary = games.get(0);
        assertEquals(42L, summary.gameId());
        assertEquals("BOT", summary.mode());
        assertEquals("alan", summary.botType());
        assertNull(summary.opponentUsername());
        assertEquals("WHITE", summary.color());
    }

    @Test
    void activeGamesFor_returnsSummaryForBothPvpPlayers() {
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(7L);
        manager.join(ws1, "alice", "WHITE");
        manager.join(ws2, "bob",   "WHITE");
        manager.startGame(manager.getSession(ws1));

        ActiveGameSummary aliceView = manager.activeGamesFor("alice").get(0);
        ActiveGameSummary bobView = manager.activeGamesFor("bob").get(0);

        assertEquals("bob", aliceView.opponentUsername());
        assertEquals("HUMAN", aliceView.mode());
        assertNull(aliceView.botType());
        assertEquals("alice", bobView.opponentUsername());
    }

    @Test
    void activeGamesFor_excludesOtherUsersGames() {
        FakeWs ws = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(42L);
        manager.join(ws, "alice", "WHITE");
        manager.joinBot(ws, "alan");
        manager.startGame(manager.getSession(ws));

        assertTrue(manager.activeGamesFor("bob").isEmpty());
    }

    @Test
    void activeGamesFor_emptyWhenNoActiveGames() {
        assertTrue(manager.activeGamesFor("alice").isEmpty());
    }

    // ---- restoreSession (Phase 5 of docs/RECONNECT_PLAN.md) ----

    @Test
    void restoreSession_indexesPvpGameByGameId() {
        GameSession restored = GameSession.restore(objectMapper, gameRecorder, 55L, "HUMAN", null,
                "alice", 1L, "bob", 2L, List.of());

        manager.restoreSession(restored);

        assertNotNull(manager.rejoin(humanWs("a"), "alice", 55L));
    }

    @Test
    void restoreSession_indexesBotGameByBotSlot() {
        GameSession restored = GameSession.restore(objectMapper, gameRecorder, 55L, "BOT", "alan",
                "alice", 1L, "BOT", null, List.of());

        manager.restoreSession(restored);

        assertEquals(1, manager.activeGamesFor("alice").size());
        // Rejoining the same bot without a gameId (the normal "Resume" path)
        // must reattach to the restored session, not spawn a duplicate.
        PlayerRole role = manager.join(new FakeWs("a", Map.of("botType", "alan")), "alice", "WHITE");
        assertEquals(PlayerRole.WHITE, role);
        assertEquals(1, manager.activeGamesFor("alice").size());
    }

    // ---- concurrent bot games — resume/new (Phase 4 of docs/RECONNECT_PLAN.md) ----

    @Test
    void join_botGame_secondConnectReattachesToExistingSlot() throws Exception {
        // Simulates the lobby's "Resume" click: connecting to the same bot
        // again after the original socket closed must reattach, not spawn
        // a second concurrent game for the same (user, bot) slot.
        FakeWs ws1 = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(42L);
        manager.join(ws1, "alice", "WHITE");
        manager.joinBot(ws1, "alan");
        manager.startGame(manager.getSession(ws1));
        manager.disconnect(ws1);

        FakeWs ws2 = new FakeWs("a2", Map.of("botType", "alan"));
        PlayerRole role = manager.join(ws2, "alice", "WHITE");

        assertEquals(PlayerRole.WHITE, role);
        assertEquals(42L, manager.getSession(ws2).getGameId());
        assertEquals(1, manager.activeGamesFor("alice").size(), "Must not have created a second session");
    }

    @Test
    void join_botGame_occupiedSlot_rejectsSecondConnection() {
        // Two tabs open on the same bot at once: the first is still live,
        // so the second must be rejected rather than silently duplicating
        // the session.
        FakeWs ws1 = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(42L);
        manager.join(ws1, "alice", "WHITE");
        manager.joinBot(ws1, "alan");
        manager.startGame(manager.getSession(ws1));

        FakeWs ws2 = new FakeWs("a2", Map.of("botType", "alan"));
        PlayerRole role = manager.join(ws2, "alice", "WHITE");

        assertNull(role, "A second live socket for the same bot slot must be rejected");
        assertEquals(1, manager.activeGamesFor("alice").size());
    }

    @Test
    void join_botGame_afterPreviousGameEnded_startsFreshSession() {
        FakeWs ws1 = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(1L, 2L);
        manager.join(ws1, "alice", "WHITE");
        manager.joinBot(ws1, "alan");
        GameSession first = manager.getSession(ws1);
        manager.startGame(first);
        first.resign(true);
        manager.pruneIfOver(first);

        FakeWs ws2 = new FakeWs("a2", Map.of("botType", "alan"));
        manager.join(ws2, "alice", "WHITE");
        manager.joinBot(ws2, "alan");
        manager.startGame(manager.getSession(ws2));

        assertEquals(2L, manager.getSession(ws2).getGameId());
    }

    @Test
    void join_botGame_differentBotTypes_areIndependentSlots() {
        FakeWs alanWs = new FakeWs("a", Map.of("botType", "alan"));
        FakeWs barbaraWs = new FakeWs("b", Map.of("botType", "barbara"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(1L, 2L);
        manager.join(alanWs, "alice", "WHITE");
        manager.joinBot(alanWs, "alan");
        manager.startGame(manager.getSession(alanWs));

        manager.join(barbaraWs, "alice", "WHITE");
        manager.joinBot(barbaraWs, "barbara");
        manager.startGame(manager.getSession(barbaraWs));

        assertEquals(2, manager.activeGamesFor("alice").size());
    }

    @Test
    void abandonBotGame_endsGameAndFreesTheSlot() {
        FakeWs ws1 = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(1L, 2L);
        manager.join(ws1, "alice", "WHITE");
        manager.joinBot(ws1, "alan");
        manager.startGame(manager.getSession(ws1));

        boolean abandoned = manager.abandonBotGame("alice", 1L);

        assertTrue(abandoned);
        verify(gameRecorder).endGame(1L, "ABANDONED", null);
        assertTrue(manager.activeGamesFor("alice").isEmpty());

        FakeWs ws2 = new FakeWs("a2", Map.of("botType", "alan"));
        manager.join(ws2, "alice", "WHITE");
        manager.joinBot(ws2, "alan");
        manager.startGame(manager.getSession(ws2));
        assertEquals(2L, manager.getSession(ws2).getGameId(), "Abandoning must free the slot for a fresh game");
    }

    @Test
    void abandonBotGame_notAPlayerInThatGame_returnsFalse() {
        FakeWs ws = new FakeWs("a", Map.of("botType", "alan"));
        stubUser("alice", 1L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(1L);
        manager.join(ws, "alice", "WHITE");
        manager.joinBot(ws, "alan");
        manager.startGame(manager.getSession(ws));

        assertFalse(manager.abandonBotGame("bob", 1L));
    }

    @Test
    void abandonBotGame_pvpGame_returnsFalse() {
        FakeWs ws1 = humanWs("a");
        FakeWs ws2 = humanWs("b");
        stubUser("alice", 1L, 1000);
        stubUser("bob",   2L, 1000);
        when(gameRecorder.startGame(any(), any(), any(), any(), any(), any())).thenReturn(1L);
        manager.join(ws1, "alice", "WHITE");
        manager.join(ws2, "bob",   "WHITE");
        manager.startGame(manager.getSession(ws1));

        assertFalse(manager.abandonBotGame("alice", 1L), "Abandon must not apply to PvP games");
    }

    @Test
    void abandonBotGame_unknownGameId_returnsFalse() {
        assertFalse(manager.abandonBotGame("alice", 999L));
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

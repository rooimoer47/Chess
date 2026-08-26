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

class GameSessionExtendedTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FakeWs whiteWs;
    private FakeWs blackWs;

    @BeforeEach
    void setUp() {
        whiteWs = new FakeWs("white");
        blackWs = new FakeWs("black");
    }

    private GameSession newSession() {
        GameSession s = new GameSession(objectMapper);
        s.join(whiteWs, "alice");
        s.join(blackWs, "bob");
        return s;
    }

    // ---- Color preference in join ----

    @Test
    void join_noPreference_firstPlayerGetsWhite() {
        GameSession s = new GameSession(objectMapper);
        PlayerRole role = s.join(whiteWs, "alice");
        assertEquals(PlayerRole.WHITE, role);
    }

    @Test
    void join_blackPreference_playerGetsBlack() {
        GameSession s = new GameSession(objectMapper);
        PlayerRole role = s.join(whiteWs, "alice", null, "BLACK");
        assertEquals(PlayerRole.BLACK, role);
    }

    @Test
    void join_whitePreferenceTaken_fallsBackToBlack() {
        GameSession s = new GameSession(objectMapper);
        s.join(whiteWs, "alice", null, "WHITE"); // takes white
        FakeWs ws2 = new FakeWs("ws2");
        PlayerRole role = s.join(ws2, "bob", null, "WHITE"); // white taken, falls back
        assertEquals(PlayerRole.BLACK, role);
    }

    @Test
    void join_duplicateUsername_returnsNull() {
        GameSession s = new GameSession(objectMapper);
        s.join(whiteWs, "alice");
        FakeWs duplicate = new FakeWs("dup");
        assertNull(s.join(duplicate, "alice"));
    }

    @Test
    void join_fullSession_returnsNull() {
        GameSession s = newSession();
        FakeWs third = new FakeWs("third");
        assertNull(s.join(third, "charlie"));
    }

    // ---- Bot join ----

    @Test
    void joinBot_alan_selectsMinimax2() {
        GameSession s = new GameSession(objectMapper);
        s.join(whiteWs, "alice");
        assertTrue(s.joinBot("alan"), "alan bot should join successfully");
        assertTrue(s.isFull());
    }

    @Test
    void joinBot_barbara_joinsBotAsBlack() {
        GameSession s = new GameSession(objectMapper);
        s.join(whiteWs, "alice", null, "WHITE");
        assertTrue(s.joinBot("barbara"));
        assertTrue(s.isFull());
    }

    @Test
    void joinBot_unknownType_stillJoinsAsRandomBot() {
        GameSession s = new GameSession(objectMapper);
        s.join(whiteWs, "alice", null, "WHITE");
        assertTrue(s.joinBot("unknown-type"), "Unknown bot type should fall through to random strategy");
    }

    @Test
    void joinBot_sessionFull_returnsFalse() {
        GameSession s = newSession(); // both human players already joined
        assertFalse(s.joinBot("alan"), "Cannot add bot when session is already full");
    }

    // ---- isBotTurn ----

    @Test
    void isBotTurn_humanGame_alwaysFalse() {
        GameSession s = newSession();
        assertFalse(s.isBotTurn());
    }

    @Test
    void isBotTurn_botGame_trueWhenBotColorToMove() {
        GameSession s = new GameSession(objectMapper);
        s.join(whiteWs, "alice", null, "WHITE"); // human is white
        s.joinBot("alan");                        // bot is black
        // Initially it's white's turn — not the bot
        assertFalse(s.isBotTurn());
    }

    @Test
    void isBotTurn_afterWhiteMove_trueForBlackBot() {
        GameSession s = new GameSession(objectMapper);
        s.join(whiteWs, "alice", null, "WHITE");
        s.joinBot("alan");
        s.applyMove(new Position(1, 4), new Position(3, 4)); // e2→e4
        assertTrue(s.isBotTurn(), "After white's move, black bot must have its turn");
    }

    // ---- Terminal state: resign ----

    @Test
    void resign_whiteResigns_gameOver() {
        GameSession s = newSession();
        s.resign(true); // white resigns
        assertTrue(s.isGameOver());
    }

    @Test
    void resign_blackResigns_gameOver() {
        GameSession s = newSession();
        s.resign(false);
        assertTrue(s.isGameOver());
    }

    @Test
    void resign_broadcastShowsResignedStatus() throws Exception {
        GameSession s = newSession();
        s.resign(true);
        s.broadcastBoardState();
        assertTrue(whiteWs.lastPayload().contains("RESIGNED"));
        assertTrue(blackWs.lastPayload().contains("RESIGNED"));
    }

    @Test
    void resign_legalMovesEmptyAfterResign() throws Exception {
        GameSession s = newSession();
        s.resign(true);
        s.broadcastBoardState();
        // Game over states broadcast empty legalMoves
        assertTrue(whiteWs.lastPayload().contains("\"legalMoves\":[]"));
    }

    // ---- Terminal state: isGameOver covers all statuses ----

    @Test
    void isGameOver_freshGame_isFalse() {
        GameSession s = newSession();
        assertFalse(s.isGameOver());
    }

    // ---- applyMove: captured pieces recorded ----

    @Test
    void applyMove_captureUpdatesCapturedList() throws Exception {
        // Scholar's mate setup that reaches a capture
        GameSession s = newSession();
        // 1.e4 e5  2.Qh5 Nc6  3.Bc4 — then Qxf7 captures
        applySequence(s,
            new int[]{1,4, 3,4},   // e2-e4
            new int[]{6,4, 4,4},   // e7-e5
            new int[]{0,3, 4,7},   // Qd1-h5
            new int[]{7,6, 5,5},   // Ng8-f6 (blocking h5-f7 threat but we continue)
            new int[]{0,5, 3,2},   // Bc1-c4 (bishop)
            new int[]{6,3, 4,3}    // d7-d5
        );
        // White queen captures on f7 (4,5)
        MoveResult result = s.applyMove(new Position(4, 7), new Position(6, 5));
        assertTrue(result.isValid(), "Queen should be able to capture on f7");

        s.broadcastBoardState();
        assertTrue(whiteWs.lastPayload().contains("capturedByWhite"),
            "Board update must include capturedByWhite");
    }

    // ---- createRematch: colors are swapped ----

    @Test
    void createRematch_swapsColors() {
        GameSession s = newSession();
        s.resign(true); // end game so rematch is allowed

        GameSession rematch = s.createRematch(objectMapper, null);

        // alice was WHITE → should be BLACK in rematch; bob was BLACK → should be WHITE
        assertEquals(PlayerRole.BLACK, rematch.roleOf(whiteWs),
            "Original white must be black in rematch");
        assertEquals(PlayerRole.WHITE, rematch.roleOf(blackWs),
            "Original black must be white in rematch");
    }

    // ---- helpers ----

    private static void applySequence(GameSession s, int[]... moves) {
        for (int[] m : moves) {
            MoveResult r = s.applyMove(new Position(m[0], m[1]), new Position(m[2], m[3]));
            assertTrue(r.isValid(), "Setup move invalid: " + java.util.Arrays.toString(m));
        }
    }

    // Minimal fake WebSocketSession
    static class FakeWs implements WebSocketSession {
        private final String id;
        final List<TextMessage> messages = new ArrayList<>();

        FakeWs(String id) { this.id = id; }

        String lastPayload() {
            assertFalse(messages.isEmpty(), "No messages sent to " + id);
            return messages.getLast().getPayload();
        }

        @Override public boolean isOpen()  { return true; }
        @Override public void sendMessage(WebSocketMessage<?> msg) {
            if (msg instanceof TextMessage tm) messages.add(tm);
        }
        @Override public String getId()    { return id; }
        @Override public URI getUri()      { return null; }
        @Override public HttpHeaders getHandshakeHeaders()    { return HttpHeaders.EMPTY; }
        @Override public Map<String, Object> getAttributes()  { return Map.of(); }
        @Override public Principal getPrincipal()             { return null; }
        @Override public InetSocketAddress getLocalAddress()  { return null; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public String getAcceptedProtocol()         { return null; }
        @Override public void setTextMessageSizeLimit(int l)  { }
        @Override public int  getTextMessageSizeLimit()       { return 0; }
        @Override public void setBinaryMessageSizeLimit(int l){ }
        @Override public int  getBinaryMessageSizeLimit()     { return 0; }
        @Override public List<WebSocketExtension> getExtensions() { return List.of(); }
        @Override public void close()                         { }
        @Override public void close(CloseStatus s)            { }
    }
}

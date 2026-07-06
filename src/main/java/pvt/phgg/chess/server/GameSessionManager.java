package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import pvt.phgg.chess.server.dto.ServerMessage;
import pvt.phgg.chess.server.elo.EloProperties;
import pvt.phgg.chess.server.elo.MatchmakingProperties;
import pvt.phgg.chess.server.game.GameRecorder;
import pvt.phgg.chess.server.user.AppUser;
import pvt.phgg.chess.server.user.UserService;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GameSessionManager {

    private static final Logger log = LoggerFactory.getLogger(GameSessionManager.class);

    private final ObjectMapper objectMapper;
    private final GameRecorder gameRecorder;
    private final UserService userService;
    private final EloProperties eloProperties;
    private final MatchmakingProperties matchmakingProperties;

    // Players waiting for a human opponent, in arrival order
    private final List<WaitingPlayer> humanQueue = new ArrayList<>();

    // Both players' WebSocket sessions map to the same GameSession
    private final Map<WebSocketSession, GameSession> activeSessions = new HashMap<>();

    public GameSessionManager(ObjectMapper objectMapper, GameRecorder gameRecorder,
                              UserService userService, EloProperties eloProperties,
                              MatchmakingProperties matchmakingProperties) {
        this.objectMapper = objectMapper;
        this.gameRecorder = gameRecorder;
        this.userService = userService;
        this.eloProperties = eloProperties;
        this.matchmakingProperties = matchmakingProperties;
    }

    public synchronized GameSession getSession(WebSocketSession ws) {
        return activeSessions.get(ws);
    }

    public synchronized PlayerRole rejoin(WebSocketSession ws, String username) {
        Set<GameSession> unique = new HashSet<>(activeSessions.values());
        for (GameSession session : unique) {
            if (!session.isGameOver()) {
                PlayerRole role = session.rejoin(ws, username);
                if (role != null) {
                    activeSessions.put(ws, session);
                    return role;
                }
            }
        }
        return null;
    }

    public synchronized PlayerRole join(WebSocketSession ws, String username, String colorPreference) {
        // Bot games bypass the queue — create a session immediately
        String botType = (String) ws.getAttributes().getOrDefault("botType", "none");
        if (!"none".equals(botType)) {
            AppUser user = userService.findByUsername(username).orElse(null);
            Long userId = user != null ? user.getId() : null;
            GameSession session = new GameSession(objectMapper, gameRecorder);
            PlayerRole role = session.join(ws, username, userId, colorPreference);
            if (role != null) activeSessions.put(ws, session);
            return role;
        }

        // Human game — look up player, try to match immediately
        AppUser user = userService.findByUsername(username).orElse(null);
        int elo = user != null ? user.getElo() : eloProperties.defaultElo();
        Long userId = user != null ? user.getId() : null;

        WaitingPlayer match = findMatch(elo);
        if (match != null) {
            humanQueue.remove(match);
            return createMatchedSession(ws, username, userId, colorPreference, match);
        }

        // No match found — add to queue
        humanQueue.add(new WaitingPlayer(ws, username, userId, elo, colorPreference, Instant.now()));
        return "BLACK".equals(colorPreference) ? PlayerRole.BLACK : PlayerRole.WHITE;
    }

    public synchronized boolean joinBot(WebSocketSession ws, String botType) {
        GameSession session = activeSessions.get(ws);
        if (session == null) return false;
        return session.joinBot(botType);
    }

    public synchronized void disconnect(WebSocketSession ws) throws IOException {
        // Remove from queue if still waiting — no session to clean up
        if (humanQueue.removeIf(p -> p.ws().equals(ws))) return;

        GameSession session = activeSessions.remove(ws);
        if (session == null) return;

        session.disconnect(ws);
        if (session.isEmpty()) {
            activeSessions.values().removeIf(s -> s == session);
        }
    }

    public synchronized GameSession.RematchOutcome requestRematch(WebSocketSession ws) {
        GameSession session = activeSessions.get(ws);
        if (session == null) return null;
        PlayerRole role = session.roleOf(ws);
        if (role == null) return null;

        GameSession.RematchOutcome outcome = session.requestRematch(role == PlayerRole.WHITE);
        if (outcome == GameSession.RematchOutcome.STARTED) {
            GameSession newSession = session.createRematch(objectMapper, gameRecorder);
            activeSessions.replaceAll((k, v) -> v == session ? newSession : v);
        }
        return outcome;
    }

    public synchronized void declineRematch(WebSocketSession ws) throws IOException {
        GameSession session = activeSessions.get(ws);
        if (session == null) return;
        PlayerRole role = session.roleOf(ws);
        if (role == null) return;
        boolean opponentIsWhite = role != PlayerRole.WHITE;
        if (session.hasRematchRequest(opponentIsWhite)) {
            session.sendRematchDeclinedTo(opponentIsWhite);
        }
    }

    // Called by the scheduled task in Step 10 to widen windows over time
    synchronized List<WaitingPlayer> getHumanQueue() {
        return humanQueue;
    }

    synchronized void registerSession(WebSocketSession ws, GameSession session) {
        activeSessions.put(ws, session);
    }

    // Exposed package-private for testing
    int getWindow(Instant joinedAt, Instant now) {
        long waitSeconds = Duration.between(joinedAt, now).getSeconds();
        if (waitSeconds < matchmakingProperties.tier1Seconds()) return matchmakingProperties.tier1Spread();
        if (waitSeconds < matchmakingProperties.tier2Seconds()) return matchmakingProperties.tier2Spread();
        if (waitSeconds < matchmakingProperties.tier3Seconds()) return matchmakingProperties.tier3Spread();
        return Integer.MAX_VALUE;
    }

    private WaitingPlayer findMatch(int elo) {
        Instant now = Instant.now();
        int joiningWindow = matchmakingProperties.tier1Spread();
        WaitingPlayer best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (WaitingPlayer p : humanQueue) {
            int diff = Math.abs(elo - p.elo());
            // Use the wider of the two windows: gives the waiting player benefit of their wait time
            int window = Math.max(joiningWindow, getWindow(p.joinedAt(), now));
            if (diff <= window && diff < bestDiff) {
                best = p;
                bestDiff = diff;
            }
        }
        return best;
    }

    private PlayerRole createMatchedSession(WebSocketSession ws, String username, Long userId,
                                            String colorPreference, WaitingPlayer waiting) {
        // Waiting player's preference takes priority — they arrived first
        PlayerRole waitingRole = "BLACK".equals(waiting.colorPreference()) ? PlayerRole.BLACK : PlayerRole.WHITE;
        PlayerRole joiningRole = waitingRole == PlayerRole.WHITE ? PlayerRole.BLACK : PlayerRole.WHITE;

        GameSession session = new GameSession(objectMapper, gameRecorder);
        if (waitingRole == PlayerRole.WHITE) {
            session.join(waiting.ws(), waiting.username(), waiting.userId(), "WHITE");
            session.join(ws, username, userId, "BLACK");
        } else {
            session.join(ws, username, userId, "WHITE");
            session.join(waiting.ws(), waiting.username(), waiting.userId(), "BLACK");
        }
        activeSessions.put(waiting.ws(), session);
        activeSessions.put(ws, session);

        // Notify the waiting player that a match was found and confirm their color
        sendMessage(waiting.ws(), ServerMessage.waiting(waitingRole.name()));
        return joiningRole;
    }

    private void sendMessage(WebSocketSession ws, ServerMessage message) {
        try {
            if (ws != null && ws.isOpen()) {
                ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
        } catch (Exception e) {
            log.error("Failed to send message to {}", ws != null ? ws.getId() : "null", e);
        }
    }
}

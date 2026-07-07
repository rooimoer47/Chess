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

import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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

    @Scheduled(fixedDelay = 5000)
    public synchronized void matchPendingPlayers() {
        humanQueue.removeIf(p -> !p.ws().isOpen());
        if (humanQueue.isEmpty()) return;

        if (humanQueue.size() >= 2) {
            Instant now = Instant.now();
            boolean[] claimed = new boolean[humanQueue.size()];
            List<WaitingPlayer[]> toMatch = new ArrayList<>();

            for (int i = 0; i < humanQueue.size(); i++) {
                if (claimed[i]) continue;
                WaitingPlayer a = humanQueue.get(i);
                int windowA = getWindow(a.joinedAt(), now);
                int bestDiff = Integer.MAX_VALUE;
                int bestJ = -1;

                for (int j = i + 1; j < humanQueue.size(); j++) {
                    if (claimed[j]) continue;
                    WaitingPlayer b = humanQueue.get(j);
                    int diff = Math.abs(a.elo() - b.elo());
                    int window = Math.max(windowA, getWindow(b.joinedAt(), now));
                    if (diff <= window && diff < bestDiff) {
                        bestDiff = diff;
                        bestJ = j;
                    }
                }

                if (bestJ >= 0) {
                    claimed[i] = true;
                    claimed[bestJ] = true;
                    toMatch.add(new WaitingPlayer[]{humanQueue.get(i), humanQueue.get(bestJ)});
                }
            }

            for (WaitingPlayer[] pair : toMatch) {
                humanQueue.remove(pair[0]);
                humanQueue.remove(pair[1]);
                startScheduledMatch(pair[0], pair[1]);
            }
        }

        // Update wait time for players still in queue — including a lone
        // player with nobody to match against yet, who otherwise would never
        // see their displayed wait time move past 0:00.
        Instant afterMatch = Instant.now();
        for (WaitingPlayer p : humanQueue) {
            int waitSecs = (int) Duration.between(p.joinedAt(), afterMatch).getSeconds();
            String color = "BLACK".equals(p.colorPreference()) ? "BLACK" : "WHITE";
            sendMessage(p.ws(), ServerMessage.waitingInQueue(color, waitSecs));
        }
    }

    private void startScheduledMatch(WaitingPlayer a, WaitingPlayer b) {
        log.info("Matched {} (ELO {}) vs {} (ELO {})", a.username(), a.elo(), b.username(), b.elo());

        PlayerRole roleA = resolveFirstRole(a.colorPreference(), b.colorPreference());
        PlayerRole roleB = roleA == PlayerRole.WHITE ? PlayerRole.BLACK : PlayerRole.WHITE;

        GameSession session = new GameSession(objectMapper, gameRecorder);
        if (roleA == PlayerRole.WHITE) {
            session.join(a.ws(), a.username(), a.userId(), "WHITE");
            session.join(b.ws(), b.username(), b.userId(), "BLACK");
        } else {
            session.join(b.ws(), b.username(), b.userId(), "WHITE");
            session.join(a.ws(), a.username(), a.userId(), "BLACK");
        }
        activeSessions.put(a.ws(), session);
        activeSessions.put(b.ws(), session);

        sendMessage(a.ws(), ServerMessage.waiting(roleA.name()));
        sendMessage(b.ws(), ServerMessage.waiting(roleB.name()));

        try {
            session.onGameStart();
            session.broadcastBoardState();
        } catch (Exception e) {
            log.error("Failed to start scheduled game for {} vs {}", a.username(), b.username(), e);
            activeSessions.remove(a.ws());
            activeSessions.remove(b.ws());
            sendMessage(a.ws(), ServerMessage.error("Failed to start game. Please reconnect."));
            sendMessage(b.ws(), ServerMessage.error("Failed to start game. Please reconnect."));
        }
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
        PlayerRole waitingRole = resolveFirstRole(waiting.colorPreference(), colorPreference);
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

    /**
     * Decides which of two paired players gets White. A fixed preference
     * (WHITE/BLACK) is always honored over RANDOM — the RANDOM side simply
     * takes whichever colour the other side didn't take. If both sides are
     * RANDOM, it's a coin toss. If both are fixed and conflict (both want
     * the same colour), {@code prefFirst} wins — same arrival-order tiebreak
     * as before this method existed.
     */
    private PlayerRole resolveFirstRole(String prefFirst, String prefSecond) {
        boolean firstRandom = !"WHITE".equals(prefFirst) && !"BLACK".equals(prefFirst);
        boolean secondRandom = !"WHITE".equals(prefSecond) && !"BLACK".equals(prefSecond);

        if (firstRandom && secondRandom) {
            return ThreadLocalRandom.current().nextBoolean() ? PlayerRole.WHITE : PlayerRole.BLACK;
        }
        if (firstRandom) {
            return "WHITE".equals(prefSecond) ? PlayerRole.BLACK : PlayerRole.WHITE;
        }
        return "BLACK".equals(prefFirst) ? PlayerRole.BLACK : PlayerRole.WHITE;
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

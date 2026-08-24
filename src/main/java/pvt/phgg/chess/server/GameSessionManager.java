package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import pvt.phgg.chess.server.dto.ActiveGameSummary;
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

    // Sent to a socket that has been displaced from its slot by a newer
    // connection for the same player. 4000+ is the application-defined range.
    private static final CloseStatus SUPERSEDED =
            new CloseStatus(4001, "Superseded by a newer connection");

    private final ObjectMapper objectMapper;
    private final GameRecorder gameRecorder;
    private final UserService userService;
    private final EloProperties eloProperties;
    private final MatchmakingProperties matchmakingProperties;

    // Players waiting for a human opponent, in arrival order
    private final List<WaitingPlayer> humanQueue = new ArrayList<>();

    // Both players' WebSocket sessions map to the same GameSession
    private final Map<WebSocketSession, GameSession> activeSessions = new HashMap<>();

    // Live sessions keyed by their durable gameId. A bot game has only one
    // WebSocketSession pointing at it (the human's) — the instant that
    // socket closes, activeSessions has nothing left referencing the
    // session at all. This index keeps it reachable regardless of which
    // (or how many) WebSocketSessions are currently attached to it.
    private final Map<Long, GameSession> sessionsByGameId = new HashMap<>();

    // One live bot game per (userId, botType) slot, keyed by "userId:botType".
    // Lets join() recognize "you already have a game with this bot" and
    // reattach instead of silently starting a second, orphaning the first.
    private final Map<String, Long> activeBotGameId = new HashMap<>();

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

    public synchronized List<ActiveGameSummary> activeGamesFor(String username) {
        List<ActiveGameSummary> result = new ArrayList<>();
        for (GameSession session : sessionsByGameId.values()) {
            ActiveGameSummary summary = session.summarizeFor(username);
            if (summary != null) result.add(summary);
        }
        return result;
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

    // Preferred over the username-only overload whenever a gameId is known
    // (e.g. a reconnect that names the game it belongs to) — an O(1) lookup
    // instead of a scan, and unambiguous once a user can hold more than one
    // live session (concurrent bot games). Falls back to the username scan
    // when no gameId is supplied, so existing PvP reconnects are unaffected.
    public synchronized PlayerRole rejoin(WebSocketSession ws, String username, Long gameId) {
        if (gameId == null) {
            return rejoin(ws, username);
        }
        GameSession session = sessionsByGameId.get(gameId);
        if (session == null || session.isGameOver()) {
            return null;
        }
        PlayerRole role = session.rejoin(ws, username);
        if (role != null) {
            activeSessions.put(ws, session);
        }
        return role;
    }

    // Binds ws to the player's existing slot, displacing whatever socket held
    // it, and retires that socket: dropped from activeSessions so its late
    // close can't be mistaken for the live one, and closed so a genuine second
    // tab learns it has been superseded rather than sitting on a silent board.
    private PlayerRole takeOverSlot(GameSession session, WebSocketSession ws, String username) {
        PlayerRole role = session.slotOf(username);
        if (role == null) return null;

        WebSocketSession displaced = session.takeOverSlot(ws, username);
        if (displaced != null) {
            activeSessions.remove(displaced);
            log.info("Slot takeover: {} reattached to game {} (displaced socket {})",
                    username, session.getGameId(), displaced.getId());
            try {
                displaced.close(SUPERSEDED);
            } catch (IOException e) {
                // Already gone is the common case and exactly what we wanted.
                log.debug("Displaced socket {} could not be closed", displaced.getId(), e);
            }
        }
        return role;
    }

    // The player's live human game, if they have one. Deliberately not any
    // live session: a player with an in-progress bot game who now asks for a
    // human opponent must get the human game they asked for, not be dragged
    // back into the bot game.
    private PlayerRole takeOverLiveHumanGame(WebSocketSession ws, String username) {
        for (GameSession session : new HashSet<>(activeSessions.values())) {
            if (session.isBotEnabled() || session.isGameOver()) continue;
            if (session.slotOf(username) == null) continue;

            PlayerRole role = takeOverSlot(session, ws, username);
            if (role != null) {
                activeSessions.put(ws, session);
                return role;
            }
        }
        return null;
    }

    public synchronized PlayerRole join(WebSocketSession ws, String username, String colorPreference) {
        String variant = (String) ws.getAttributes().getOrDefault("variant", "STANDARD");
        // Bot games bypass the queue — create a session immediately
        String botType = (String) ws.getAttributes().getOrDefault("botType", "none");
        if (!"none".equals(botType)) {
            AppUser user = userService.findByUsername(username).orElse(null);
            Long userId = user != null ? user.getId() : null;

            String botKey = botKey(userId, botType);
            Long existingGameId = activeBotGameId.get(botKey);
            if (existingGameId != null) {
                GameSession existing = sessionsByGameId.get(existingGameId);
                if (existing == null || existing.isGameOver()) {
                    activeBotGameId.remove(botKey);
                } else {
                    // A live game already exists for this bot — reattach to it
                    // rather than starting a duplicate. If another socket still
                    // holds the slot it is stale (a reconnect that outran its
                    // predecessor's close, or a second tab), so take it over
                    // rather than refusing: returning null here left the player
                    // staring at "Game is full or you are already connected"
                    // for a game that is theirs.
                    PlayerRole role = existing.rejoin(ws, username);
                    if (role == null) role = takeOverSlot(existing, ws, username);
                    if (role != null) {
                        activeSessions.put(ws, existing);
                    }
                    return role;
                }
            }

            GameSession session = new GameSession(objectMapper, gameRecorder, variant);
            PlayerRole role = session.join(ws, username, userId, colorPreference);
            if (role != null) activeSessions.put(ws, session);
            return role;
        }

        // Human game — prune stale/closed entries before searching (a
        // disconnect can land in the queue before its close event is fully
        // processed), then try to match immediately
        humanQueue.removeIf(p -> !p.ws().isOpen());

        // The player may already be in a human game: a reconnect can land
        // before the old socket's close is processed, so the vacant-slot
        // rejoin the handler tried first found nothing to fill. Take the slot
        // over instead of queueing them as a brand-new player — that stranded
        // them in the queue while their own game waited on a dead socket.
        PlayerRole resumed = takeOverLiveHumanGame(ws, username);
        if (resumed != null) return resumed;

        // A player must never be left with two entries in the queue (and so
        // never be matched against themselves) — drop any earlier one.
        humanQueue.removeIf(p -> p.username().equals(username));

        AppUser user = userService.findByUsername(username).orElse(null);
        int elo = eloForVariant(user, variant);
        Long userId = user != null ? user.getId() : null;

        WaitingPlayer match = findMatch(elo, variant);
        log.info("Queue join: {} variant={} elo={} queueSize={} matched={}",
                username, variant, elo, humanQueue.size(), match != null ? match.username() : "none");
        if (match != null) {
            humanQueue.remove(match);
            return createMatchedSession(ws, username, userId, colorPreference, variant, match);
        }

        // No match found — add to queue
        humanQueue.add(new WaitingPlayer(ws, username, userId, elo, colorPreference, variant, Instant.now()));
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

    // Starts a session's recorded game and indexes it by gameId. Must be
    // used instead of calling session.onGameStart() directly — otherwise
    // the session is never reachable via rejoin(ws, username, gameId).
    public synchronized void startGame(GameSession session) {
        session.onGameStart();
        index(session);
    }

    // Registers an already-started session (gameId already assigned) rebuilt
    // by GameRestorationService after a server restart — same indexing
    // startGame() does, without calling onGameStart() again.
    public synchronized void restoreSession(GameSession session) {
        index(session);
    }

    private void index(GameSession session) {
        Long gameId = session.getGameId();
        if (gameId == null) return;
        sessionsByGameId.put(gameId, session);
        if (session.isBotEnabled()) {
            Long humanId = session.getHumanPlayerId();
            if (humanId != null) {
                activeBotGameId.put(botKey(humanId, session.getBotType()), gameId);
            }
        }
    }

    // Called after any action that might have ended a game, so finished
    // games don't linger in the gameId (or bot-slot) index forever.
    public synchronized void pruneIfOver(GameSession session) {
        if (session == null || !session.isGameOver()) return;
        Long gameId = session.getGameId();
        if (gameId != null) {
            sessionsByGameId.remove(gameId);
            activeBotGameId.values().removeIf(id -> id.equals(gameId));
        }
    }

    // Lets a user walk away from a bot game they no longer want to finish,
    // freeing up that bot's slot for a fresh game. Only bot games — PvP
    // abandonment isn't a concept the other player should be subject to.
    public synchronized boolean abandonBotGame(String username, long gameId) {
        GameSession session = sessionsByGameId.get(gameId);
        if (session == null) return false;
        ActiveGameSummary summary = session.summarizeFor(username);
        if (summary == null || !"BOT".equals(summary.mode())) return false;
        session.abandon();
        pruneIfOver(session);
        return true;
    }

    private static String botKey(Long userId, String botType) {
        return userId + ":" + botType;
    }

    @Scheduled(fixedDelay = 5000)
    public synchronized void expireDisconnectedPvpGames() {
        for (GameSession session : new ArrayList<>(sessionsByGameId.values())) {
            if (!session.expireIfDisconnectedPastGrace(eloProperties.disconnectGraceSeconds())) continue;
            try {
                session.broadcastBoardState();
            } catch (IOException e) {
                log.error("Failed to notify players of PvP disconnect timeout for game {}", session.getGameId(), e);
            }
            pruneIfOver(session);
            activeSessions.values().removeIf(s -> s == session);
        }
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

    @Scheduled(fixedDelay = 1000)
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
                    if (!a.variant().equals(b.variant())) continue; // never cross-match variants
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
        log.info("Matched {} (ELO {}) vs {} (ELO {}) variant={}",
                a.username(), a.elo(), b.username(), b.elo(), a.variant());

        PlayerRole roleA = resolveFirstRole(a.colorPreference(), b.colorPreference());
        PlayerRole roleB = roleA == PlayerRole.WHITE ? PlayerRole.BLACK : PlayerRole.WHITE;

        // Both players are guaranteed to share a variant (matching filter above).
        GameSession session = new GameSession(objectMapper, gameRecorder, a.variant());
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
            startGame(session);
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

    // A player only ever matches an opponent queued for the same variant — this equality filter is
    // the entire "both or neither have Chess960" rule; it runs before the ELO-window comparison.
    private WaitingPlayer findMatch(int elo, String variant) {
        Instant now = Instant.now();
        int joiningWindow = matchmakingProperties.tier1Spread();
        WaitingPlayer best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (WaitingPlayer p : humanQueue) {
            if (!p.variant().equals(variant)) continue;
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

    private int eloForVariant(AppUser user, String variant) {
        if (user == null) return eloProperties.defaultElo();
        return "CHESS960".equals(variant) ? user.getElo960() : user.getElo();
    }

    private PlayerRole createMatchedSession(WebSocketSession ws, String username, Long userId,
                                            String colorPreference, String variant, WaitingPlayer waiting) {
        PlayerRole waitingRole = resolveFirstRole(waiting.colorPreference(), colorPreference);
        PlayerRole joiningRole = waitingRole == PlayerRole.WHITE ? PlayerRole.BLACK : PlayerRole.WHITE;

        GameSession session = new GameSession(objectMapper, gameRecorder, variant);
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

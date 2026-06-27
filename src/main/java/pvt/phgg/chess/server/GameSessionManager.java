package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import pvt.phgg.chess.server.game.GameRecorder;
import pvt.phgg.chess.server.user.AppUser;
import pvt.phgg.chess.server.user.UserService;

import java.io.IOException;

@Component
public class GameSessionManager {

    private final ObjectMapper objectMapper;
    private final GameRecorder gameRecorder;
    private final UserService userService;
    private GameSession activeSession;

    public GameSessionManager(ObjectMapper objectMapper, GameRecorder gameRecorder, UserService userService) {
        this.objectMapper = objectMapper;
        this.gameRecorder = gameRecorder;
        this.userService = userService;
        this.activeSession = new GameSession(objectMapper, gameRecorder);
    }

    public synchronized GameSession getSession() {
        return activeSession;
    }

    public synchronized PlayerRole rejoin(WebSocketSession ws, String username) {
        if (activeSession.isGameOver()) return null;
        return activeSession.rejoin(ws, username);
    }

    public synchronized boolean isClockCompatibleForJoin() {
        if (activeSession.isGameOver()) return true;
        return activeSession.isClockCompatible();
    }

    public synchronized PlayerRole join(WebSocketSession ws, String username, String colorPreference) {
        if (activeSession.isGameOver()) {
            activeSession = new GameSession(objectMapper, gameRecorder);
        }
        if (activeSession.isFull()) {
            return null;
        }
        Long userId = userService.findByUsername(username)
                .map(AppUser::getId)
                .orElse(null);
        return activeSession.join(ws, username, userId, colorPreference);
    }

    public synchronized boolean joinBot(String botType) {
        return activeSession.joinBot(botType);
    }

    public synchronized void onGameStart() {
        activeSession.onGameStart();
    }

    public synchronized void disconnect(WebSocketSession ws) throws IOException {
        activeSession.disconnect(ws);
        if (activeSession.isEmpty()) {
            activeSession = new GameSession(objectMapper, gameRecorder);
        }
    }
}

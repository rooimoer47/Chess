package pvt.phgg.chess.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class GameSessionManager {

    private final ObjectMapper objectMapper;
    private GameSession activeSession;

    public GameSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.activeSession = new GameSession(objectMapper);
    }

    public synchronized GameSession getSession() {
        return activeSession;
    }

    public synchronized PlayerRole join(WebSocketSession ws, String username) throws IOException {
        if (activeSession.isFull()) {
            return null;
        }
        return activeSession.join(ws, username);
    }

    public synchronized boolean joinBot() {
        return activeSession.joinBot();
    }

    public synchronized void disconnect(WebSocketSession ws) throws IOException {
        activeSession.disconnect(ws);
        if (activeSession.isEmpty()) {
            activeSession = new GameSession(objectMapper);
        }
    }
}

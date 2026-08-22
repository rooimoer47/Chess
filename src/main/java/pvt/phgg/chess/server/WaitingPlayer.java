package pvt.phgg.chess.server;

import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;

record WaitingPlayer(
        WebSocketSession ws,
        String username,
        Long userId,
        int elo,
        String colorPreference,
        String variant,
        Instant joinedAt
) {}

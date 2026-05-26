package pvt.phgg.chess.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pvt.phgg.chess.MoveResult;
import pvt.phgg.chess.Position;
import pvt.phgg.chess.PromotionChoice;
import pvt.phgg.chess.server.dto.ClientMessage;
import pvt.phgg.chess.server.dto.ServerMessage;

import java.io.IOException;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final GameSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public GameWebSocketHandler(GameSessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
        String username = (String) ws.getAttributes().get("username");
        PlayerRole role = sessionManager.join(ws, username);
        if (role == null) {
            sendTo(ws, ServerMessage.error("Game is full or you are already connected."));
            ws.close();
            return;
        }

        LOGGER.info("Player connected as {}: {}", role, ws.getId());
        sendTo(ws, ServerMessage.waiting(role.name()));

        GameSession session = sessionManager.getSession();
        if (session.isFull()) {
            LOGGER.info("Both players connected — game starting");
            session.broadcastBoardState();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage raw) throws Exception {
        ClientMessage msg = objectMapper.readValue(raw.getPayload(), ClientMessage.class);
        GameSession session = sessionManager.getSession();
        PlayerRole role = session.roleOf(ws);

        if (role == null) {
            sendTo(ws, ServerMessage.error("Not a player in this game."));
            return;
        }

        switch (msg.type()) {
            case "MOVE"    -> handleMove(ws, session, role, msg);
            case "PROMOTE" -> handlePromotion(ws, session, role, msg);
            default        -> sendTo(ws, ServerMessage.error("Unknown message type: " + msg.type()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) throws Exception {
        LOGGER.info("Player disconnected: {} ({})", ws.getId(), status);
        sessionManager.disconnect(ws);
    }

    @Override
    public void handleTransportError(WebSocketSession ws, Throwable ex) throws Exception {
        LOGGER.error("Transport error for {}: {}", ws.getId(), ex.getMessage());
        sessionManager.disconnect(ws);
    }

    private void handleMove(WebSocketSession ws, GameSession session, PlayerRole role, ClientMessage msg) throws IOException {
        if (!session.isFull()) {
            session.sendError(ws, "Waiting for opponent.");
            return;
        }
        if (!session.isPlayerTurn(role)) {
            session.sendError(ws, "Not your turn.");
            return;
        }

        Position from = new Position(msg.fromRow(), msg.fromCol());
        Position to   = new Position(msg.toRow(),   msg.toCol());
        MoveResult result = session.applyMove(from, to);

        if (!result.isValid()) {
            session.sendError(ws, "Illegal move.");
            return;
        }

        if (result.getType() == MoveResult.Type.PROMOTION_NEEDED) {
            session.broadcastBoardState();
            session.sendPromotionNeeded(to);
            return;
        }

        session.broadcastBoardState();
    }

    private void handlePromotion(WebSocketSession ws, GameSession session, PlayerRole role, ClientMessage msg) throws IOException {
        PromotionChoice choice;
        try {
            choice = PromotionChoice.valueOf(msg.choice().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            session.sendError(ws, "Invalid promotion choice.");
            return;
        }

        // Promotion is applied for the player whose pawn just reached the end.
        // The engine tracks whose pawn is pending promotion via position.
        // We locate the promoting pawn: it's on row 0 or 7, belongs to the current session player.
        Position promotionPos = findPromotionPawn(session, role);
        if (promotionPos == null) {
            session.sendError(ws, "No pawn awaiting promotion.");
            return;
        }

        session.applyPromotion(promotionPos, choice);
        session.broadcastBoardState();
    }

    private Position findPromotionPawn(GameSession session, PlayerRole role) {
        // The pawn that just promoted is on row 7 (white) or row 0 (black)
        boolean isWhite = (role == PlayerRole.WHITE);
        int promotionRow = isWhite ? 7 : 0;
        for (int col = 0; col < 8; col++) {
            var piece = session.getPiece(promotionRow, col);
            if (piece.isPositionOccupied() && piece.isPawn() && piece.isWhite() == isWhite) {
                return new Position(promotionRow, col);
            }
        }
        return null;
    }

    private void sendTo(WebSocketSession ws, ServerMessage message) throws IOException {
        if (!ws.isOpen()) return;
        String json = objectMapper.writeValueAsString(message);
        synchronized (ws) {
            ws.sendMessage(new TextMessage(json));
        }
    }
}

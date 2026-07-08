package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
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
import pvt.phgg.chess.server.user.UserService;

import java.io.IOException;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final GameSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    public GameWebSocketHandler(GameSessionManager sessionManager, ObjectMapper objectMapper, UserService userService) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.userService = userService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
        String username = (String) ws.getAttributes().get("username");
        Long gameId = (Long) ws.getAttributes().get("gameId");

        PlayerRole role = sessionManager.rejoin(ws, username, gameId);
        boolean isRejoin = role != null;

        if (!isRejoin) {
            String colorPreference = (String) ws.getAttributes().getOrDefault("colorPreference", "RANDOM");
            role = sessionManager.join(ws, username, colorPreference);
        }

        if (role == null) {
            sendTo(ws, ServerMessage.error("Game is full or you are already connected."));
            ws.close();
            return;
        }

        if (!isRejoin) {
            String botType = (String) ws.getAttributes().get("botType");
            if (!"none".equals(botType)) {
                sessionManager.joinBot(ws, botType);
            }
        }

        GameSession session = sessionManager.getSession(ws);
        LOGGER.info("Player {} as {}: {}", isRejoin ? "reconnected" : "connected", role, ws.getId());

        if (session != null) {
            sendTo(ws, ServerMessage.waiting(role.name()));
        } else {
            sendTo(ws, ServerMessage.waitingInQueue(role.name(), 0));
        }

        if (session != null && session.isFull()) {
            LOGGER.info(isRejoin ? "Player rejoined — game resuming" : "Both players connected — game starting");
            if (!isRejoin) {
                sessionManager.startGame(session);
            }
            session.broadcastBoardState();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage raw) throws Exception {
        Long userId = (Long) ws.getAttributes().get("userId");
        if (userId != null) {
            userService.updateLastActive(userId);
        }

        ClientMessage msg = objectMapper.readValue(raw.getPayload(), ClientMessage.class);
        GameSession session = sessionManager.getSession(ws);
        if (session == null) {
            sendTo(ws, ServerMessage.error("Not in a game yet."));
            return;
        }
        PlayerRole role = session.roleOf(ws);

        if (role == null) {
            sendTo(ws, ServerMessage.error("Not a player in this game."));
            return;
        }

        switch (msg.type()) {
            case "MOVE"           -> handleMove(ws, session, role, msg);
            case "PROMOTE"        -> handlePromotion(ws, session, role, msg);
            case "RESIGN"         -> handleResign(ws, session, role);
            case "OFFER_DRAW"     -> handleOfferDraw(ws, session, role);
            case "RESPOND_DRAW"   -> handleRespondDraw(session, role, msg);
            case "REMATCH_REQUEST"-> handleRematchRequest(ws, session, role);
            case "REMATCH_DECLINE"-> handleRematchDecline(ws);
            default               -> sendTo(ws, ServerMessage.error("Unknown message type: " + msg.type()));
        }
        sessionManager.pruneIfOver(session);
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

        if (result.type() == MoveResult.Type.PROMOTION_NEEDED) {
            session.broadcastBoardState();
            session.sendPromotionNeeded(to);
            return;
        }

        session.broadcastBoardState();
        if (session.isBotTurn() && session.makeBotMove()) {
            session.broadcastBoardState();
            session.maybeBotDrawOffer();
        }
    }

    private void handleResign(WebSocketSession ws, GameSession session, PlayerRole role) throws IOException {
        if (!session.isFull()) {
            session.sendError(ws, "Game not started.");
            return;
        }
        session.resign(role == PlayerRole.WHITE);
        session.broadcastBoardState();
    }

    private void handleOfferDraw(WebSocketSession ws, GameSession session, PlayerRole role) throws IOException {
        if (!session.isFull()) {
            session.sendError(ws, "Game not started.");
            return;
        }
        boolean isWhite = role == PlayerRole.WHITE;
        GameSession.DrawOfferOutcome outcome = session.offerDraw(isWhite);
        switch (outcome) {
            case ACCEPTED -> session.broadcastBoardState();
            case DECLINED -> session.sendDrawDeclinedTo(isWhite);
            case SENT_TO_OPPONENT -> { /* offer forwarded; no further action needed on this side */ }
        }
    }

    private void handleRespondDraw(GameSession session, PlayerRole role, ClientMessage msg) throws IOException {
        boolean isWhite = role == PlayerRole.WHITE;
        if (Boolean.TRUE.equals(msg.accept())) {
            session.acceptDraw();
            session.broadcastBoardState();
        } else {
            // Notify the offeror (the other player) that the draw was declined
            session.sendDrawDeclinedTo(!isWhite);
        }
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
        if (session.isBotTurn() && session.makeBotMove()) {
            session.broadcastBoardState();
            session.maybeBotDrawOffer();
        }
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

    private void handleRematchRequest(WebSocketSession ws, GameSession session, PlayerRole role) throws IOException {
        if (!session.isGameOver()) {
            session.sendError(ws, "Game is not over.");
            return;
        }
        GameSession.RematchOutcome outcome = sessionManager.requestRematch(ws);
        if (outcome == null) return;

        switch (outcome) {
            case WAITING -> session.sendRematchRequestedToOpponent(role == PlayerRole.WHITE);
            case OPPONENT_GONE -> sendTo(ws, ServerMessage.rematchDeclined());
            case STARTED -> {
                GameSession newSession = sessionManager.getSession(ws);
                newSession.sendRematchStart();
                sessionManager.startGame(newSession);
                if (newSession.isBotTurn() && newSession.makeBotMove()) {
                    // bot made its opening move
                }
                newSession.broadcastBoardState();
            }
        }
    }

    private void handleRematchDecline(WebSocketSession ws) throws IOException {
        sessionManager.declineRematch(ws);
    }

    private void sendTo(WebSocketSession ws, ServerMessage message) throws IOException {
        if (!ws.isOpen()) return;
        ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }
}

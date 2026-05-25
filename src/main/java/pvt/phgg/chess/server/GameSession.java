package pvt.phgg.chess.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import pvt.phgg.chess.*;
import pvt.phgg.chess.piece.APiece;
import pvt.phgg.chess.server.dto.LegalMove;
import pvt.phgg.chess.server.dto.PieceDto;
import pvt.phgg.chess.server.dto.ServerMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GameSession {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameSession.class);
    private static final int BOARD_SIZE = 8;

    private final ObjectMapper objectMapper;
    private final GameEngine engine = new GameEngine();

    private WebSocketSession whiteSession;
    private WebSocketSession blackSession;

    public GameSession(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized PlayerRole join(WebSocketSession ws) {
        if (whiteSession == null) {
            whiteSession = ws;
            return PlayerRole.WHITE;
        }
        if (blackSession == null) {
            blackSession = ws;
            return PlayerRole.BLACK;
        }
        return null;
    }

    public synchronized boolean isFull() {
        return whiteSession != null && blackSession != null;
    }

    public synchronized boolean isEmpty() {
        return whiteSession == null && blackSession == null;
    }

    public synchronized PlayerRole roleOf(WebSocketSession ws) {
        if (ws.equals(whiteSession)) return PlayerRole.WHITE;
        if (ws.equals(blackSession)) return PlayerRole.BLACK;
        return null;
    }

    public synchronized void disconnect(WebSocketSession ws) throws IOException {
        PlayerRole role = roleOf(ws);
        if (role == PlayerRole.WHITE) {
            whiteSession = null;
            sendTo(blackSession, ServerMessage.opponentDisconnected());
        } else if (role == PlayerRole.BLACK) {
            blackSession = null;
            sendTo(whiteSession, ServerMessage.opponentDisconnected());
        }
    }

    public synchronized MoveResult applyMove(Position from, Position to) {
        return engine.applyMove(from, to);
    }

    public synchronized MoveResult applyPromotion(Position pos, PromotionChoice choice) {
        return engine.applyPromotion(pos, choice);
    }

    public synchronized pvt.phgg.chess.piece.APiece getPiece(int row, int col) {
        return engine.getPiece(row, col);
    }

    public synchronized boolean isPlayerTurn(PlayerRole role) {
        return (role == PlayerRole.WHITE) == engine.isWhiteTurn();
    }

    public synchronized void broadcastBoardState() throws IOException {
        ServerMessage msg = buildBoardUpdate();
        sendTo(whiteSession, msg);
        sendTo(blackSession, msg);
    }

    public synchronized void sendPromotionNeeded(Position pos) throws IOException {
        ServerMessage msg = ServerMessage.promotionNeeded(pos.getRow(), pos.getCol());
        WebSocketSession current = engine.isWhiteTurn() ? whiteSession : blackSession;
        sendTo(current, msg);
    }

    public synchronized void sendError(WebSocketSession ws, String message) throws IOException {
        sendTo(ws, ServerMessage.error(message));
    }

    private ServerMessage buildBoardUpdate() {
        PieceDto[][] board = new PieceDto[BOARD_SIZE][BOARD_SIZE];
        List<LegalMove> legalMoves = new ArrayList<>();

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                APiece piece = engine.getPiece(row, col);
                board[row][col] = PieceDto.from(piece);

                if (piece.isPositionOccupied() && piece.isWhite() == engine.isWhiteTurn()) {
                    Position pos = new Position(row, col);
                    for (Position target : engine.getLegalMoves(pos)) {
                        legalMoves.add(new LegalMove(row, col, target.getRow(), target.getCol()));
                    }
                }
            }
        }

        String turn = engine.isWhiteTurn() ? "WHITE" : "BLACK";
        String status = engine.getStatus().name();
        return ServerMessage.boardUpdate(board, turn, status, legalMoves);
    }

    private void sendTo(WebSocketSession ws, ServerMessage message) throws IOException {
        if (ws == null || !ws.isOpen()) return;
        String json = objectMapper.writeValueAsString(message);
        synchronized (ws) {
            ws.sendMessage(new TextMessage(json));
        }
    }
}

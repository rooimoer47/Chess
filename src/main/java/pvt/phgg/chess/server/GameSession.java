package pvt.phgg.chess.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import pvt.phgg.chess.*;
import pvt.phgg.chess.piece.APiece;
import pvt.phgg.chess.piece.PieceType;
import pvt.phgg.chess.server.dto.LastMoveDto;
import pvt.phgg.chess.server.dto.LegalMove;
import pvt.phgg.chess.server.dto.PieceDto;
import pvt.phgg.chess.server.dto.ServerMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import pvt.phgg.chess.server.bot.BotStrategy;
import pvt.phgg.chess.server.bot.RandomBotStrategy;

public class GameSession {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameSession.class);
    private static final int BOARD_SIZE = 8;

    private final ObjectMapper objectMapper;
    private final GameEngine engine = new GameEngine();

    // Q > R > B > N > P
    private static final Comparator<PieceType> PIECE_ORDER = Comparator.comparingInt(p -> switch (p) {
        case QUEEN  -> 0;
        case ROOK   -> 1;
        case BISHOP -> 2;
        case KNIGHT -> 3;
        default     -> 4; // PAWN
    });

    private WebSocketSession whiteSession;
    private WebSocketSession blackSession;
    private String whiteUsername;
    private String blackUsername;
    private LastMoveDto lastMove;
    private final List<PieceType> capturedByWhite = new ArrayList<>();
    private final List<PieceType> capturedByBlack = new ArrayList<>();
    private boolean botEnabled = false;
    private boolean botIsWhite;
    private BotStrategy botStrategy;

    public GameSession(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized PlayerRole join(WebSocketSession ws, String username) {
        if (username.equals(whiteUsername) || username.equals(blackUsername)) {
            return null;
        }
        if (whiteSession == null) {
            whiteSession = ws;
            whiteUsername = username;
            return PlayerRole.WHITE;
        }
        if (blackSession == null) {
            blackSession = ws;
            blackUsername = username;
            return PlayerRole.BLACK;
        }
        return null;
    }

    public synchronized boolean joinBot() {
        if (whiteSession == null && whiteUsername == null) {
            whiteUsername = "BOT";
            botEnabled = true;
            botIsWhite = true;
            botStrategy = new RandomBotStrategy();
            return true;
        }
        if (blackSession == null && blackUsername == null) {
            blackUsername = "BOT";
            botEnabled = true;
            botIsWhite = false;
            botStrategy = new RandomBotStrategy();
            return true;
        }
        return false;
    }

    public synchronized boolean isBotTurn() {
        return botEnabled && engine.isWhiteTurn() == botIsWhite;
    }

    public synchronized boolean makeBotMove() {
        Position[] chosen = botStrategy.chooseMove(engine, botIsWhite);
        if (chosen == null) return false;
        MoveResult result = applyMove(chosen[0], chosen[1]);
        if (result.getType() == MoveResult.Type.PROMOTION_NEEDED) {
            applyPromotion(chosen[1], PromotionChoice.QUEEN);
        }
        return true;
    }

    public synchronized boolean isFull() {
        return whiteUsername != null && blackUsername != null;
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
            whiteUsername = null;
            sendTo(blackSession, ServerMessage.opponentDisconnected());
        } else if (role == PlayerRole.BLACK) {
            blackSession = null;
            blackUsername = null;
            sendTo(whiteSession, ServerMessage.opponentDisconnected());
        }
    }

    public synchronized MoveResult applyMove(Position from, Position to) {
        APiece movingPiece = engine.getPiece(from.getRow(), from.getCol());
        APiece targetPiece = engine.getPiece(to.getRow(), to.getCol());

        MoveResult result = engine.applyMove(from, to);

        if (result.isValid()) {
            lastMove = new LastMoveDto(from.getRow(), from.getCol(), to.getRow(), to.getCol());
            if (targetPiece.isPositionOccupied()) {
                recordCapture(movingPiece.isWhite(), targetPiece.getPieceType());
            } else if (movingPiece.isPawn() && from.getCol() != to.getCol()) {
                // en passant — the passed-through square is empty but a pawn is captured
                recordCapture(movingPiece.isWhite(), PieceType.PAWN);
            }
        }
        return result;
    }

    private void recordCapture(boolean byWhite, PieceType pieceType) {
        List<PieceType> list = byWhite ? capturedByWhite : capturedByBlack;
        list.add(pieceType);
        list.sort(PIECE_ORDER);
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
        List<String> capturedW = capturedByWhite.stream().map(Enum::name).toList();
        List<String> capturedB = capturedByBlack.stream().map(Enum::name).toList();
        return ServerMessage.boardUpdate(board, turn, status, legalMoves, lastMove, capturedW, capturedB);
    }

    private void sendTo(WebSocketSession ws, ServerMessage message) throws IOException {
        if (ws == null || !ws.isOpen()) return;
        String json = objectMapper.writeValueAsString(message);
        synchronized (ws) {
            ws.sendMessage(new TextMessage(json));
        }
    }
}

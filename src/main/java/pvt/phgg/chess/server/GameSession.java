package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import pvt.phgg.chess.*;
import pvt.phgg.chess.piece.APiece;
import pvt.phgg.chess.piece.PieceType;
import pvt.phgg.chess.server.dto.LastMoveDto;
import pvt.phgg.chess.server.dto.LegalMove;
import pvt.phgg.chess.server.dto.PieceDto;
import pvt.phgg.chess.server.dto.ServerMessage;
import pvt.phgg.chess.server.game.GameRecorder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import pvt.phgg.chess.server.bot.BotStrategy;
import pvt.phgg.chess.server.bot.MinimaxBotStrategy;
import pvt.phgg.chess.server.bot.RandomBotStrategy;

public class GameSession {

    private static final int BOARD_SIZE = 8;
    private static final String WHITE = "WHITE";
    private static final String BLACK = "BLACK";

    private final ObjectMapper objectMapper;
    private final GameEngine engine = new GameEngine();
    private final GameRecorder gameRecorder;  // null in unit-test context

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
    private Long whitePlayerId;
    private Long blackPlayerId;
    private LastMoveDto lastMove;
    private final List<PieceType> capturedByWhite = new ArrayList<>();
    private final List<PieceType> capturedByBlack = new ArrayList<>();
    private boolean botEnabled = false;
    private boolean botIsWhite;
    private BotStrategy botStrategy;
    private boolean resigned = false;
    private boolean resignedWhite;
    private boolean drawAgreed = false;

    // Game recording state
    private Long gameId;
    private int moveCount = 0;
    private Position pendingPromotionFrom;
    private Position pendingPromotionTo;
    private boolean pendingPromotionWasWhite;

    // Used by unit tests — no recording
    public GameSession(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.gameRecorder = null;
    }

    public GameSession(ObjectMapper objectMapper, GameRecorder gameRecorder) {
        this.objectMapper = objectMapper;
        this.gameRecorder = gameRecorder;
    }

    public synchronized PlayerRole join(WebSocketSession ws, String username) {
        return join(ws, username, null, "RANDOM");
    }

    public synchronized PlayerRole join(WebSocketSession ws, String username, Long userId, String colorPreference) {
        if (username.equals(whiteUsername) || username.equals(blackUsername)) {
            return null;
        }
        // Try preferred colour first (RANDOM and WHITE both try white first)
        if (!"BLACK".equals(colorPreference) && whiteSession == null && whiteUsername == null) {
            whiteSession = ws;
            whiteUsername = username;
            whitePlayerId = userId;
            return PlayerRole.WHITE;
        }
        if (!"WHITE".equals(colorPreference) && blackSession == null && blackUsername == null) {
            blackSession = ws;
            blackUsername = username;
            blackPlayerId = userId;
            return PlayerRole.BLACK;
        }
        // Preferred slot taken — assign whatever is still open
        if (whiteSession == null && whiteUsername == null) {
            whiteSession = ws;
            whiteUsername = username;
            whitePlayerId = userId;
            return PlayerRole.WHITE;
        }
        if (blackSession == null && blackUsername == null) {
            blackSession = ws;
            blackUsername = username;
            blackPlayerId = userId;
            return PlayerRole.BLACK;
        }
        return null;
    }

    public synchronized boolean joinBot(String botType) {
        BotStrategy strategy = selectStrategy(botType);
        if (whiteSession == null && whiteUsername == null) {
            whiteUsername = "BOT";
            botEnabled = true;
            botIsWhite = true;
            botStrategy = strategy;
            return true;
        }
        if (blackSession == null && blackUsername == null) {
            blackUsername = "BOT";
            botEnabled = true;
            botIsWhite = false;
            botStrategy = strategy;
            return true;
        }
        return false;
    }

    private static BotStrategy selectStrategy(String botType) {
        return switch (botType) {
            case "alan"    -> new MinimaxBotStrategy(2);
            case "barbara" -> new MinimaxBotStrategy(3);
            case "claude"  -> new MinimaxBotStrategy(4);
            default        -> new RandomBotStrategy();
        };
    }

    public synchronized void onGameStart() {
        if (gameRecorder == null || gameId != null) return;
        String mode = botEnabled ? "BOT" : "HUMAN";
        gameId = gameRecorder.startGame(whitePlayerId, blackPlayerId, mode);
    }

    public synchronized boolean isBotTurn() {
        return botEnabled && engine.isWhiteTurn() == botIsWhite;
    }

    @SuppressWarnings("java:S2245") // ThreadLocalRandom is fine for non-security game logic
    public synchronized void maybeBotDrawOffer() throws IOException {
        if (!botEnabled) return;
        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            WebSocketSession humanSession = botIsWhite ? blackSession : whiteSession;
            sendTo(humanSession, ServerMessage.drawOffered());
        }
    }

    public synchronized boolean makeBotMove() {
        Position[] chosen = botStrategy.chooseMove(engine, botIsWhite);
        if (chosen.length == 0) return false;
        MoveResult result = applyMove(chosen[0], chosen[1]);
        if (result.type() == MoveResult.Type.PROMOTION_NEEDED) {
            applyPromotion(chosen[1], PromotionChoice.QUEEN);
        }
        return true;
    }

    public synchronized boolean isFull() {
        return whiteUsername != null && blackUsername != null;
    }

    public synchronized boolean isGameOver() {
        if (resigned || drawAgreed) return true;
        GameStatus status = engine.getStatus();
        return status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE
                || status == GameStatus.THREEFOLD_REPETITION || status == GameStatus.FIFTY_MOVE_RULE
                || status == GameStatus.INSUFFICIENT_MATERIAL || status == GameStatus.TIMEOUT;
    }

    public synchronized boolean isClockCompatible() {
        return true;
    }

    public synchronized boolean isEmpty() {
        if (botEnabled) {
            return botIsWhite
                    ? (blackSession == null && blackUsername == null)
                    : (whiteSession == null && whiteUsername == null);
        }
        return whiteSession == null && blackSession == null
                && whiteUsername == null && blackUsername == null;
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

    public synchronized PlayerRole rejoin(WebSocketSession ws, String username) {
        if (username.equals(whiteUsername) && whiteSession == null) {
            whiteSession = ws;
            return PlayerRole.WHITE;
        }
        if (username.equals(blackUsername) && blackSession == null) {
            blackSession = ws;
            return PlayerRole.BLACK;
        }
        return null;
    }

    public synchronized void resign(boolean isWhite) {
        if (!resigned) {
            resigned = true;
            resignedWhite = isWhite;
            if (gameRecorder != null && gameId != null) {
                gameRecorder.endGame(gameId, "RESIGNED", isWhite ? BLACK : WHITE);
            }
        }
    }

    public enum DrawOfferOutcome { ACCEPTED, DECLINED, SENT_TO_OPPONENT }

    @SuppressWarnings("java:S2245") // ThreadLocalRandom is fine for non-security game logic
    public synchronized DrawOfferOutcome offerDraw(boolean isWhite) throws IOException {
        if (botEnabled) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                drawAgreed = true;
                if (gameRecorder != null && gameId != null) {
                    gameRecorder.endGame(gameId, "DRAW_AGREED", null);
                }
                return DrawOfferOutcome.ACCEPTED;
            }
            return DrawOfferOutcome.DECLINED;
        }
        WebSocketSession opponent = isWhite ? blackSession : whiteSession;
        sendTo(opponent, ServerMessage.drawOffered());
        return DrawOfferOutcome.SENT_TO_OPPONENT;
    }

    public synchronized void acceptDraw() {
        drawAgreed = true;
        if (gameRecorder != null && gameId != null) {
            gameRecorder.endGame(gameId, "DRAW_AGREED", null);
        }
    }

    public synchronized void sendDrawDeclinedTo(boolean isWhite) throws IOException {
        sendTo(isWhite ? whiteSession : blackSession, ServerMessage.drawDeclined());
    }

    public synchronized MoveResult applyMove(Position from, Position to) {
        if (botEnabled && !isBotTurn()) {
            botStrategy.recordOpponentMove(from.getRow(), from.getCol(), to.getRow(), to.getCol());
        }
        boolean wasWhiteTurn = engine.isWhiteTurn();
        APiece movingPiece = engine.getPiece(from.getRow(), from.getCol());
        APiece targetPiece = engine.getPiece(to.getRow(), to.getCol());

        MoveResult result = engine.applyMove(from, to);

        if (result.isValid()) {
            lastMove = new LastMoveDto(from.getRow(), from.getCol(), to.getRow(), to.getCol());
            if (targetPiece.isPositionOccupied()) {
                recordCapture(movingPiece.isWhite(), targetPiece.getPieceType());
            } else if (movingPiece.isPawn() && from.getCol() != to.getCol()) {
                recordCapture(movingPiece.isWhite(), PieceType.PAWN);
            }
            if (gameRecorder != null && gameId != null) {
                if (result.type() == MoveResult.Type.PROMOTION_NEEDED) {
                    pendingPromotionFrom = from;
                    pendingPromotionTo = to;
                    pendingPromotionWasWhite = wasWhiteTurn;
                } else {
                    gameRecorder.recordMove(gameId, ++moveCount, from, to, null);
                    if (isTerminalResult(result.type())) {
                        endRecording(result.type(), wasWhiteTurn);
                    }
                }
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
        MoveResult result = engine.applyPromotion(pos, choice);
        if (gameRecorder != null && gameId != null && pendingPromotionFrom != null) {
            gameRecorder.recordMove(gameId, ++moveCount, pendingPromotionFrom, pendingPromotionTo, choice.name());
            boolean wasWhite = pendingPromotionWasWhite;
            pendingPromotionFrom = null;
            pendingPromotionTo = null;
            if (isTerminalResult(result.type())) {
                endRecording(result.type(), wasWhite);
            }
        }
        return result;
    }

    private boolean isTerminalResult(MoveResult.Type type) {
        return type == MoveResult.Type.CHECKMATE
                || type == MoveResult.Type.STALEMATE
                || type == MoveResult.Type.DRAW;
    }

    private void endRecording(MoveResult.Type type, boolean whiteMadeLastMove) {
        String result = switch (type) {
            case CHECKMATE -> "CHECKMATE";
            case STALEMATE -> "STALEMATE";
            default -> engine.getStatus().name();
        };
        String winner = null;
        if (type == MoveResult.Type.CHECKMATE) {
            winner = whiteMadeLastMove ? WHITE : BLACK;
        }
        gameRecorder.endGame(gameId, result, winner);
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
        GameStatus currentStatus;
        if (resigned) {
            currentStatus = GameStatus.RESIGNED;
        } else if (drawAgreed) {
            currentStatus = GameStatus.DRAW_AGREED;
        } else {
            currentStatus = engine.getStatus();
        }
        boolean sendLegalMoves = currentStatus != GameStatus.RESIGNED
                && currentStatus != GameStatus.THREEFOLD_REPETITION
                && currentStatus != GameStatus.FIFTY_MOVE_RULE
                && currentStatus != GameStatus.INSUFFICIENT_MATERIAL
                && currentStatus != GameStatus.DRAW_AGREED;

        PieceDto[][] board = new PieceDto[BOARD_SIZE][BOARD_SIZE];
        List<LegalMove> legalMoves = new ArrayList<>();

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                APiece piece = engine.getPiece(row, col);
                board[row][col] = PieceDto.from(piece);

                if (sendLegalMoves && piece.isPositionOccupied() && piece.isWhite() == engine.isWhiteTurn()) {
                    Position pos = new Position(row, col);
                    for (Position target : engine.getLegalMoves(pos)) {
                        legalMoves.add(new LegalMove(row, col, target.getRow(), target.getCol()));
                    }
                }
            }
        }

        String turn;
        if (resigned) {
            turn = resignedWhite ? WHITE : BLACK;
        } else {
            turn = engine.isWhiteTurn() ? WHITE : BLACK;
        }
        List<String> capturedW = capturedByWhite.stream().map(Enum::name).toList();
        List<String> capturedB = capturedByBlack.stream().map(Enum::name).toList();
        return ServerMessage.boardUpdate(board, turn, currentStatus.name(), legalMoves, lastMove, capturedW, capturedB, null, null);
    }

    private void sendTo(WebSocketSession ws, ServerMessage message) throws IOException {
        if (ws == null || !ws.isOpen()) return;
        ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }
}

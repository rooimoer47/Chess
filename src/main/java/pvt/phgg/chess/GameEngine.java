package pvt.phgg.chess;

import pvt.phgg.chess.piece.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameEngine {

    private static final int BOARD_SIZE = 8;

    private final APiece[][] board = new APiece[BOARD_SIZE][BOARD_SIZE];
    private final BoardState boardState = new BoardState();
    private final Map<String, Integer> positionHistory = new HashMap<>();
    private boolean whiteTurn = true;
    private int halfMoveClock = 0;

    public GameEngine() {
        initializeBoard();
    }

    GameEngine(APiece[][] initialBoard, boolean whiteTurn) {
        this(initialBoard, whiteTurn, 0);
    }

    GameEngine(APiece[][] initialBoard, boolean whiteTurn, int halfMoveClock) {
        for (int r = 0; r < BOARD_SIZE; r++)
            System.arraycopy(initialBoard[r], 0, board[r], 0, BOARD_SIZE);
        this.whiteTurn = whiteTurn;
        this.halfMoveClock = halfMoveClock;
    }

    // --- Public API ---

    public List<Position> getLegalMoves(Position from) {
        APiece piece = board[from.getRow()][from.getCol()];
        if (!piece.isPositionOccupied() || piece.isWhite() != whiteTurn) {
            return List.of();
        }
        return piece.getLegalPositions(board, boardState);
    }

    public MoveResult applyMove(Position from, Position to) {
        APiece piece = board[from.getRow()][from.getCol()];
        List<Position> legal = piece.getLegalPositions(board, boardState);

        for (Position legalPos : legal) {
            if (legalPos.equals(to)) {
                boolean captureOccurred = boardState.isOccupied(board, legalPos);
                executeMove(piece, from, legalPos);

                if (piece.isPawn() && (legalPos.getRow() == 7 || legalPos.getRow() == 0)) {
                    return new MoveResult(MoveResult.Type.PROMOTION_NEEDED, whiteTurn, captureOccurred);
                }

                return finalizeTurn(whiteTurn, captureOccurred, piece.isPawn());
            }
        }
        return new MoveResult(MoveResult.Type.INVALID, whiteTurn, false);
    }

    public MoveResult applyPromotion(Position pos, PromotionChoice choice) {
        boolean isWhite = board[pos.getRow()][pos.getCol()].isWhite();
        board[pos.getRow()][pos.getCol()] = switch (choice) {
            case QUEEN  -> new Queen(pos, isWhite);
            case ROOK   -> new Rook(pos, isWhite);
            case BISHOP -> new Bishop(pos, isWhite);
            case KNIGHT -> new Knight(pos, isWhite);
        };
        return finalizeTurn(isWhite, false, true);
    }

    public GameStatus getStatus() {
        return computeStatus(whiteTurn);
    }

    public APiece getPiece(int row, int col) {
        return board[row][col];
    }

    public boolean isWhiteTurn() {
        return whiteTurn;
    }

    // --- Internal ---

    private MoveResult finalizeTurn(boolean wasWhite, boolean captureOccurred, boolean wasPawnMove) {
        // Close the en passant window opened by the opponent's previous double push
        clearJumpedPawns(!wasWhite);
        whiteTurn = !wasWhite;
        if (captureOccurred || wasPawnMove) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }
        positionHistory.merge(positionFingerprint(), 1, Integer::sum);
        GameStatus status = computeStatus(whiteTurn);
        MoveResult.Type type = switch (status) {
            case IN_PROGRESS          -> MoveResult.Type.VALID;
            case CHECK                -> MoveResult.Type.CHECK;
            case CHECKMATE            -> MoveResult.Type.CHECKMATE;
            case STALEMATE            -> MoveResult.Type.STALEMATE;
            case RESIGNED             -> MoveResult.Type.VALID;
            case THREEFOLD_REPETITION -> MoveResult.Type.DRAW;
            case FIFTY_MOVE_RULE          -> MoveResult.Type.DRAW;
            case INSUFFICIENT_MATERIAL    -> MoveResult.Type.DRAW;
        };
        return new MoveResult(type, wasWhite, captureOccurred);
    }

    private void executeMove(APiece piece, Position from, Position to) {
        board[to.getRow()][to.getCol()] = piece;
        piece.moved();
        clearSquare(from);

        if (to.isEnPassant()) {
            int capturedRow = to.getRow() + (piece.isWhite() ? -1 : 1);
            clearSquare(new Position(capturedRow, to.getCol()));
        }
        if (to.isCastle()) {
            handleCastling(to);
        }
        piece.setCurrentPosition(to);

        if (piece.isPawn() && Math.abs(from.getRow() - to.getRow()) == 2) {
            ((Pawn) piece).setJumped();
        }
    }

    private void handleCastling(Position kingPos) {
        int row = kingPos.getRow();
        int col = kingPos.getCol();
        if (col > 4) {
            APiece rook = board[row][7];
            executeMove(rook, rook.getCurrentPosition(), new Position(row, col - 1));
        } else {
            APiece rook = board[row][0];
            executeMove(rook, rook.getCurrentPosition(), new Position(row, col + 1));
        }
    }

    private void clearSquare(Position pos) {
        board[pos.getRow()][pos.getCol()] = new EmptySquare(pos);
    }

    private void clearJumpedPawns(boolean color) {
        for (APiece[] row : board) {
            for (APiece piece : row) {
                if (piece.isPawn() && piece.isWhite() == color) {
                    ((Pawn) piece).unsetJumped();
                }
            }
        }
    }

    private GameStatus computeStatus(boolean forWhite) {
        if (!boardState.canMove(board, forWhite)) {
            return boardState.isInCheck(board, forWhite) ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
        }
        if (isInsufficientMaterial()) {
            return GameStatus.INSUFFICIENT_MATERIAL;
        }
        if (positionHistory.getOrDefault(positionFingerprint(), 0) >= 3) {
            return GameStatus.THREEFOLD_REPETITION;
        }
        if (halfMoveClock >= 100) {
            return GameStatus.FIFTY_MOVE_RULE;
        }
        return boardState.isInCheck(board, forWhite) ? GameStatus.CHECK : GameStatus.IN_PROGRESS;
    }

    private boolean isInsufficientMaterial() {
        List<APiece> white = new java.util.ArrayList<>();
        List<APiece> black = new java.util.ArrayList<>();
        for (APiece[] row : board) {
            for (APiece p : row) {
                if (!p.isPositionOccupied() || p.isKing()) continue;
                (p.isWhite() ? white : black).add(p);
            }
        }
        int w = white.size(), b = black.size();
        // K vs K
        if (w == 0 && b == 0) return true;
        // K + single minor vs K
        if (w == 1 && b == 0) return isMinor(white.get(0));
        if (w == 0 && b == 1) return isMinor(black.get(0));
        // K + B vs K + B, bishops on the same color square
        if (w == 1 && b == 1) {
            APiece wp = white.get(0), bp = black.get(0);
            if (wp.getPieceType() == PieceType.BISHOP && bp.getPieceType() == PieceType.BISHOP) {
                return sameSquareColor(wp.getCurrentPosition(), bp.getCurrentPosition());
            }
        }
        return false;
    }

    private boolean isMinor(APiece p) {
        return p.getPieceType() == PieceType.BISHOP || p.getPieceType() == PieceType.KNIGHT;
    }

    private boolean sameSquareColor(Position a, Position b) {
        return (a.getRow() + a.getCol()) % 2 == (b.getRow() + b.getCol()) % 2;
    }

    private String positionFingerprint() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                APiece p = board[r][c];
                if (!p.isPositionOccupied()) {
                    sb.append('.');
                } else {
                    sb.append(p.isWhite() ? 'W' : 'B');
                    sb.append(switch (p.getPieceType()) {
                        case PAWN   -> 'P';
                        case KNIGHT -> 'N';
                        case BISHOP -> 'B';
                        case ROOK   -> 'R';
                        case QUEEN  -> 'Q';
                        case KING   -> 'K';
                    });
                    if (p.isOriginalPosition()) sb.append('O');
                    if (p.isPawn() && ((Pawn) p).isJumped()) sb.append('J');
                }
                sb.append(',');
            }
        }
        sb.append(whiteTurn ? 'W' : 'B');
        return sb.toString();
    }

    private void initializeBoard() {
        board[0][0] = new Rook(new Position(0, 0), true);
        board[0][7] = new Rook(new Position(0, 7), true);
        board[0][1] = new Knight(new Position(0, 1), true);
        board[0][6] = new Knight(new Position(0, 6), true);
        board[0][2] = new Bishop(new Position(0, 2), true);
        board[0][5] = new Bishop(new Position(0, 5), true);
        board[0][3] = new Queen(new Position(0, 3), true);
        board[0][4] = new King(new Position(0, 4), true);

        for (int col = 0; col < BOARD_SIZE; col++) {
            board[1][col] = new Pawn(new Position(1, col), true);
        }

        for (int row = 2; row < 6; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                board[row][col] = new EmptySquare(new Position(row, col));
            }
        }

        for (int col = 0; col < BOARD_SIZE; col++) {
            board[6][col] = new Pawn(new Position(6, col), false);
        }

        board[7][0] = new Rook(new Position(7, 0), false);
        board[7][7] = new Rook(new Position(7, 7), false);
        board[7][1] = new Knight(new Position(7, 1), false);
        board[7][6] = new Knight(new Position(7, 6), false);
        board[7][2] = new Bishop(new Position(7, 2), false);
        board[7][5] = new Bishop(new Position(7, 5), false);
        board[7][3] = new Queen(new Position(7, 3), false);
        board[7][4] = new King(new Position(7, 4), false);
    }
}

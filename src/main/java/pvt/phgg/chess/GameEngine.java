package pvt.phgg.chess;

import pvt.phgg.chess.piece.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameEngine {

    private static final int BOARD_SIZE = 8;
    public static final String STANDARD_BACK_RANK = "RNBQKBNR";

    private final APiece[][] board = new APiece[BOARD_SIZE][BOARD_SIZE];
    private final BoardState boardState = new BoardState();
    private final Map<String, Integer> positionHistory = new HashMap<>();
    private boolean whiteTurn = true;
    private int halfMoveClock = 0;

    public GameEngine() {
        this(STANDARD_BACK_RANK);
    }

    public GameEngine(String backRank) {
        initializeBoard(backRank);
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

    public GameEngine(GameEngine source) {
        APiece[][] deep = source.boardState.deepCopy(source.board);
        for (int r = 0; r < BOARD_SIZE; r++)
            System.arraycopy(deep[r], 0, board[r], 0, BOARD_SIZE);
        this.whiteTurn = source.whiteTurn;
        this.halfMoveClock = source.halfMoveClock;
        this.positionHistory.putAll(source.positionHistory);
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
        if (piece.isKing() && piece.isOriginalPosition()) {
            // In Chess960 the king's castle target (c/g-file) may coincide with a normal one-square
            // step, so castling is expressed by moving the king onto its own rook. Translate that
            // gesture to the canonical c/g-file castle target before matching.
            to = canonicalCastleTarget((King) piece, from, to);
        }
        List<Position> legal = piece.getLegalPositions(board, boardState);

        for (Position legalPos : legal) {
            // Position.equals compares only row/col, so a castle and a normal one-square step can
            // share a target square (Chess960). When the caller asked for a castle, only a castle
            // legal move satisfies it — otherwise the normal step (listed first) would win.
            if (legalPos.equals(to) && (!to.isCastle() || legalPos.isCastle())) {
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

    public MoveResult.Type peekMoveResult(Position from, Position to) {
        APiece[][] copy = boardState.deepCopy(board);
        GameEngine temp = new GameEngine(copy, whiteTurn, halfMoveClock);
        return temp.applyMove(from, to).type();
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
            case IN_PROGRESS, RESIGNED -> MoveResult.Type.VALID;
            case CHECK                -> MoveResult.Type.CHECK;
            case CHECKMATE, TIMEOUT            -> MoveResult.Type.CHECKMATE;
            case STALEMATE            -> MoveResult.Type.STALEMATE;
            case THREEFOLD_REPETITION, FIFTY_MOVE_RULE, INSUFFICIENT_MATERIAL, DRAW_AGREED -> MoveResult.Type.DRAW;
        };
        return new MoveResult(type, wasWhite, captureOccurred);
    }

    private void executeMove(APiece piece, Position from, Position to) {
        if (to.isCastle()) {
            executeCastle((King) piece, from, to);
            return;
        }

        board[to.getRow()][to.getCol()] = piece;
        piece.moved();
        clearSquare(from);

        if (to.isEnPassant()) {
            int capturedRow = to.getRow() + (piece.isWhite() ? -1 : 1);
            clearSquare(new Position(capturedRow, to.getCol()));
        }
        piece.setCurrentPosition(to);

        if (piece.isPawn() && Math.abs(from.getRow() - to.getRow()) == 2) {
            ((Pawn) piece).setJumped();
        }
    }

    // Maps a "king moves onto its own rook" castle gesture to the canonical c-file (2) / g-file (6)
    // castle target. Returns `to` unchanged for every ordinary move, so standard-chess input (king
    // steps two squares to c/g) and stored move logs are unaffected. Only fires when the target
    // square actually holds this king's own, unmoved rook — an enemy piece or an empty square there
    // is left as an ordinary move/capture.
    private Position canonicalCastleTarget(King king, Position from, Position to) {
        int row = to.getRow();
        int col = to.getCol();
        if (from.getRow() != row || col == from.getCol()) {
            return to;
        }
        if (col == king.getQueensideRookFile() && isOwnCastlingRook(row, col, king.isWhite())) {
            return new Position(row, 2, Position.SpecialMove.CASTLE);
        }
        if (col == king.getKingsideRookFile() && isOwnCastlingRook(row, col, king.isWhite())) {
            return new Position(row, 6, Position.SpecialMove.CASTLE);
        }
        return to;
    }

    private boolean isOwnCastlingRook(int row, int col, boolean white) {
        APiece piece = board[row][col];
        return piece.isRook() && piece.isWhite() == white && piece.isOriginalPosition();
    }

    // The king's destination is always the c-file (queenside) or g-file (kingside); the rook's is
    // the d-file or f-file. In Chess960 the king or rook may already sit on a destination square, or
    // start adjacent to each other, so both origins are cleared before either piece is placed to
    // avoid one relocation clobbering the other.
    private void executeCastle(King king, Position from, Position kingDest) {
        int row = from.getRow();
        boolean kingside = kingDest.getCol() > 4;   // destination file is fixed at c(2) or g(6)
        int rookStartFile = kingside ? king.getKingsideRookFile() : king.getQueensideRookFile();
        int rookDestCol = kingside ? 5 : 3;

        APiece rook = board[row][rookStartFile];

        clearSquare(from);
        clearSquare(new Position(row, rookStartFile));

        Position kingTo = new Position(row, kingDest.getCol());
        board[kingTo.getRow()][kingTo.getCol()] = king;
        king.moved();
        king.setCurrentPosition(kingTo);

        Position rookTo = new Position(row, rookDestCol);
        board[rookTo.getRow()][rookTo.getCol()] = rook;
        rook.moved();
        rook.setCurrentPosition(rookTo);
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
        List<APiece> white = new ArrayList<>();
        List<APiece> black = new ArrayList<>();
        for (APiece[] row : board) {
            for (APiece p : row) {
                if (!p.isPositionOccupied() || p.isKing()) continue;
                (p.isWhite() ? white : black).add(p);
            }
        }
        int w = white.size();
        int b = black.size();
        // K vs K
        if (w == 0 && b == 0) return true;
        // K + single minor vs K
        if (w == 1 && b == 0) return isMinor(white.getFirst());
        if (w == 0 && b == 1) return isMinor(black.getFirst());
        // K + B vs K + B, bishops on the same color square
        if (w == 1 && b == 1) {
            APiece wp = white.getFirst();
            APiece bp = black.getFirst();
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

    private void initializeBoard(String backRank) {
        if (backRank == null || backRank.length() != BOARD_SIZE) {
            throw new IllegalArgumentException("Back rank must be " + BOARD_SIZE + " characters: " + backRank);
        }

        int kingsideRookFile = -1;
        int queensideRookFile = -1;
        for (int col = 0; col < BOARD_SIZE; col++) {
            char c = backRank.charAt(col);
            board[0][col] = pieceFor(c, new Position(0, col), true);
            board[7][col] = pieceFor(c, new Position(7, col), false);
            if (c == 'R') {
                if (queensideRookFile == -1) {
                    queensideRookFile = col;   // lower file seen first
                } else {
                    kingsideRookFile = col;    // higher file
                }
            }
        }

        for (int col = 0; col < BOARD_SIZE; col++) {
            board[1][col] = new Pawn(new Position(1, col), true);
            board[6][col] = new Pawn(new Position(6, col), false);
        }

        for (int row = 2; row < 6; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                board[row][col] = new EmptySquare(new Position(row, col));
            }
        }

        // Tell each king which files its rooks start on, so castling geometry works for any layout.
        for (int row : new int[]{0, 7}) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (board[row][col] instanceof King king) {
                    king.setRookFiles(queensideRookFile, kingsideRookFile);
                }
            }
        }
    }

    private APiece pieceFor(char c, Position pos, boolean white) {
        return switch (c) {
            case 'R' -> new Rook(pos, white);
            case 'N' -> new Knight(pos, white);
            case 'B' -> new Bishop(pos, white);
            case 'Q' -> new Queen(pos, white);
            case 'K' -> new King(pos, white);
            default  -> throw new IllegalArgumentException("Bad back-rank char: " + c);
        };
    }
}

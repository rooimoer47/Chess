package pvt.phgg.chess.server.bot;

import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.GameStatus;
import pvt.phgg.chess.MoveResult;
import pvt.phgg.chess.Position;
import pvt.phgg.chess.PromotionChoice;
import pvt.phgg.chess.piece.APiece;
import pvt.phgg.chess.piece.PieceType;

import java.util.ArrayList;
import java.util.List;

public class MinimaxBotStrategy implements BotStrategy {

    private static final int INF = Integer.MAX_VALUE / 2;

    private static final int PAWN_VALUE   = 100;
    private static final int KNIGHT_VALUE = 320;
    private static final int BISHOP_VALUE = 330;
    private static final int ROOK_VALUE   = 500;
    private static final int QUEEN_VALUE  = 900;
    private static final int KING_VALUE   = 20_000;

    // Piece-square tables, white's perspective (row 0 = rank 1, row 7 = rank 8).
    // For black pieces, mirror by indexing [7 - row][col].
    private static final int[][] PST_PAWN = {
        {  0,  0,  0,  0,  0,  0,  0,  0},
        {  5, 10, 10,-20,-20, 10, 10,  5},
        {  5, -5,-10,  0,  0,-10, -5,  5},
        {  0,  0,  0, 20, 20,  0,  0,  0},
        {  5,  5, 10, 25, 25, 10,  5,  5},
        { 10, 10, 20, 30, 30, 20, 10, 10},
        { 50, 50, 50, 50, 50, 50, 50, 50},
        {  0,  0,  0,  0,  0,  0,  0,  0}
    };
    private static final int[][] PST_KNIGHT = {
        {-50,-40,-30,-30,-30,-30,-40,-50},
        {-40,-20,  0,  5,  5,  0,-20,-40},
        {-30,  5, 10, 15, 15, 10,  5,-30},
        {-30,  0, 15, 20, 20, 15,  0,-30},
        {-30,  5, 15, 20, 20, 15,  5,-30},
        {-30,  0, 10, 15, 15, 10,  0,-30},
        {-40,-20,  0,  0,  0,  0,-20,-40},
        {-50,-40,-30,-30,-30,-30,-40,-50}
    };
    private static final int[][] PST_BISHOP = {
        {-20,-10,-10,-10,-10,-10,-10,-20},
        {-10,  0,  0,  0,  0,  0,  0,-10},
        {-10,  0,  5, 10, 10,  5,  0,-10},
        {-10,  5,  5, 10, 10,  5,  5,-10},
        {-10,  0, 10, 10, 10, 10,  0,-10},
        {-10, 10, 10, 10, 10, 10, 10,-10},
        {-10,  5,  0,  0,  0,  0,  5,-10},
        {-20,-10,-10,-10,-10,-10,-10,-20}
    };
    private static final int[][] PST_ROOK = {
        {  0,  0,  0,  5,  5,  0,  0,  0},
        { -5,  0,  0,  0,  0,  0,  0, -5},
        { -5,  0,  0,  0,  0,  0,  0, -5},
        { -5,  0,  0,  0,  0,  0,  0, -5},
        { -5,  0,  0,  0,  0,  0,  0, -5},
        { -5,  0,  0,  0,  0,  0,  0, -5},
        {  5, 10, 10, 10, 10, 10, 10,  5},
        {  0,  0,  0,  0,  0,  0,  0,  0}
    };
    private static final int[][] PST_QUEEN = {
        {-20,-10,-10, -5, -5,-10,-10,-20},
        {-10,  0,  5,  0,  0,  0,  0,-10},
        {-10,  5,  5,  5,  5,  5,  0,-10},
        {  0,  0,  5,  5,  5,  5,  0, -5},
        { -5,  0,  5,  5,  5,  5,  0, -5},
        {-10,  0,  5,  5,  5,  5,  0,-10},
        {-10,  0,  0,  0,  0,  0,  0,-10},
        {-20,-10,-10, -5, -5,-10,-10,-20}
    };
    private static final int[][] PST_KING = {
        { 20, 30, 10,  0,  0, 10, 30, 20},
        { 20, 20,  0,  0,  0,  0, 20, 20},
        {-10,-20,-20,-20,-20,-20,-20,-10},
        {-20,-30,-30,-40,-40,-30,-30,-20},
        {-30,-40,-40,-50,-50,-40,-40,-30},
        {-30,-40,-40,-50,-50,-40,-40,-30},
        {-30,-40,-40,-50,-50,-40,-40,-30},
        {-30,-40,-40,-50,-50,-40,-40,-30}
    };

    private final int depth;
    private String moveHistory = "";

    public MinimaxBotStrategy(int depth) {
        this.depth = depth;
    }

    @Override
    public void recordOpponentMove(int fromRow, int fromCol, int toRow, int toCol) {
        moveHistory += "" + fromRow + fromCol + toRow + toCol;
    }

    @Override
    public Position[] chooseMove(GameEngine engine, boolean isWhite) {
        int[] bookMove = OpeningBook.lookup(moveHistory);
        if (bookMove != null) {
            moveHistory += "" + bookMove[0] + bookMove[1] + bookMove[2] + bookMove[3];
            return new Position[]{new Position(bookMove[0], bookMove[1]),
                                   new Position(bookMove[2], bookMove[3])};
        }

        List<int[]> rootMoves = collectMoves(engine, isWhite);
        if (rootMoves.isEmpty()) return new Position[0];

        record ScoredMove(int score, int[] move) {}
        final int searchDepth = depth;

        ScoredMove best = rootMoves.parallelStream()
            .map(move -> {
                GameEngine copy = new GameEngine(engine);
                MoveResult result = copy.applyMove(
                        new Position(move[0], move[1]), new Position(move[2], move[3]));
                if (result.type() == MoveResult.Type.PROMOTION_NEEDED) {
                    copy.applyPromotion(new Position(move[2], move[3]), PromotionChoice.QUEEN);
                }
                int score = alphabeta(copy, searchDepth - 1, -INF, INF, !isWhite);
                return new ScoredMove(score, move);
            })
            .reduce((a, b) -> isWhite ? (a.score() >= b.score() ? a : b)
                                       : (a.score() <= b.score() ? a : b))
            .orElse(new ScoredMove(0, rootMoves.get(0)));

        int[] m = best.move();
        moveHistory += "" + m[0] + m[1] + m[2] + m[3];
        return new Position[]{new Position(m[0], m[1]), new Position(m[2], m[3])};
    }

    private int alphabeta(GameEngine engine, int depth, int alpha, int beta, boolean maximising) {
        GameStatus status = engine.getStatus();
        if (isTerminal(status)) return terminalScore(status, maximising);
        if (depth == 0) return evaluate(engine);

        List<int[]> moves = collectMoves(engine, maximising);
        if (moves.isEmpty()) return evaluate(engine);

        if (maximising) {
            int best = -INF;
            for (int[] move : moves) {
                GameEngine copy = new GameEngine(engine);
                MoveResult result = copy.applyMove(
                        new Position(move[0], move[1]), new Position(move[2], move[3]));
                if (result.type() == MoveResult.Type.PROMOTION_NEEDED) {
                    copy.applyPromotion(new Position(move[2], move[3]), PromotionChoice.QUEEN);
                }
                best = Math.max(best, alphabeta(copy, depth - 1, alpha, beta, false));
                alpha = Math.max(alpha, best);
                if (alpha >= beta) break;
            }
            return best;
        } else {
            int best = INF;
            for (int[] move : moves) {
                GameEngine copy = new GameEngine(engine);
                MoveResult result = copy.applyMove(
                        new Position(move[0], move[1]), new Position(move[2], move[3]));
                if (result.type() == MoveResult.Type.PROMOTION_NEEDED) {
                    copy.applyPromotion(new Position(move[2], move[3]), PromotionChoice.QUEEN);
                }
                best = Math.min(best, alphabeta(copy, depth - 1, alpha, beta, true));
                beta = Math.min(beta, best);
                if (beta <= alpha) break;
            }
            return best;
        }
    }

    private List<int[]> collectMoves(GameEngine engine, boolean forWhite) {
        List<int[]> moves = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                APiece piece = engine.getPiece(row, col);
                if (!piece.isPositionOccupied() || piece.isWhite() != forWhite) continue;
                for (Position to : engine.getLegalMoves(new Position(row, col))) {
                    moves.add(new int[]{row, col, to.getRow(), to.getCol()});
                }
            }
        }
        // Captures first for better alpha-beta pruning
        moves.sort((a, b) -> {
            boolean aCaptures = engine.getPiece(a[2], a[3]).isPositionOccupied();
            boolean bCaptures = engine.getPiece(b[2], b[3]).isPositionOccupied();
            if (aCaptures == bCaptures) return 0;
            return aCaptures ? -1 : 1;
        });
        return moves;
    }

    private boolean isTerminal(GameStatus status) {
        return switch (status) {
            case CHECKMATE, STALEMATE, THREEFOLD_REPETITION,
                 FIFTY_MOVE_RULE, INSUFFICIENT_MATERIAL, DRAW_AGREED -> true;
            default -> false;
        };
    }

    // When status is CHECKMATE, the side whose turn it is was mated.
    // maximising = true means it's white's turn; white mated → very bad for white → -INF.
    private int terminalScore(GameStatus status, boolean maximising) {
        if (status == GameStatus.CHECKMATE) {
            return maximising ? -INF : INF;
        }
        return 0;
    }

    private int evaluate(GameEngine engine) {
        int score = 0;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                APiece piece = engine.getPiece(row, col);
                if (!piece.isPositionOccupied()) continue;
                int tableRow = piece.isWhite() ? row : 7 - row;
                int value = materialValue(piece.getPieceType()) + pst(piece.getPieceType(), tableRow, col);
                score += piece.isWhite() ? value : -value;
            }
        }
        return score;
    }

    private int materialValue(PieceType type) {
        return switch (type) {
            case PAWN   -> PAWN_VALUE;
            case KNIGHT -> KNIGHT_VALUE;
            case BISHOP -> BISHOP_VALUE;
            case ROOK   -> ROOK_VALUE;
            case QUEEN  -> QUEEN_VALUE;
            case KING   -> KING_VALUE;
        };
    }

    private int pst(PieceType type, int row, int col) {
        return switch (type) {
            case PAWN   -> PST_PAWN[row][col];
            case KNIGHT -> PST_KNIGHT[row][col];
            case BISHOP -> PST_BISHOP[row][col];
            case ROOK   -> PST_ROOK[row][col];
            case QUEEN  -> PST_QUEEN[row][col];
            case KING   -> PST_KING[row][col];
        };
    }
}

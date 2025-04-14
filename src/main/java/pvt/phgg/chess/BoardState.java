package pvt.phgg.chess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pvt.phgg.chess.piece.APiece;
import pvt.phgg.chess.piece.King;

import java.util.List;

public class BoardState {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoardState.class);
    private static final int BOARD_SIZE = 8;

    public boolean isOnBoard(Position pos) {
        return pos.getRow() >= 0 && pos.getRow() < BOARD_SIZE &&
                pos.getCol() >= 0 && pos.getCol() < BOARD_SIZE;
    }

    public boolean isOccupied(APiece[][] board, Position pos) {
        return board[pos.getRow()][pos.getCol()].getImage() != null;
    }

    public boolean isUnOccupied(APiece[][] board, List<Position> positions) {
        for (Position pos : positions) {
            if (isOccupied(board, pos)) {
                return false;
            }
        }
        return true;
    }

    public boolean isInCheck(APiece[][] board, boolean kingColor) {
        Position kingPosition = findKing(board, kingColor);
        if (kingPosition == null) {
            LOGGER.warn("Cannot find {} king on the board", kingColor ? "white" : "black");
            return false;
        }

        for (APiece[] row : board) {
            for (APiece piece : row) {
                if (piece.isPositionOccupied() && piece.isWhite() != kingColor) {
                    List<Position> attackPositions = piece.getValidPositions(board, this);

                    for (Position attackPos : attackPositions) {
                        if (attackPos.equals(kingPosition)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private Position findKing(APiece[][] board, boolean kingColor) {
        for (int row = 0; row < board.length; row++) {
            for (int col = 0; col < board[row].length; col++) {
                APiece piece = board[row][col];
                if (piece.isKing() && piece.isWhite() == kingColor) {
                    return new Position(row, col);
                }
            }
        }
        return null;
    }

    public boolean canMove(APiece[][] board, boolean playerColor) {
        for (APiece[] row : board) {
            for (APiece piece : row) {
                if (piece.isPositionOccupied() &&
                        piece.isWhite() == playerColor &&
                        !piece.getLegalPositions(board).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean arePositionsSafe(APiece [][] board, List<Position> posList, boolean KingColor) {
        for (APiece [] row : board) {
            for (APiece piece : row) {
                if (piece.isPositionOccupied() && (piece.isWhite() != KingColor)) {
                    List<Position> attackPositions;
                    if (piece.isKing()) {
                        attackPositions = ((King) piece).getKingMovements(board, this);
                    } else {
                        attackPositions = piece.getValidPositions(board, this);
                    }

                    for (Position attackPos : attackPositions) {
                        for (Position posToCheck : posList) {
                            if (attackPos.equals(posToCheck)) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    public APiece[][] deepCopy(APiece[][] original) {
        APiece[][] copy = new APiece[original.length][];

        for (int i = 0; i < original.length; i++) {
            copy[i] = new APiece[original[i].length];
            for (int j = 0; j < original[i].length; j++) {
                copy[i][j] = original[i][j].clone();
            }
        }

        return copy;
    }
}

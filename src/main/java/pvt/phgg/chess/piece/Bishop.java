package pvt.phgg.chess.piece;

import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

import java.util.ArrayList;
import java.util.List;

public class Bishop extends APiece{

    public Bishop(Position position, boolean white) {
        super(position, white);
    }

    @Override
    public PieceType getPieceType() {
        return PieceType.BISHOP;
    }

    @Override
    public List<Position> getValidPositions(APiece[][] board, BoardState boardState) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] direction : directions) {
            Position newPos = getCurrentPosition().withRowOffset(direction[0]).withColOffset(direction[1]);

            while (boardState.isOnBoard(newPos)) {
                if (boardState.isOccupied(board, newPos)) {
                    if (board[newPos.getRow()][newPos.getCol()].isWhite() != this.isWhite()) {
                        moves.add(new Position(newPos.getRow(), newPos.getCol()));
                    }
                    break; // can't move past an occupied square
                }
                moves.add(new Position(newPos.getRow(), newPos.getCol()));
                newPos = newPos.withRowOffset(direction[0]).withColOffset(direction[1]);
            }
        }

        return moves;
    }

    @Override
    public APiece copy() {
        return copyStateTo(new Bishop(new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol()), isWhite()));
    }
}
package pvt.phgg.chess.piece;

import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

import java.util.ArrayList;
import java.util.List;

public class Knight extends APiece{

    public Knight(Position position, boolean white) {
        super(position, white);
    }

    @Override
    public PieceType getPieceType() {
        return PieceType.KNIGHT;
    }

    @Override
    public List<Position> getValidPositions(APiece[][] board, BoardState boardState) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, 2}, {-2, 1}, {-2, -1}, {-1, -2}};
        for (int[] direction : directions) {
            Position newPos = getCurrentPosition().withRowOffset(direction[0]).withColOffset(direction[1]);

            if (boardState.isOnBoard(newPos) &&
                    (!boardState.isOccupied(board, newPos) ||
                     board[newPos.getRow()][newPos.getCol()].isWhite() != this.isWhite())) {
                moves.add(new Position(newPos.getRow(), newPos.getCol()));
            }
        }

        return moves;
    }

    @Override
    public APiece copy() {
        return copyStateTo(new Knight(new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol()), isWhite()));
    }
}
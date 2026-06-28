package pvt.phgg.chess.piece;

import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

import java.util.ArrayList;
import java.util.List;

public class Rook extends APiece{

    public Rook(Position position, boolean white) {
        super(position, white);
    }

    @Override
    public List<Position> getValidPositions(APiece[][] board, BoardState boardState) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
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
    public PieceType getPieceType() {
        return PieceType.ROOK;
    }

    @Override
    public boolean isRook() {
        return true;
    }

    @Override
    public APiece copy() {
        return copyStateTo(new Rook(new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol()), isWhite()));
    }
}
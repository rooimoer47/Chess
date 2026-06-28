package pvt.phgg.chess.piece;

import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

import java.util.ArrayList;
import java.util.List;

public class EmptySquare extends APiece {

    public EmptySquare(Position pos) {
        super(pos);
    }

    @Override
    public PieceType getPieceType() {
        return null;
    }

    @Override
    public boolean isPositionOccupied() {
        return false;
    }

    @Override
    public List<Position> getValidPositions(APiece[][] board, BoardState boardState) {
        return new ArrayList<>();
    }

    @Override
    public APiece copy() {
        return new EmptySquare(new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol()));
    }
}

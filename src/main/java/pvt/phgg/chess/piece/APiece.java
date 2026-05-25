package pvt.phgg.chess.piece;

import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public abstract class APiece implements Cloneable {

    private Position pos;
    private final boolean white;
    private boolean originalPosition = true;
    protected static final String ROOT = "src/main/resources";

    public APiece(Position pos) {
        this.pos = pos;
        this.white = false;
    }

    public APiece(Position position, boolean white) {
        this.pos = position;
        this.white = white;
    }

    public abstract BufferedImage getImage(boolean selected);

    public boolean isPositionOccupied() {
        return true;
    }

    public abstract List<Position> getValidPositions(APiece[][] board, BoardState boardState);

    public Position getCurrentPosition() {
        return this.pos;
    }

    public void setCurrentPosition(Position position) {
        this.pos = position;
    }

    public boolean isWhite() {
        return white;
    }

    public boolean isOriginalPosition() {
        return originalPosition;
    }

    public void moved() {
        originalPosition = false;
    }

    public boolean isKing() {
        return false;
    }

    public boolean isRook() {
        return false;
    }

    public boolean isPawn() {
        return false;
    }

    public List<Position> getLegalPositions(APiece[][] board, BoardState boardState) {
        List<Position> validPositions = this.getValidPositions(board, boardState);
        List<Position> legalPositions = new ArrayList<>();

        for (Position candidate : validPositions) {
            APiece[][] tempBoard = boardState.deepCopy(board);
            tempBoard[candidate.getRow()][candidate.getCol()] = this.clone();
            tempBoard[this.pos.getRow()][this.pos.getCol()] = new EmptySquare(this.pos);
            tempBoard[candidate.getRow()][candidate.getCol()].setCurrentPosition(candidate);
            if (!boardState.isInCheck(tempBoard, this.isWhite())) {
                legalPositions.add(candidate);
            }
        }
        return legalPositions;
    }

    public List<Position> getLegalPositions(APiece[][] board) {
        return getLegalPositions(board, new BoardState());
    }

    @Override
    public APiece clone() {
        try {
            APiece clone = (APiece) super.clone();
            clone.pos = new Position(this.pos.getRow(), this.pos.getCol());
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}

package pvt.phgg.chess.piece;

import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public abstract class APiece implements Cloneable{
    private Position pos;
    private final boolean white;
    private boolean selected = false;
    private boolean marked = false;
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

    public abstract BufferedImage getImage();

    public boolean isPositionOccupied() {
        return this.getImage() != null;
    }

    public abstract List<Position> getValidPositions(APiece [][] board, BoardState boardState);

    public Position getCurrentPosition() {
        return this.pos;
    }

    public void setCurrentPosition(Position position) {
        this.pos = position;
    }

    public boolean isWhite() {
        return white;
    }

    public boolean isSelected() {
        return selected;
    }

    public void toggleSelected() {
        selected = !selected;
    }

    public boolean isMarked() {
        return marked;
    }

    public void mark() {
        marked = true;
    }

    public void unMark() {
        marked = false;
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

        for (Position pos : validPositions) {
            APiece[][] tempBoard = boardState.deepCopy(board);

            tempBoard[pos.getRow()][pos.getCol()] = this.clone();
            tempBoard[this.pos.getRow()][this.pos.getCol()] = createEmptyPiece(this.pos);
            tempBoard[pos.getRow()][pos.getCol()].setCurrentPosition(pos);
            if (!boardState.isInCheck(tempBoard, this.isWhite())) {
                legalPositions.add(pos);
            }
        }
        return legalPositions;
    }

    public List<Position> getLegalPositions(APiece[][] board) {
        BoardState tempState = new BoardState();
        return getLegalPositions(board, tempState);
    }

    private APiece createEmptyPiece(Position pos) {
        return new APiece(new Position(pos.getRow(), pos.getCol())) {
            @Override
            public BufferedImage getImage() {
                return null;
            }

            @Override
            public List<Position> getValidPositions(APiece[][] board, BoardState boardState) {
                return new ArrayList<>();
            }
        };
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
package main.java.pvt.phgg.chess.piece;

import main.java.pvt.phgg.chess.Board;
import main.java.pvt.phgg.chess.Position;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public abstract class APiece implements Cloneable{
    private Position position;
    private final boolean white;
    private boolean selected = false;
    private boolean marked = false;
    private boolean originalPosition = true;
    static final String ROOT = "src/main/resources";

    public APiece(Position position) {
        this.position = position;
        this.white = false;
    }

    public APiece(Position position, boolean white) {
        this.position = position;
        this.white = white;
    }

    public abstract BufferedImage getImage();
    public boolean isPositionOccupied() {
        return this.getImage() != null;
    }
    public abstract List<Position> getValidPositions(APiece [][] board);
    public Position getCurrentPosition() {
        return this.position;
    }
    public void setCurrentPosition(Position position) {
        this.position = position;
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

    public boolean isInCheck(APiece [][] board) {
        for (APiece [] row : board) {
            for (APiece square : row) {
                if (square.isPositionOccupied() && (square.isWhite() != this.isWhite())) {
                    List<Position> opponentPositions = square.getValidPositions(board);
                    for (Position position : opponentPositions) {
                        if (board[position.getRow()][position.getCol()].isPositionOccupied() && board[position.getRow()][position.getCol()].isKing()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public List<Position> getRealPositions(APiece[][] board) {
        List<Position> validPositions = this.getValidPositions(board);
        List<Position> realPositions = new ArrayList<>();
        if (isInCheck(board)) {
            for (Position pos : validPositions) {
                APiece[][] nepBoard = Board.deepCopy();
                nepBoard[pos.getRow()][pos.getCol()] = this.clone();
                nepBoard[this.position.getRow()][this.position.getCol()] = new APiece(new Position(this.position.getRow(), this.position.getCol())) {
                    @Override
                    public BufferedImage getImage() {
                        return null;
                    }

                    @Override
                    public List<Position> getValidPositions(APiece[][] board) {
                        return null;
                    }

                };
                nepBoard[pos.getRow()][pos.getCol()].setCurrentPosition(pos);
                if (!this.isInCheck(nepBoard)) {
                    realPositions.add(new Position(pos.getRow(), pos.getCol()));
                }
            }
            return realPositions;
        }
        return validPositions;
    }

    @Override
    public APiece clone() {
        try {
            APiece clone = (APiece) super.clone();
            clone.position = new Position(this.position.getRow(), this.position.getCol());
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
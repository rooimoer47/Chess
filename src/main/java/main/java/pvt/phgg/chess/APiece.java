package main.java.pvt.phgg.chess;

import java.awt.image.BufferedImage;
import java.util.List;

abstract class APiece {
    private Position position;
    private final boolean white;
    private boolean selected = false;
    static final String ROOT = "src/main/resources";

    public APiece(Position position, boolean white) {
        this.position = position;
        this.white = white;
    }

    public abstract BufferedImage getImage();
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

    boolean isOnBoard(Position pos) {
        return pos.getRow() >= 0 && pos.getRow() < 8 && pos.getCol() >= 0 && pos.getCol() < 8;
    }

    public boolean isOccupied(APiece [][] board, Position position) {
        return board[position.getRow()][position.getCol()] != null;
    }
}

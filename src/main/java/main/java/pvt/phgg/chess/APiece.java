package main.java.pvt.phgg.chess;

import java.awt.image.BufferedImage;
import java.util.List;

abstract class APiece {
    private Position position;
    private final boolean white;
    private boolean selected = false;
    private boolean marked = false;
    private boolean originalPosition = true;
    static final String ROOT = "src/main/resources";

    public APiece() {
        this.white = false;
    }

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
}

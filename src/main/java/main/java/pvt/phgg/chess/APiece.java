package main.java.pvt.phgg.chess;

import java.awt.image.BufferedImage;

abstract class APiece {
    final boolean white;
    static final String ROOT = "src/main/resources";

    public APiece(boolean white) {
        this.white = white;
    }

    public abstract BufferedImage getImage();
    public abstract Type getType();
    public abstract boolean isWhite();
//    TODO
//    getRow
//    getCol
    enum Type{
        PAWN,
        ROOK,
        KNIGHT,
        BISHOP,
        QUEEN,
        KING
    }
}

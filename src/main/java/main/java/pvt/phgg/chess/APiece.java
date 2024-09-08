package main.java.pvt.phgg.chess;

import java.awt.Color;
import java.awt.image.BufferedImage;

abstract class APiece {
    final Color color;
    static final String ROOT = "src/main/resources";

    public APiece(Color color) {
        this.color = color;
    }

    public abstract BufferedImage getImage();
    public abstract Type getType();
    public abstract Color getColor();
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

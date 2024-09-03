package main.java.pvt.phgg.chess;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Pawn extends APiece{
    public Pawn(Color color) {
        super(color);
    }

    @Override
    public BufferedImage getImage() {
        try {
            return ImageIO.read(new File(ROOT+"/images/pawn.png"));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Type getType() {
        return Type.PAWN;
    }

    @Override
    public Color getColor() {
        return null;
    }
}

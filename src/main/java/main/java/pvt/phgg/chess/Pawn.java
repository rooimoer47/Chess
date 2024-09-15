package main.java.pvt.phgg.chess;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Pawn extends APiece{
    public Pawn(boolean white) {
        super(white);
    }

    @Override
    public BufferedImage getImage() {
        try {
            if (this.isWhite()) {
                return ImageIO.read(new File(ROOT+"/images/pawn_white.png"));
            }
            else {
                return ImageIO.read(new File(ROOT+"/images/pawn_black.png"));
            }

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
    public boolean isWhite() {
        return this.white;
    }
}

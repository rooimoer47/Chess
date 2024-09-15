package main.java.pvt.phgg.chess;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Pawn extends APiece{
    public Pawn(Position position, boolean white) {
        super(position, white);
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
    public boolean isWhite() {
        return this.white;
    }

    @Override
    public List<Position> getValidPositions(APiece [][] board) {
        List<Position> moves = new ArrayList<>();
        Position newPos = getCurrentPosition();

        if (isWhite()) {
            newPos.incRow();
        } else {
            newPos.decRow();
        }
//        TODO take, extra forward, promote, en passant

        if (isOnBoard(newPos) && !isOccupied(board, newPos)) {
            moves.add(newPos);
        }

        return moves;
    }
}

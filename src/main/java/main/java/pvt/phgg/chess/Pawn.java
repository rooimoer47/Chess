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
                if (this.isSelected()) {
                    return ImageIO.read(new File(ROOT+"/images/pawn_white_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/pawn_white.png"));
                }
            }
            else {
                if (this.isSelected()) {
                    return ImageIO.read(new File(ROOT+"/images/pawn_black_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/pawn_black.png"));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<Position> getValidPositions(APiece [][] board) {
        List<Position> moves = new ArrayList<>();
        Position newPos = new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol());
        Position takePos = new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol());

        // moving
        if (isWhite()) {
            newPos.incRow();
            takePos.incRow();
        } else {
            newPos.decRow();
            takePos.decRow();
        }
//        TODO promote, en passant

        if (Board.isOnBoard(newPos) && !Board.isOccupied(newPos)) {
            moves.add(new Position(newPos.getRow(), newPos.getCol()));
        }

        // initial extra jump
        if (isOriginalPosition()) {
            if (isWhite()) {
                newPos.incRow();
            } else {
                newPos.decRow();
            }

            if (Board.isOnBoard(newPos) && !Board.isOccupied(newPos)) {
                moves.add(new Position(newPos.getRow(), newPos.getCol()));
            }
        }

        // take
        takePos.incCol();
        if (Board.isOnBoard(takePos) && Board.isOccupied(takePos) && board[takePos.getRow()][takePos.getCol()].isWhite() != this.isWhite()) {
            moves.add((new Position(takePos.getRow(), takePos.getCol())));
        }
        takePos.incCol(-2);
        if (Board.isOnBoard(takePos) && Board.isOccupied(takePos) && board[takePos.getRow()][takePos.getCol()].isWhite() != this.isWhite()) {
            moves.add((new Position(takePos.getRow(), takePos.getCol())));
        }

        return moves;
    }
}

package main.java.pvt.phgg.chess;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Bishop extends APiece{
    public Bishop(Position position, boolean white) {
        super(position, white);
    }

    @Override
    public BufferedImage getImage() {
        try {
            if (this.isWhite()) {
                if (this.isSelected()) {
                    return ImageIO.read(new File(ROOT+"/images/bishop_white_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/bishop_white.png"));
                }
            }
            else {
                if (this.isSelected()) {
                    return ImageIO.read(new File(ROOT+"/images/bishop_black_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/bishop_black.png"));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<Position> getValidPositions(APiece[][] board) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] direction : directions) {
            Position newPos = new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol());
            newPos.incRow(direction[0]);
            newPos.incCol(direction[1]);

            while (Board.isOnBoard(newPos)) {
                if (Board.isOccupied(newPos)) {
                    if (board[newPos.getRow()][newPos.getCol()].isWhite() != this.isWhite()) {
                        moves.add(new Position(newPos.getRow(), newPos.getCol()));
                    }
                    break; // can't move past an occupied square
                }
                moves.add(new Position(newPos.getRow(), newPos.getCol()));
                newPos.incRow(direction[0]);
                newPos.incCol(direction[1]);
            }
        }

        return moves;
    }
}

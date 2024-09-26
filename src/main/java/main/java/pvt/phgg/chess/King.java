package main.java.pvt.phgg.chess;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class King extends APiece{
    public King(Position position, boolean white) {
        super(position, white);
    }

    @Override
    public BufferedImage getImage() {
        try {
            if (this.isWhite()) {
                if (this.isSelected()) {
                    return ImageIO.read(new File(ROOT+"/images/king_white_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/king_white.png"));
                }
            }
            else {
                if (this.isSelected()) {
                    return ImageIO.read(new File(ROOT+"/images/king_black_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/king_black.png"));
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
        int[][] directions = {{1, 1}, {1, 0}, {1, -1}, {0, 1}, {0, -1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int[] direction : directions) {
            Position newPos = new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol());
            newPos.incRow(direction[0]);
            newPos.incCol(direction[1]);

            if (Board.isOnBoard(newPos)) {
                if (Board.isOccupied(newPos) && (board[newPos.getRow()][newPos.getCol()].isWhite() != this.isWhite())) {
                    moves.add(new Position(newPos.getRow(), newPos.getCol()));
                } else if (!Board.isOccupied(newPos)) {
                    moves.add(new Position(newPos.getRow(), newPos.getCol()));
                }
            }
        }

        return moves;
    }
}

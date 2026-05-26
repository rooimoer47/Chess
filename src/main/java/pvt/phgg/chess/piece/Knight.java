package pvt.phgg.chess.piece;

import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Knight extends APiece{
    public Knight(Position position, boolean white) {
        super(position, white);
    }

    @Override
    public BufferedImage getImage(boolean selected) {
        try {
            if (this.isWhite()) {
                if (selected) {
                    return ImageIO.read(new File(ROOT+"/images/knight_white_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/knight_white.png"));
                }
            }
            else {
                if (selected) {
                    return ImageIO.read(new File(ROOT+"/images/knight_black_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/knight_black.png"));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public PieceType getPieceType() {
        return PieceType.KNIGHT;
    }

    @Override
    public List<Position> getValidPositions(APiece[][] board, BoardState boardState) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, 2}, {-2, 1}, {-2, -1}, {-1, -2}};
        for (int[] direction : directions) {
            Position newPos = getCurrentPosition().withRowOffset(direction[0]).withColOffset(direction[1]);

            if (boardState.isOnBoard(newPos)) {
                if (boardState.isOccupied(board, newPos) && (board[newPos.getRow()][newPos.getCol()].isWhite() != this.isWhite())) {
                    moves.add(new Position(newPos.getRow(), newPos.getCol()));
                } else if (!boardState.isOccupied(board, newPos)) {
                    moves.add(new Position(newPos.getRow(), newPos.getCol()));
                }
            }
        }

        return moves;
    }
}

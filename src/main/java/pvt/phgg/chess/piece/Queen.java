package pvt.phgg.chess.piece;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Queen extends APiece{

    private static final Logger LOGGER = LoggerFactory.getLogger(Queen.class);
    public Queen(Position position, boolean white) {
        super(position, white);
    }

    @Override
    public BufferedImage getImage(boolean selected) {
        try {
            if (this.isWhite()) {
                if (selected) {
                    return ImageIO.read(new File(ROOT+"/images/queen_white_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/queen_white.png"));
                }
            }
            else {
                if (selected) {
                    return ImageIO.read(new File(ROOT+"/images/queen_black_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/queen_black.png"));
                }
            }

        } catch (IOException e) {
            LOGGER.error("Failed to load queen image", e);
            return null;
        }
    }

    @Override
    public PieceType getPieceType() {
        return PieceType.QUEEN;
    }

    @Override
    public List<Position> getValidPositions(APiece[][] board, BoardState boardState) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{1, 1}, {1, 0}, {1, -1}, {0, 1}, {0, -1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int[] direction : directions) {
            Position newPos = getCurrentPosition().withRowOffset(direction[0]).withColOffset(direction[1]);

            while (boardState.isOnBoard(newPos)) {
                if (boardState.isOccupied(board, newPos)) {
                    if (board[newPos.getRow()][newPos.getCol()].isWhite() != this.isWhite()) {
                        moves.add(new Position(newPos.getRow(), newPos.getCol()));
                    }
                    break; // can't move past an occupied square
                }
                moves.add(new Position(newPos.getRow(), newPos.getCol()));
                newPos = newPos.withRowOffset(direction[0]).withColOffset(direction[1]);
            }
        }

        return moves;
    }

    @Override
    public APiece copy() {
        return copyStateTo(new Queen(new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol()), isWhite()));
    }
}
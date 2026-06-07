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

public class Rook extends APiece{

    private static final Logger LOGGER = LoggerFactory.getLogger(Rook.class);
    public Rook(Position position, boolean white) {
        super(position, white);
    }

    @Override
    public BufferedImage getImage(boolean selected) {
        try {
            if (this.isWhite()) {
                if (selected) {
                    return ImageIO.read(new File(ROOT+"/images/rook_white_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/rook_white.png"));
                }
            }
            else {
                if (selected) {
                    return ImageIO.read(new File(ROOT+"/images/rook_black_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/rook_black.png"));
                }
            }

        } catch (IOException e) {
            LOGGER.error("Failed to load rook image", e);
            return null;
        }
    }

    @Override
    public List<Position> getValidPositions(APiece[][] board, BoardState boardState) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
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
    public PieceType getPieceType() {
        return PieceType.ROOK;
    }

    @Override
    public boolean isRook() {
        return true;
    }

    @Override
    public APiece copy() {
        return copyStateTo(new Rook(new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol()), isWhite()));
    }
}
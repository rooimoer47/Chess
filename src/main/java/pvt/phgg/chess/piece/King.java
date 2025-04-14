package pvt.phgg.chess.piece;

import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

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

    public List<Position> getKingMovements(APiece[][] board, BoardState boardState) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{1, 1}, {1, 0}, {1, -1}, {0, 1}, {0, -1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int[] direction : directions) {
            Position newPos = new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol());
            newPos.incRow(direction[0]);
            newPos.incCol(direction[1]);

            if (boardState.isOnBoard(newPos)) {
                if (boardState.isOccupied(board, newPos) &&
                        (board[newPos.getRow()][newPos.getCol()].isWhite() != this.isWhite())) {
                    moves.add(new Position(newPos.getRow(), newPos.getCol()));
                } else if (!boardState.isOccupied(board, newPos)) {
                    moves.add(new Position(newPos.getRow(), newPos.getCol()));
                }
            }
        }
        return moves;
    }

    @Override
    public List<Position> getValidPositions(APiece[][] board, BoardState boardState) {
        List<Position> moves = getKingMovements(board, boardState);

        // castle
        if (this.isOriginalPosition()) {
            int kingRow = this.getCurrentPosition().getRow();
            int kingCol = this.getCurrentPosition().getCol();
            Position rookPos = new Position(kingRow, 0);
            List<Position> betweenSquares = new ArrayList<>();
            List<Position> kingSquares = List.of(
                    this.getCurrentPosition(),
                    new Position(kingRow, kingCol-1),
                    new Position(kingRow, kingCol-2));
            for (int col=1; col<kingCol; col++) {
                betweenSquares.add(new Position(kingRow, col));
            }
            if (boardState.isOccupied(board, rookPos) &&
                    board[rookPos.getRow()][rookPos.getCol()].isRook() &&
                    board[rookPos.getRow()][rookPos.getCol()].isOriginalPosition() &&
                    boardState.isUnOccupied(board, betweenSquares) &&
                    boardState.arePositionsSafe(board, kingSquares , this.isWhite())) {
                moves.add(new Position(kingRow, kingCol-2, Position.SpecialMove.CASTLE));
            }

            rookPos = new Position(kingRow, 7);
            kingSquares = List.of(
                    this.getCurrentPosition(),
                    new Position(kingRow, kingCol+1),
                    new Position(kingRow, kingCol+2));
            betweenSquares.clear();
            for (int col=6; col>kingCol; col--) {
                betweenSquares.add(new Position(kingRow, col));
            }
            if (boardState.isOccupied(board, rookPos) &&
                    board[rookPos.getRow()][rookPos.getCol()].isRook() &&
                    board[rookPos.getRow()][rookPos.getCol()].isOriginalPosition() &&
                    boardState.isUnOccupied(board, betweenSquares) &&
                    boardState.arePositionsSafe(board, kingSquares , this.isWhite())) {
                moves.add(new Position(kingRow, kingCol+2, Position.SpecialMove.CASTLE));
            }
        }

        return moves;
    }

    @Override
    public boolean isKing() {
        return true;
    }

}

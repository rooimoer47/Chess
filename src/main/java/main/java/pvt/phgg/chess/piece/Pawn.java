package main.java.pvt.phgg.chess.piece;

import main.java.pvt.phgg.chess.BoardState;
import main.java.pvt.phgg.chess.Position;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Pawn extends APiece{
    private boolean jumped = false;

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
    public List<Position> getValidPositions(APiece [][] board, BoardState boardState) {
        List<Position> moves = new ArrayList<>();
        Position newPos = new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol());
        Position takePos = new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol());
        Position enPassantPos = new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol());

        // moving
        if (isWhite()) {
            newPos.incRow();
            takePos.incRow();
        } else {
            newPos.decRow();
            takePos.decRow();
        }

        if (boardState.isOnBoard(newPos) && !boardState.isOccupied(board, newPos)) {
            moves.add(new Position(newPos.getRow(), newPos.getCol()));
        }

        // initial extra jump
        if (isOriginalPosition()) {
            if (isWhite()) {
                newPos.incRow();
            } else {
                newPos.decRow();
            }

            if (boardState.isOnBoard(newPos) && !boardState.isOccupied(board, newPos)) {
                moves.add(new Position(newPos.getRow(), newPos.getCol()));
            }
        }

        // take
        takePos.incCol();
        if (boardState.isOnBoard(takePos) && boardState.isOccupied(board, takePos) && board[takePos.getRow()][takePos.getCol()].isWhite() != this.isWhite()) {
            moves.add((new Position(takePos.getRow(), takePos.getCol())));
        }
        takePos.incCol(-2);
        if (boardState.isOnBoard(takePos) && boardState.isOccupied(board, takePos) && board[takePos.getRow()][takePos.getCol()].isWhite() != this.isWhite()) {
            moves.add((new Position(takePos.getRow(), takePos.getCol())));
        }

        // en passant
        enPassantPos.incCol();
        if (boardState.isOnBoard(enPassantPos) &&
            boardState.isOccupied(board, enPassantPos) &&
            board[enPassantPos.getRow()][enPassantPos.getCol()].isWhite() != this.isWhite() &&
            board[enPassantPos.getRow()][enPassantPos.getCol()].isPawn() &&
            ((Pawn)board[enPassantPos.getRow()][enPassantPos.getCol()]).isJumped()) {
            Position move = new Position(enPassantPos.getRow(), enPassantPos.getCol(), Position.SpecialMove.ENPASSANT);
            if (isWhite()) {
                move.incRow();
            } else {
                move.decRow();
            }
            moves.add(move);
        }
        enPassantPos.incCol(-2);
        if (boardState.isOnBoard(enPassantPos) &&
                boardState.isOccupied(board, enPassantPos) &&
                board[enPassantPos.getRow()][enPassantPos.getCol()].isWhite() != this.isWhite() &&
                board[enPassantPos.getRow()][enPassantPos.getCol()].isPawn() &&
                ((Pawn)board[enPassantPos.getRow()][enPassantPos.getCol()]).isJumped()) {
            Position move = new Position(enPassantPos.getRow(), enPassantPos.getCol(), Position.SpecialMove.ENPASSANT);
            if (isWhite()) {
                move.incRow();
            } else {
                move.decRow();
            }
            moves.add(move);
        }

        return moves;
    }

    public boolean isPawn() {
        return true;
    }

    public void setJumped() {
        jumped = true;
    }

    public void unsetJumped() {
        jumped = false;
    }

    public boolean isJumped() {
        return jumped;
    }
}

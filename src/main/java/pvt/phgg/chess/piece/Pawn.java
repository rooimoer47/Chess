package pvt.phgg.chess.piece;

import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

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
    public BufferedImage getImage(boolean selected) {
        try {
            if (this.isWhite()) {
                if (selected) {
                    return ImageIO.read(new File(ROOT+"/images/pawn_white_selected.png"));
                }
                else {
                    return ImageIO.read(new File(ROOT+"/images/pawn_white.png"));
                }
            }
            else {
                if (selected) {
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
        Position currentPos = getCurrentPosition();

        int rowDirection = isWhite() ? 1 : -1;
        Position forwardPos = currentPos.withRowOffset(rowDirection);

        if (boardState.isOnBoard(forwardPos) && !boardState.isOccupied(board, forwardPos)) {
            moves.add(new Position(forwardPos.getRow(), forwardPos.getCol()));
        }

        // initial extra jump
        if (isOriginalPosition()) {
            Position doubleForwardPos = forwardPos.withRowOffset(rowDirection);
            if (boardState.isOnBoard(doubleForwardPos) && !boardState.isOccupied(board, doubleForwardPos)) {
                moves.add(new Position(doubleForwardPos.getRow(), doubleForwardPos.getCol()));
            }
        }

        // take
        Position rightCapturePos = currentPos.withRowOffset(rowDirection).withColOffset(1);
        if (boardState.isOnBoard(rightCapturePos) &&
            boardState.isOccupied(board, rightCapturePos) &&
            board[rightCapturePos.getRow()][rightCapturePos.getCol()].isWhite() != this.isWhite()) {
            moves.add((new Position(rightCapturePos.getRow(), rightCapturePos.getCol())));
        }
        Position leftCapturePos = currentPos.withRowOffset(rowDirection).withColOffset(-1);
        if (boardState.isOnBoard(leftCapturePos) &&
            boardState.isOccupied(board, leftCapturePos) &&
            board[leftCapturePos.getRow()][leftCapturePos.getCol()].isWhite() != this.isWhite()) {
            moves.add((new Position(leftCapturePos.getRow(), leftCapturePos.getCol())));
        }

        // en passant
        Position rightEnPassantTarget = currentPos.withColOffset(1);
        if (boardState.isOnBoard(rightEnPassantTarget) &&
            boardState.isOccupied(board, rightEnPassantTarget) &&
            board[rightEnPassantTarget.getRow()][rightEnPassantTarget.getCol()].isWhite() != this.isWhite() &&
            board[rightEnPassantTarget.getRow()][rightEnPassantTarget.getCol()].isPawn() &&
            ((Pawn)board[rightEnPassantTarget.getRow()][rightEnPassantTarget.getCol()]).isJumped()) {
            Position move = new Position(
                rightEnPassantTarget.getRow() + rowDirection,
                rightEnPassantTarget.getCol(),
                Position.SpecialMove.ENPASSANT);
            moves.add(move);
        }

        Position leftEnPassantTarget = currentPos.withColOffset(-1);
        if (boardState.isOnBoard(leftEnPassantTarget) &&
            boardState.isOccupied(board, leftEnPassantTarget) &&
            board[leftEnPassantTarget.getRow()][leftEnPassantTarget.getCol()].isWhite() != this.isWhite() &&
            board[leftEnPassantTarget.getRow()][leftEnPassantTarget.getCol()].isPawn() &&
            ((Pawn)board[leftEnPassantTarget.getRow()][leftEnPassantTarget.getCol()]).isJumped()) {
            Position move = new Position(
                leftEnPassantTarget.getRow() + rowDirection,
                leftEnPassantTarget.getCol(),
                Position.SpecialMove.ENPASSANT);
            moves.add(move);
        }

        return moves;
    }

    @Override
    public PieceType getPieceType() {
        return PieceType.PAWN;
    }

    @Override
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

    @Override
    public APiece copy() {
        Pawn p = (Pawn) copyStateTo(new Pawn(new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol()), isWhite()));
        if (jumped) p.setJumped();
        return p;
    }
}

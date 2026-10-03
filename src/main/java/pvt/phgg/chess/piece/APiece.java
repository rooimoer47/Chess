package pvt.phgg.chess.piece;

import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

import java.util.ArrayList;
import java.util.List;

public abstract class APiece {

    private Position pos;
    private final boolean white;
    private boolean originalPosition = true;
    protected APiece(Position pos) {
        this.pos = pos;
        this.white = false;
    }

    protected APiece(Position position, boolean white) {
        this.pos = position;
        this.white = white;
    }

    public abstract APiece copy();

    public abstract PieceType getPieceType();

    public boolean isPositionOccupied() {
        return true;
    }

    public abstract List<Position> getValidPositions(APiece[][] board, BoardState boardState);

    public Position getCurrentPosition() {
        return this.pos;
    }

    public void setCurrentPosition(Position position) {
        this.pos = position;
    }

    public boolean isWhite() {
        return white;
    }

    public boolean isOriginalPosition() {
        return originalPosition;
    }

    public void moved() {
        originalPosition = false;
    }

    public boolean isKing() {
        return false;
    }

    public boolean isRook() {
        return false;
    }

    public boolean isPawn() {
        return false;
    }

    public List<Position> getLegalPositions(APiece[][] board, BoardState boardState) {
        List<Position> validPositions = this.getValidPositions(board, boardState);
        List<Position> legalPositions = new ArrayList<>();

        for (Position candidate : validPositions) {
            APiece[][] tempBoard = boardState.deepCopy(board);
            tempBoard[candidate.getRow()][candidate.getCol()] = this.copy();
            tempBoard[this.pos.getRow()][this.pos.getCol()] = new EmptySquare(this.pos);
            tempBoard[candidate.getRow()][candidate.getCol()].setCurrentPosition(candidate);
            if (candidate.isEnPassant()) {
                // The captured pawn sits beside the capturing one, not on the target square. Leaving
                // it on the board would block checks it no longer blocks and keep a checking pawn alive.
                Position captured = new Position(this.pos.getRow(), candidate.getCol());
                tempBoard[captured.getRow()][captured.getCol()] = new EmptySquare(captured);
            }
            if (!boardState.isInCheck(tempBoard, this.isWhite())) {
                legalPositions.add(candidate);
            }
        }
        return legalPositions;
    }

    public List<Position> getLegalPositions(APiece[][] board) {
        return getLegalPositions(board, new BoardState());
    }

    protected APiece copyStateTo(APiece target) {
        if (!isOriginalPosition()) target.moved();
        return target;
    }
}

package pvt.phgg.chess.piece;

import pvt.phgg.chess.BoardState;
import pvt.phgg.chess.Position;

import java.util.ArrayList;
import java.util.List;

public class King extends APiece{

    // Home files of the two rooks this king may castle with. Defaults match standard chess
    // (queenside rook on file a=0, kingside rook on file h=7); Chess960 sets them explicitly.
    private int queensideRookFile = 0;
    private int kingsideRookFile = 7;

    public King(Position position, boolean white) {
        super(position, white);
    }

    public void setRookFiles(int queensideRookFile, int kingsideRookFile) {
        this.queensideRookFile = queensideRookFile;
        this.kingsideRookFile = kingsideRookFile;
    }

    public int getQueensideRookFile() {
        return queensideRookFile;
    }

    public int getKingsideRookFile() {
        return kingsideRookFile;
    }

    public List<Position> getKingMovements(APiece[][] board, BoardState boardState) {
        List<Position> moves = new ArrayList<>();
        int[][] directions = {{1, 1}, {1, 0}, {1, -1}, {0, 1}, {0, -1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int[] direction : directions) {
            Position newPos = getCurrentPosition().withRowOffset(direction[0]).withColOffset(direction[1]);

            if (boardState.isOnBoard(newPos) &&
                    (!boardState.isOccupied(board, newPos) ||
                     board[newPos.getRow()][newPos.getCol()].isWhite() != this.isWhite())) {
                moves.add(new Position(newPos.getRow(), newPos.getCol()));
            }
        }
        return moves;
    }

    @Override
    public List<Position> getValidPositions(APiece[][] board, BoardState boardState) {
        List<Position> moves = getKingMovements(board, boardState);

        // Castling. Under FIDE Chess960 rules the king always lands on the c-file (queenside)
        // or g-file (kingside), and the rook on the d-file or f-file, regardless of where either
        // started. Standard chess (king on e, rooks on a/h) is just the special case where the
        // home files happen to be 4/0/7.
        if (this.isOriginalPosition()) {
            addCastle(board, boardState, moves, queensideRookFile, 2, 3); // king -> c-file, rook -> d-file
            addCastle(board, boardState, moves, kingsideRookFile, 6, 5);  // king -> g-file, rook -> f-file
        }

        return moves;
    }

    private void addCastle(APiece[][] board, BoardState boardState, List<Position> moves,
                           int rookStartFile, int kingDestCol, int rookDestCol) {
        int row = getCurrentPosition().getRow();
        int kingCol = getCurrentPosition().getCol();

        if (rookStartFile < 0 || rookStartFile > 7) {
            return;
        }
        APiece rook = board[row][rookStartFile];
        if (!rook.isPositionOccupied() || !rook.isRook() || !rook.isOriginalPosition()) {
            return;
        }

        // Every square the king and the rook travel across (including their destinations) must be
        // vacant, except for the king's and the castling rook's own starting squares — in Chess960
        // the king and rook can start adjacent or already occupy a destination square.
        if (isPathBlocked(board, row, kingCol, kingDestCol, kingCol, rookStartFile)
                || isPathBlocked(board, row, rookStartFile, rookDestCol, kingCol, rookStartFile)) {
            return;
        }

        // The king may not start in, move through, or land on an attacked square.
        List<Position> kingPath = new ArrayList<>();
        for (int col = Math.min(kingCol, kingDestCol); col <= Math.max(kingCol, kingDestCol); col++) {
            kingPath.add(new Position(row, col));
        }
        if (!boardState.arePositionsSafe(board, kingPath, this.isWhite())) {
            return;
        }

        moves.add(new Position(row, kingDestCol, Position.SpecialMove.CASTLE));
    }

    private boolean isPathBlocked(APiece[][] board, int row, int from, int to, int kingCol, int rookStartFile) {
        for (int col = Math.min(from, to); col <= Math.max(from, to); col++) {
            if (col == kingCol || col == rookStartFile) {
                continue;
            }
            if (board[row][col].isPositionOccupied()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PieceType getPieceType() {
        return PieceType.KING;
    }

    @Override
    public boolean isKing() {
        return true;
    }

    @Override
    public APiece copy() {
        King clone = new King(new Position(getCurrentPosition().getRow(), getCurrentPosition().getCol()), isWhite());
        clone.setRookFiles(queensideRookFile, kingsideRookFile);
        return copyStateTo(clone);
    }
}

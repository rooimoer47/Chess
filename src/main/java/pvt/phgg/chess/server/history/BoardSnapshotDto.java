package pvt.phgg.chess.server.history;

import pvt.phgg.chess.server.dto.LastMoveDto;
import pvt.phgg.chess.server.dto.PieceDto;

import java.util.Arrays;
import java.util.Objects;

public record BoardSnapshotDto(int moveNumber, PieceDto[][] board, LastMoveDto lastMove) {

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof BoardSnapshotDto(var oMoveNumber, var oBoard, var oLastMove)
                && moveNumber == oMoveNumber
                && Arrays.deepEquals(board, oBoard)
                && Objects.equals(lastMove, oLastMove));
    }

    @Override
    public int hashCode() {
        return Objects.hash(moveNumber, Arrays.deepHashCode(board), lastMove);
    }

    @Override
    public String toString() {
        return "BoardSnapshotDto[moveNumber=" + moveNumber
                + ", board=" + Arrays.deepToString(board)
                + ", lastMove=" + lastMove + "]";
    }
}

package pvt.phgg.chess.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerMessage(
        String type,
        String color,
        PieceDto[][] board,
        String currentTurn,
        String status,
        List<LegalMove> legalMoves,
        LastMoveDto lastMove,
        List<String> capturedByWhite,
        List<String> capturedByBlack,
        Integer promotionRow,
        Integer promotionCol,
        String message
) {

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ServerMessage(
                var oType, var oColor, var oBoard, var oCurrentTurn, var oStatus,
                var oLegalMoves, var oLastMove, var oCapturedByWhite, var oCapturedByBlack,
                var oPromotionRow, var oPromotionCol, var oMessage)
                && Objects.equals(type, oType)
                && Objects.equals(color, oColor)
                && Arrays.deepEquals(board, oBoard)
                && Objects.equals(currentTurn, oCurrentTurn)
                && Objects.equals(status, oStatus)
                && Objects.equals(legalMoves, oLegalMoves)
                && Objects.equals(lastMove, oLastMove)
                && Objects.equals(capturedByWhite, oCapturedByWhite)
                && Objects.equals(capturedByBlack, oCapturedByBlack)
                && Objects.equals(promotionRow, oPromotionRow)
                && Objects.equals(promotionCol, oPromotionCol)
                && Objects.equals(message, oMessage));
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, color, Arrays.deepHashCode(board), currentTurn, status,
                legalMoves, lastMove, capturedByWhite, capturedByBlack,
                promotionRow, promotionCol, message);
    }

    @Override
    @NonNull
    public String toString() {
        return "ServerMessage[type=" + type
                + ", color=" + color
                + ", board=" + Arrays.deepToString(board)
                + ", currentTurn=" + currentTurn
                + ", status=" + status
                + ", legalMoves=" + legalMoves
                + ", lastMove=" + lastMove
                + ", capturedByWhite=" + capturedByWhite
                + ", capturedByBlack=" + capturedByBlack
                + ", promotionRow=" + promotionRow
                + ", promotionCol=" + promotionCol
                + ", message=" + message + "]";
    }

    public static ServerMessage waiting(String color) {
        return new Builder("WAITING").color(color).build();
    }

    public static ServerMessage boardUpdate(PieceDto[][] board, String currentTurn, String status,
                                            List<LegalMove> legalMoves, LastMoveDto lastMove,
                                            List<String> capturedByWhite, List<String> capturedByBlack) {
        return new Builder("BOARD_UPDATE")
                .board(board).currentTurn(currentTurn).status(status).legalMoves(legalMoves).lastMove(lastMove)
                .capturedByWhite(capturedByWhite).capturedByBlack(capturedByBlack)
                .build();
    }

    public static ServerMessage promotionNeeded(int row, int col) {
        return new Builder("PROMOTION_NEEDED").promotionRow(row).promotionCol(col).build();
    }

    public static ServerMessage drawOffered() {
        return new Builder("DRAW_OFFERED").build();
    }

    public static ServerMessage drawDeclined() {
        return new Builder("DRAW_DECLINED").build();
    }

    public static ServerMessage opponentDisconnected() {
        return new Builder("OPPONENT_DISCONNECTED").build();
    }

    public static ServerMessage error(String message) {
        return new Builder("ERROR").message(message).build();
    }

    private static class Builder {
        private final String type;
        private String color;
        private PieceDto[][] board;
        private String currentTurn;
        private String status;
        private List<LegalMove> legalMoves;
        private LastMoveDto lastMove;
        private List<String> capturedByWhite;
        private List<String> capturedByBlack;
        private Integer promotionRow;
        private Integer promotionCol;
        private String message;

        Builder(String type) { this.type = type; }

        Builder color(String v)                  { this.color = v; return this; }
        Builder board(PieceDto[][] v)            { this.board = v; return this; }
        Builder currentTurn(String v)            { this.currentTurn = v; return this; }
        Builder status(String v)                 { this.status = v; return this; }
        Builder legalMoves(List<LegalMove> v)    { this.legalMoves = v; return this; }
        Builder lastMove(LastMoveDto v)          { this.lastMove = v; return this; }
        Builder capturedByWhite(List<String> v)  { this.capturedByWhite = v; return this; }
        Builder capturedByBlack(List<String> v)  { this.capturedByBlack = v; return this; }
        Builder promotionRow(Integer v)          { this.promotionRow = v; return this; }
        Builder promotionCol(Integer v)          { this.promotionCol = v; return this; }
        Builder message(String v)                { this.message = v; return this; }

        ServerMessage build() {
            return new ServerMessage(type, color, board, currentTurn, status,
                    legalMoves, lastMove, capturedByWhite, capturedByBlack,
                    promotionRow, promotionCol, message);
        }
    }
}

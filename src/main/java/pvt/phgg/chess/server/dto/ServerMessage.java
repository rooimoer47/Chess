package pvt.phgg.chess.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerMessage {

    private final String type;
    private final String color;
    private final PieceDto[][] board;
    private final String currentTurn;
    private final String status;
    private final List<LegalMove> legalMoves;
    private final LastMoveDto lastMove;
    private final List<String> capturedByWhite;
    private final List<String> capturedByBlack;
    private final Integer promotionRow;
    private final Integer promotionCol;
    private final String message;

    private ServerMessage(Builder b) {
        this.type = b.type;
        this.color = b.color;
        this.board = b.board;
        this.currentTurn = b.currentTurn;
        this.status = b.status;
        this.legalMoves = b.legalMoves;
        this.lastMove = b.lastMove;
        this.capturedByWhite = b.capturedByWhite;
        this.capturedByBlack = b.capturedByBlack;
        this.promotionRow = b.promotionRow;
        this.promotionCol = b.promotionCol;
        this.message = b.message;
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

    public String getType()             { return type; }
    public String getColor()            { return color; }
    public PieceDto[][] getBoard()      { return board; }
    public String getCurrentTurn()      { return currentTurn; }
    public String getStatus()           { return status; }
    public List<LegalMove> getLegalMoves() { return legalMoves; }
    public Integer getPromotionRow()    { return promotionRow; }
    public Integer getPromotionCol()    { return promotionCol; }
    public String getMessage()             { return message; }
    public LastMoveDto getLastMove()       { return lastMove; }
    public List<String> getCapturedByWhite() { return capturedByWhite; }
    public List<String> getCapturedByBlack() { return capturedByBlack; }

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

        Builder color(String v)                   { this.color = v; return this; }
        Builder board(PieceDto[][] v)             { this.board = v; return this; }
        Builder currentTurn(String v)             { this.currentTurn = v; return this; }
        Builder status(String v)                  { this.status = v; return this; }
        Builder legalMoves(List<LegalMove> v)        { this.legalMoves = v; return this; }
        Builder lastMove(LastMoveDto v)              { this.lastMove = v; return this; }
        Builder capturedByWhite(List<String> v)      { this.capturedByWhite = v; return this; }
        Builder capturedByBlack(List<String> v)      { this.capturedByBlack = v; return this; }
        Builder promotionRow(Integer v)              { this.promotionRow = v; return this; }
        Builder promotionCol(Integer v)           { this.promotionCol = v; return this; }
        Builder message(String v)                 { this.message = v; return this; }

        ServerMessage build() { return new ServerMessage(this); }
    }
}

package pvt.phgg.chess.server.history;

public record GameMoveDto(
        int moveNumber,
        int fromRow,
        int fromCol,
        int toRow,
        int toCol,
        String promotionChoice
) {}

package pvt.phgg.chess.server.dto;

import pvt.phgg.chess.piece.APiece;

public record PieceDto(String type, String color) {

    public static PieceDto from(APiece piece) {
        if (!piece.isPositionOccupied()) {
            return null;
        }
        return new PieceDto(
                piece.getPieceType().name(),
                piece.isWhite() ? "WHITE" : "BLACK"
        );
    }
}

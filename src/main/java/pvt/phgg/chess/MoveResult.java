package pvt.phgg.chess;

public record MoveResult(Type type, boolean wasWhiteMove, boolean captureOccurred) {

    public enum Type {
        VALID,
        INVALID,
        CHECK,
        CHECKMATE,
        STALEMATE,
        DRAW,
        PROMOTION_NEEDED
    }

    public boolean isGameOver() {
        return type == Type.CHECKMATE || type == Type.STALEMATE || type == Type.DRAW;
    }

    public boolean isValid() {
        return type != Type.INVALID;
    }
}

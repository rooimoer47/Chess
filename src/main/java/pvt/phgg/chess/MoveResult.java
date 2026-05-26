package pvt.phgg.chess;

public class MoveResult {

    public enum Type {
        VALID,
        INVALID,
        CHECK,
        CHECKMATE,
        STALEMATE,
        PROMOTION_NEEDED
    }

    private final Type type;
    private final boolean wasWhiteMove;
    private final boolean captureOccurred;

    public MoveResult(Type type, boolean wasWhiteMove, boolean captureOccurred) {
        this.type = type;
        this.wasWhiteMove = wasWhiteMove;
        this.captureOccurred = captureOccurred;
    }

    public Type getType() {
        return type;
    }

    public boolean wasWhiteMove() {
        return wasWhiteMove;
    }

    public boolean isCaptureOccurred() {
        return captureOccurred;
    }

    public boolean isGameOver() {
        return type == Type.CHECKMATE || type == Type.STALEMATE;
    }

    public boolean isValid() {
        return type != Type.INVALID;
    }
}

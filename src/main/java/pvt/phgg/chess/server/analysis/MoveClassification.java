package pvt.phgg.chess.server.analysis;

public enum MoveClassification {
    BEST, GOOD, INACCURACY, MISTAKE, BLUNDER,
    // One of the two positions has no evaluation (its analysis failed).
    UNKNOWN;

    // The mover could have done clearly better.
    public boolean isError() {
        return this == INACCURACY || this == MISTAKE || this == BLUNDER;
    }
}

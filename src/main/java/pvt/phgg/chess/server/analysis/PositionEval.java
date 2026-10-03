package pvt.phgg.chess.server.analysis;

// One engine evaluation, always from White's point of view. Exactly one of evalCp and mateIn is set.
// mateIn > 0: White mates in N; < 0: Black mates in N; 0: the side to move is checkmated.
// bestUci is null when the side to move has no legal move (checkmate or stalemate).
public record PositionEval(Integer evalCp, Integer mateIn, String bestUci, String pvUci, int depth) {
}

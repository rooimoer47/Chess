package pvt.phgg.chess.server.analysis;

// Evaluates single positions. Behind an interface so tests can run the queue without Stockfish.
public interface PositionAnalyzer {

    // `fen` may omit the move counters. Throws when the engine fails or is unavailable.
    PositionEval analyze(String fen, boolean chess960) throws AnalysisException;

    // Recorded with every evaluation, e.g. "Stockfish 19".
    String engineName() throws AnalysisException;

    // False when the engine can't run here at all (e.g. no binary on a dev machine), so the worker
    // leaves jobs queued instead of burning their attempts.
    boolean isAvailable();
}

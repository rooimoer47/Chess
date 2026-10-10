package pvt.phgg.chess.server.analysis;

import java.util.List;

// Everything the LLM is told about one move. `move` is numbered like "12...Nf6"; the evaluations are
// already in words, from White's point of view; `facts` are full sentences. `opening` is a name with
// its ECO code, like "Italian Game (C50)", or null once the game is past its opening.
public record MoveContext(int ply, String move, String mover, MoveClassification classification,
                          boolean decisive, String opening, String evalBefore, String evalAfter, List<String> facts) {
}

package pvt.phgg.chess.server.analysis;

import java.util.List;

// Everything the LLM is told about one move. `move` is numbered like "12...Nf6"; the evaluations are
// already in words, from White's point of view; `facts` are full sentences. `opening` is a name with
// its ECO code, like "Italian Game (C50)", or null once the game is past its opening. `openingNote`
// is a known trap or idea that the move matches, or null; it is looked up after the facts are built.
//
// `san` and `lineMoves` are not shown to the LLM as such: the move as plain SAN, and the moves of the
// engine's lines (the opponent's best reply and the best alternative), for matching against notes.
public record MoveContext(int ply, String move, String san, String mover, MoveClassification classification,
                          boolean decisive, String opening, String openingNote, String evalBefore,
                          String evalAfter, List<String> facts, List<String> lineMoves) {

    MoveContext withOpeningNote(String note) {
        return new MoveContext(ply, move, san, mover, classification, decisive, opening, note, evalBefore,
                evalAfter, facts, lineMoves);
    }
}

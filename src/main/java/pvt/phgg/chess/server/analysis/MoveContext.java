package pvt.phgg.chess.server.analysis;

import java.util.List;

// Everything the LLM is told about one move. `move` is numbered like "12...Nf6"; the evaluations are
// already in words, from White's point of view; `facts` are full sentences. `opening` is a name with
// its ECO code, like "Italian Game (C50)", or null once the game is past its opening. `openingNote`
// is a known trap or idea that the move matches, or null; it is looked up after the facts are built.
//
// `san` and `lineMoves` are not shown to the LLM as such: the move as plain SAN, and the moves of the
// engine's lines (the opponent's best reply and the best alternative), for matching against notes.
// `motifs` are the tactics found behind a mistake; each one is also spelled out in `facts`.
//
// `summary` tells a mistake without squares or move numbers, so that the same kind of mistake reads
// the same in any game; it is what the mistake memory keeps. `history` is what that memory knows
// about the mover's earlier games, or null: a finished sentence that is added to the comment, not
// shown to the LLM. Like the note it is looked up after the facts are built.
public record MoveContext(int ply, String move, String san, String mover, MoveClassification classification,
                          boolean decisive, String opening, String openingNote, String evalBefore,
                          String evalAfter, List<String> facts, List<String> lineMoves, List<Motifs.Tag> motifs,
                          String summary, String history) {

    MoveContext withOpeningNote(String note) {
        return new MoveContext(ply, move, san, mover, classification, decisive, opening, note, evalBefore,
                evalAfter, facts, lineMoves, motifs, summary, history);
    }

    MoveContext withHistory(String sentence) {
        return new MoveContext(ply, move, san, mover, classification, decisive, opening, openingNote, evalBefore,
                evalAfter, facts, lineMoves, motifs, summary, sentence);
    }
}

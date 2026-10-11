package pvt.phgg.chess.server.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import pvt.phgg.chess.server.analysis.llm.Embedder;
import pvt.phgg.chess.server.analysis.llm.LlmException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// The hand-written notes on opening traps and ideas (openings/notes.tsv), kept with their embeddings
// in a pgvector table and looked up for the moves that get a comment.
//
// Finding a note takes two steps. The vector search shortlists the notes for the game's opening that
// lie closest to the move's facts. Closest is not the same as applicable, though: the embedding model
// reads chess notation poorly, and the LLM works a note into its comment whether it fits or not. So a
// note only goes into the prompt when its key moves confirm it: the move played is one of them, and
// the engine's lines contain another.
@Component
public class OpeningNotes {

    private static final Logger log = LoggerFactory.getLogger(OpeningNotes.class);

    // How many of the nearest notes are checked against the moves.
    private static final int SHORTLIST = 5;

    private static final RowMapper<Note> NOTE = (rs, row) ->
            new Note(rs.getString("opening"), rs.getString("moves"), rs.getString("note"));

    // `opening` is part of an opening name ("Italian Game: Two Knights Defense"), or "*" for any.
    record Note(String opening, String moves, String text) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final Embedder embedder;

    // Both only touched by the commentary worker's thread.
    private boolean synced;
    private boolean warnedUnavailable;

    public OpeningNotes(JdbcTemplate jdbcTemplate, Embedder embedder) {
        this.jdbcTemplate = jdbcTemplate;
        this.embedder = embedder;
    }

    // The same moves, each with the note that matches it, if one does. Everything is embedded in one
    // call, and only when a move is still in its opening. Never fails: without the embedding model or
    // on an error the moves come back as they were, and get their comments without notes.
    List<MoveContext> annotate(List<MoveContext> moves) {
        List<MoveContext> inOpening = moves.stream().filter(move -> move.opening() != null).toList();
        if (inOpening.isEmpty()) return moves;
        if (!embedder.prepare()) {
            if (!warnedUnavailable) {
                log.warn("Embedding model {} not available; comments go without opening notes", embedder.modelName());
                warnedUnavailable = true;
            }
            return moves;
        }
        warnedUnavailable = false;
        try {
            sync();
            List<float[]> vectors = embedder.embed(inOpening.stream().map(OpeningNotes::query).toList(),
                    Embedder.Use.QUERY);
            Map<Integer, String> notes = new HashMap<>();
            for (int i = 0; i < inOpening.size(); i++) {
                MoveContext move = inOpening.get(i);
                find(move, vectors.get(i)).ifPresent(note -> notes.put(move.ply(), note));
            }
            return moves.stream()
                    .map(move -> notes.containsKey(move.ply()) ? move.withOpeningNote(notes.get(move.ply())) : move)
                    .toList();
        } catch (LlmException | RuntimeException e) {
            log.warn("Opening notes not looked up, comments go without: {}", e.getMessage());
            return moves;
        }
    }

    // What a move is searched with: the same sentences the LLM gets.
    private static String query(MoveContext move) {
        return move.move() + ". " + String.join(" ", move.facts());
    }

    // The nearest note for the move's opening that its moves confirm.
    Optional<String> find(MoveContext move, float[] query) {
        List<Note> shortlist = jdbcTemplate.query("""
                SELECT opening, moves, note FROM opening_notes
                WHERE model = ? AND (opening = '*' OR strpos(?, opening) > 0)
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """, NOTE, embedder.modelName(), move.opening(), vector(query), SHORTLIST);
        return shortlist.stream().filter(note -> confirmedBy(note, move)).map(Note::text).findFirst();
    }

    // The move played is one of the note's key moves and the engine's lines hold another: the mistake
    // together with how it is punished, or with what should have been played.
    static boolean confirmedBy(Note note, MoveContext move) {
        Set<String> keys = Arrays.stream(note.moves().split(" ")).map(OpeningNotes::plain)
                .collect(Collectors.toCollection(HashSet::new));
        if (!keys.remove(plain(move.san()))) return false;
        return move.lineMoves().stream().map(OpeningNotes::plain).anyMatch(keys::contains);
    }

    // Whether a move gives check depends on the rest of the position, so it doesn't count.
    private static String plain(String san) {
        return san.replaceAll("[+#]$", "");
    }

    // Brings the table in line with notes.tsv, once per run: embeds the notes that are new or
    // changed, all of them after a change of embedding model, and removes the ones no longer there.
    void sync() throws LlmException {
        if (synced) return;
        List<Note> wanted = load();
        String model = embedder.modelName();
        jdbcTemplate.update("DELETE FROM opening_notes WHERE model <> ?", model);
        List<Note> stored = jdbcTemplate.query("SELECT opening, moves, note FROM opening_notes", NOTE);

        for (Note note : stored) {
            if (!wanted.contains(note)) {
                jdbcTemplate.update("DELETE FROM opening_notes WHERE opening = ? AND moves = ? AND note = ?",
                        note.opening(), note.moves(), note.text());
            }
        }
        List<Note> missing = wanted.stream().filter(note -> !stored.contains(note)).toList();
        if (!missing.isEmpty()) {
            // The key moves are embedded along with the text: facts are full of moves, and the shared
            // ones pull the right note closer.
            List<float[]> vectors = embedder.embed(missing.stream()
                    .map(note -> note.text() + " Key moves: " + note.moves() + ".").toList(), Embedder.Use.STORED);
            for (int i = 0; i < missing.size(); i++) {
                Note note = missing.get(i);
                jdbcTemplate.update("""
                        INSERT INTO opening_notes (opening, moves, note, model, embedding)
                        VALUES (?, ?, ?, ?, ?::vector)
                        """, note.opening(), note.moves(), note.text(), model, vector(vectors.get(i)));
            }
            log.info("Embedded {} opening note(s) with {}; {} in all", missing.size(), model, wanted.size());
        }
        synced = true;
    }

    static List<Note> load() {
        try (InputStream in = OpeningNotes.class.getResourceAsStream("/openings/notes.tsv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<Note> notes = new ArrayList<>();
            reader.readLine();  // the header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] columns = line.split("\t");
                if (columns.length != 3 || columns[1].isBlank()) {
                    throw new IllegalStateException("Opening note needs an opening, key moves and a text: " + line);
                }
                notes.add(new Note(columns[0], columns[1], columns[2]));
            }
            return notes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // pgvector's text form, e.g. [0.1,-0.2].
    static String vector(float[] values) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) text.append(',');
            text.append(values[i]);
        }
        return text.append(']').toString();
    }
}

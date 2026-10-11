package pvt.phgg.chess.server.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pvt.phgg.chess.GameReplay.ReplayedPosition;
import pvt.phgg.chess.server.analysis.GameAnalysisService.LoadedGame;
import pvt.phgg.chess.server.analysis.Motifs.Motif;
import pvt.phgg.chess.server.analysis.Motifs.Tag;
import pvt.phgg.chess.server.analysis.llm.Embedder;
import pvt.phgg.chess.server.analysis.llm.LlmException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Remembers each player's mistakes and blunders across games (the mistakes table), so a comment
// can say that the same thing has happened before.
//
// What counts as "the same thing" is decided by exact matches, not by similarity: the same move in
// the same position, or the same motif (Motifs). The count behind "in 3 earlier games" has to be
// right, so it is a query, and the sentence goes into the comment as it is, not through the LLM.
//
// Each mistake is also kept as a short description with an embedding, for searching the memory with
// free text. The description has no squares or move numbers in it: with them, the nearest mistakes
// are the ones from the same opening, not the ones of the same kind. Embedding waits until the
// worker is idle, because loading the embedding model pushes the chat model out of Ollama.
@Component
public class MistakeMemory {

    private static final Logger log = LoggerFactory.getLogger(MistakeMemory.class);

    private static final int EMBED_BATCH = 50;

    private final JdbcTemplate jdbcTemplate;
    private final Embedder embedder;

    // Only touched by the commentary worker's thread.
    private boolean warnedUnavailable;

    public MistakeMemory(JdbcTemplate jdbcTemplate, Embedder embedder) {
        this.jdbcTemplate = jdbcTemplate;
        this.embedder = embedder;
    }

    // Stores the game's mistakes and blunders, for each side played by a user. Safe to repeat: a
    // move already stored is brought up to date, and loses its embedding if its description changed.
    // Never fails: the comments don't depend on it.
    void record(long gameId, LoadedGame game) {
        try {
            List<ReplayedPosition> positions = game.replay().positions();
            int stored = 0;
            for (int i = 0; i + 1 < positions.size(); i++) {
                boolean white = i % 2 == 0;
                Long userId = game.playerId(white);
                MoveClassification label = MoveClassifier.classify(game.eval(i), game.eval(i + 1), white,
                        positions.get(i).playedUci());
                if (userId == null || (label != MoveClassification.MISTAKE && label != MoveClassification.BLUNDER)) continue;
                MoveContext move = MoveFacts.describe(game, i, label, false);
                jdbcTemplate.update("""
                        INSERT INTO mistakes (user_id, game_id, ply, san, epd, classification, motifs, description)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (game_id, ply) DO UPDATE
                        SET user_id = EXCLUDED.user_id, san = EXCLUDED.san, epd = EXCLUDED.epd,
                            classification = EXCLUDED.classification, motifs = EXCLUDED.motifs,
                            description = EXCLUDED.description,
                            model = CASE WHEN mistakes.description = EXCLUDED.description THEN mistakes.model END,
                            embedding = CASE WHEN mistakes.description = EXCLUDED.description THEN mistakes.embedding END
                        """, userId, gameId, move.ply(), move.san(), positions.get(i).epd(), label.name(),
                        move.motifs().stream().map(Tag::code).toArray(String[]::new), move.summary());
                stored++;
            }
            log.debug("Stored {} mistake(s) of game {}", stored, gameId);
        } catch (RuntimeException e) {
            log.warn("Mistakes of game {} not stored", gameId, e);
        }
    }

    // What the mover's earlier games say about this mistake, as a sentence to add to its comment: the
    // same move in the same position before, or the same motif. Earlier means games that ended
    // before this one.
    //
    // Only when the mover is the game's one human player. Both players read a game's review, and a
    // player's history is not for the opponent to see. Never fails.
    Optional<String> recall(long gameId, LoadedGame game, MoveContext move) {
        boolean white = move.ply() % 2 == 1;
        Long userId = game.playerId(white);
        if (userId == null || game.playerId(!white) != null || !move.classification().isError()) return Optional.empty();
        try {
            List<String> parts = new ArrayList<>();
            String epd = game.replay().positions().get(move.ply() - 1).epd();
            int repeats = earlierGames(userId, gameId, "m.epd = ? AND m.san = ?", epd, move.san());
            if (repeats > 0) {
                parts.add(move.mover() + " has played this same move in this same position in " + games(repeats) + ".");
            }
            for (Tag tag : move.motifs()) {
                int before = earlierGames(userId, gameId, "m.motifs @> ARRAY[?::text]", tag.code());
                if (before > 0) {
                    parts.add(move.mover() + " has " + did(tag) + " in " + games(before) + ".");
                    break;
                }
            }
            return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(" ", parts));
        } catch (RuntimeException e) {
            log.warn("Earlier mistakes not looked up for game {} ply {}", gameId, move.ply(), e);
            return Optional.empty();
        }
    }

    // How many of the user's earlier games hold a mistake that `condition` picks out.
    private int earlierGames(long userId, long gameId, String condition, Object... arguments) {
        List<Object> parameters = new ArrayList<>(List.of(userId, gameId));
        parameters.addAll(List.of(arguments));
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(DISTINCT m.game_id) FROM mistakes m JOIN games g ON g.id = m.game_id
                WHERE m.user_id = ? AND g.ended_at < (SELECT ended_at FROM games WHERE id = ?) AND %s
                """.formatted(condition), Integer.class, parameters.toArray());
        return count == null ? 0 : count;
    }

    private static String games(int count) {
        return count == 1 ? "1 earlier game" : count + " earlier games";
    }

    // "allowed a fork", to follow "White has".
    private static String did(Tag tag) {
        String motif = switch (tag.motif()) {
            case HANGING_PIECE -> "hanging piece";
            case FORK -> "fork";
            case PIN -> "pin";
            case BACK_RANK -> "back-rank mate";
        };
        if (tag.missed()) return "missed a " + motif;
        return tag.motif() == Motif.HANGING_PIECE ? "left a piece hanging" : "allowed a " + motif;
    }

    // Embeds the descriptions that have no embedding yet, or one from another embedding model.
    // Returns how many it embedded. Without the embedding model they simply wait. Never fails.
    int embedPending() {
        String model = embedder.modelName();
        int embedded = 0;
        try {
            while (true) {
                List<Map<String, Object>> pending = jdbcTemplate.queryForList("""
                        SELECT id, description FROM mistakes
                        WHERE embedding IS NULL OR model IS DISTINCT FROM ?
                        ORDER BY id LIMIT ?
                        """, model, EMBED_BATCH);
                if (pending.isEmpty()) break;
                if (!embedder.prepare()) {
                    if (!warnedUnavailable) {
                        log.warn("Embedding model {} not available; mistakes wait to be embedded", model);
                        warnedUnavailable = true;
                    }
                    break;
                }
                warnedUnavailable = false;
                List<float[]> vectors = embedder.embed(
                        pending.stream().map(row -> (String) row.get("description")).toList(), Embedder.Use.STORED);
                for (int i = 0; i < pending.size(); i++) {
                    jdbcTemplate.update("UPDATE mistakes SET model = ?, embedding = ?::vector WHERE id = ?",
                            model, OpeningNotes.vector(vectors.get(i)), pending.get(i).get("id"));
                }
                embedded += pending.size();
            }
        } catch (LlmException | RuntimeException e) {
            log.warn("Mistakes not embedded, will try again: {}", e.getMessage());
        }
        if (embedded > 0) {
            log.info("Embedded {} mistake(s) with {}", embedded, model);
        }
        return embedded;
    }
}

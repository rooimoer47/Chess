package pvt.phgg.chess.server.analysis;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pvt.phgg.chess.GameReplay;
import pvt.phgg.chess.GameReplay.MoveDisplay;
import pvt.phgg.chess.GameReplay.ReplayedGame;
import pvt.phgg.chess.GameReplay.ReplayedPosition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Builds a game's review for the replay viewer: per move, what was played, what the engine
// preferred, and the label. Labels are worked out here from the cached evaluations, never stored.
@Service
public class GameAnalysisService {

    // White's point of view; exactly one of the two is set. mate 0: the side to move is checkmated.
    public record Eval(Integer cp, Integer mate) {
    }

    // ply: the position this move leads to (1 = White's first move). Squares are like "e2".
    // best*/line are null/empty when the position before has no evaluation or no legal move.
    public record MoveReview(int ply, String san, String uci, String from, String to,
                             MoveClassification classification,
                             String bestSan, String bestUci, String bestFrom, String bestTo,
                             List<String> line, String comment) {
    }

    // status: NONE (never queued), ENGINE, COMMENTING, DONE or FAILED. While NONE or ENGINE only the
    // progress counts are filled in. evals is indexed by ply, null where a position has no evaluation.
    public record GameAnalysis(String status, int positionsTotal, int positionsDone,
                               List<Eval> evals, List<MoveReview> moves) {
    }

    private final JdbcTemplate jdbcTemplate;

    public GameAnalysisService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Empty when the game doesn't exist or hasn't ended: reviewing a live game would be an engine
    // assistant.
    public Optional<GameAnalysis> analysis(long gameId) {
        List<Map<String, Object>> games = jdbcTemplate.queryForList(
                "SELECT ended_at, variant, starting_position FROM games WHERE id = ?", gameId);
        if (games.isEmpty() || games.getFirst().get("ended_at") == null) {
            return Optional.empty();
        }
        Map<String, Object> game = games.getFirst();

        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM game_reviews WHERE game_id = ?", String.class, gameId);
        String status = statuses.isEmpty() ? "NONE" : statuses.getFirst();
        Map<String, Object> progress = jdbcTemplate.queryForMap("""
                SELECT count(*) AS total, count(*) FILTER (WHERE status IN ('DONE', 'FAILED')) AS done
                FROM analysis_jobs WHERE game_id = ?
                """, gameId);
        int total = ((Number) progress.get("total")).intValue();
        int done = ((Number) progress.get("done")).intValue();
        if (status.equals("NONE") || status.equals("ENGINE")) {
            return Optional.of(new GameAnalysis(status, total, done, List.of(), List.of()));
        }

        boolean chess960 = "CHESS960".equals(game.get("variant"));
        ReplayedGame replay = GameReplay.replayGame((String) game.get("starting_position"), chess960,
                AnalysisQueue.recordedMoves(jdbcTemplate, gameId));
        List<ReplayedPosition> positions = replay.positions();
        Map<String, PositionEval> cache = cachedEvals(positions, chess960);
        Map<Integer, String> comments = new HashMap<>();
        jdbcTemplate.query("SELECT ply, comment FROM move_comments WHERE game_id = ?",
                rs -> { comments.put(rs.getInt("ply"), rs.getString("comment")); }, gameId);

        List<Eval> evals = new ArrayList<>();
        for (ReplayedPosition position : positions) {
            PositionEval eval = cache.get(position.epd());
            evals.add(eval == null ? null : new Eval(eval.evalCp(), eval.mateIn()));
        }

        List<MoveReview> moves = new ArrayList<>();
        for (int i = 0; i + 1 < positions.size(); i++) {
            ReplayedPosition before = positions.get(i);
            PositionEval evalBefore = cache.get(before.epd());
            PositionEval evalAfter = cache.get(positions.get(i + 1).epd());
            boolean whiteMoved = i % 2 == 0;
            MoveDisplay played = replay.describe(i, before.playedUci());

            MoveDisplay best = null;
            List<String> line = List.of();
            if (evalBefore != null && evalBefore.bestUci() != null) {
                try {
                    best = replay.describe(i, evalBefore.bestUci());
                } catch (IllegalArgumentException e) {
                    // A cached best move that doesn't fit this position would be a bug; show no arrow.
                }
                if (evalBefore.pvUci() != null) {
                    line = replay.sanLine(i, Arrays.asList(evalBefore.pvUci().split(" ")));
                }
            }

            moves.add(new MoveReview(i + 1, before.playedSan(), before.playedUci(), played.from(), played.to(),
                    MoveClassifier.classify(evalBefore, evalAfter, whiteMoved, before.playedUci()),
                    best == null ? null : best.san(), best == null ? null : evalBefore.bestUci(),
                    best == null ? null : best.from(), best == null ? null : best.to(),
                    line, comments.get(i + 1)));
        }
        return Optional.of(new GameAnalysis(status, total, done, evals, moves));
    }

    private Map<String, PositionEval> cachedEvals(List<ReplayedPosition> positions, boolean chess960) {
        String[] epds = positions.stream().map(ReplayedPosition::epd).distinct().toArray(String[]::new);
        Map<String, PositionEval> evals = new HashMap<>();
        jdbcTemplate.query("""
                SELECT epd, eval_cp, mate_in, best_uci, pv_uci, depth FROM position_evals
                WHERE chess960 = ? AND epd = ANY (?)
                """,
                rs -> {
                    evals.put(rs.getString("epd"), new PositionEval(
                            (Integer) rs.getObject("eval_cp"), (Integer) rs.getObject("mate_in"),
                            rs.getString("best_uci"), rs.getString("pv_uci"), rs.getInt("depth")));
                },
                chess960, epds);
        return evals;
    }
}

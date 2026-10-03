package pvt.phgg.chess.server.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.GameReplay;
import pvt.phgg.chess.GameReplay.RecordedMove;
import pvt.phgg.chess.GameReplay.ReplayedPosition;
import pvt.phgg.chess.UciMoveCodec;
import pvt.phgg.chess.UciMoveCodec.UciMove;
import pvt.phgg.chess.server.PostgresIntegrationTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A whole game through the queue, the worker and real Stockfish, then classified from the cache.
 * Only runs when STOCKFISH_PATH points at the binary (application.properties reads the same variable).
 */
@PostgresIntegrationTest
@EnabledIfEnvironmentVariable(named = "STOCKFISH_PATH", matches = ".+")
class AnalysisEndToEndTest {

    // Scholar's mate: 3...Nf6?? walks into 4.Qxf7#.
    private static final String SCHOLARS_MATE = "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7";

    @Autowired JdbcTemplate jdbc;
    @Autowired AnalysisQueue queue;
    @Autowired AnalysisWorker worker;

    @Test
    void scholarsMateIsReviewed() {
        List<RecordedMove> moves = new ArrayList<>();
        Long game = jdbc.queryForObject(
                "INSERT INTO games (mode, result, winner_color, ended_at) VALUES ('HUMAN', 'CHECKMATE', 'WHITE', now()) RETURNING id",
                Long.class);
        int number = 0;
        for (String uci : SCHOLARS_MATE.split(" ")) {
            UciMove m = UciMoveCodec.decode(uci);
            moves.add(new RecordedMove(m.from(), m.to(), null));
            jdbc.update("""
                    INSERT INTO game_moves (game_id, move_number, from_row, from_col, to_row, to_col)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, game, ++number, m.from().getRow(), m.from().getCol(), m.to().getRow(), m.to().getCol());
        }

        queue.enqueueGame(game);
        for (int i = 0; i < 100 && worker.runOnce(); i++) {
            // analyse every position
        }

        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM game_reviews WHERE game_id = ?", String.class, game));

        List<ReplayedPosition> positions = GameReplay.replay(GameEngine.STANDARD_BACK_RANK, false, moves);
        List<MoveClassification> labels = new ArrayList<>();
        for (int ply = 0; ply < moves.size(); ply++) {
            labels.add(MoveClassifier.classify(eval(positions.get(ply).epd()), eval(positions.get(ply + 1).epd()),
                    ply % 2 == 0, positions.get(ply).playedUci()));
        }

        assertEquals(MoveClassification.BLUNDER, labels.get(5), "3...Nf6?? allows mate; labels " + labels);
        assertEquals(MoveClassification.BEST, labels.get(6), "4.Qxf7# delivers mate");
        assertEquals(0, eval(positions.getLast().epd()).mateIn(), "final position: Black is checkmated");
    }

    private PositionEval eval(String epd) {
        return jdbc.queryForObject("""
                SELECT eval_cp, mate_in, best_uci, pv_uci, depth FROM position_evals
                WHERE epd = ? AND chess960 = false
                """,
                (rs, rowNum) -> new PositionEval((Integer) rs.getObject("eval_cp"), (Integer) rs.getObject("mate_in"),
                        rs.getString("best_uci"), rs.getString("pv_uci"), rs.getInt("depth")),
                epd);
    }
}

package pvt.phgg.chess.server.analysis;

import org.springframework.jdbc.core.JdbcTemplate;
import pvt.phgg.chess.GameReplay.RecordedMove;
import pvt.phgg.chess.UciMoveCodec;
import pvt.phgg.chess.UciMoveCodec.UciMove;

import java.util.Arrays;
import java.util.List;

// Inserts recorded games for database tests, with moves written in UCI.
final class TestGames {

    private TestGames() {
    }

    static List<RecordedMove> moves(String uciMoves) {
        return Arrays.stream(uciMoves.split(" "))
                .map(UciMoveCodec::decode)
                .map(m -> new RecordedMove(m.from(), m.to(), m.promotion()))
                .toList();
    }

    static void clear(JdbcTemplate jdbc) {
        jdbc.execute("""
                TRUNCATE move_comments, analysis_jobs, game_reviews, position_evals,
                         game_moves, elo_history, games RESTART IDENTITY CASCADE
                """);
    }

    static long ended(JdbcTemplate jdbc, String uciMoves) {
        return insert(jdbc, uciMoves, "now()", null, null);
    }

    // endedAtSql is an SQL expression, e.g. "now()" or "NULL" for a game still in progress.
    static long insert(JdbcTemplate jdbc, String uciMoves, String endedAtSql, Long whitePlayerId, Long blackPlayerId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO games (white_player_id, black_player_id, mode, result, ended_at)
                VALUES (?, ?, 'HUMAN', 'RESIGNED', %s) RETURNING id
                """.formatted(endedAtSql), Long.class, whitePlayerId, blackPlayerId);
        int number = 0;
        for (String uci : uciMoves.isBlank() ? new String[0] : uciMoves.split(" ")) {
            UciMove m = UciMoveCodec.decode(uci);
            jdbc.update("""
                    INSERT INTO game_moves (game_id, move_number, from_row, from_col, to_row, to_col, promotion_choice)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, id, ++number, m.from().getRow(), m.from().getCol(), m.to().getRow(), m.to().getCol(),
                    m.promotion() == null ? null : m.promotion().name());
        }
        return id;
    }
}

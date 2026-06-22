package pvt.phgg.chess.server.history;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import pvt.phgg.chess.server.auth.JwtUtil;

import java.util.List;

@RestController
@RequestMapping("/api")
public class HistoryController {

    private static final String USER_GAMES_SQL = """
            SELECT
                g.id,
                CASE WHEN g.white_player_id = u.id THEN COALESCE(ub.username, 'BOT')
                     ELSE COALESCE(uw.username, 'BOT') END AS opponent,
                CASE WHEN g.white_player_id = u.id THEN 'WHITE' ELSE 'BLACK' END AS player_color,
                g.result,
                g.winner_color,
                g.mode,
                g.started_at,
                g.ended_at
            FROM games g
            JOIN users u ON u.username = ?
            LEFT JOIN users uw ON g.white_player_id = uw.id
            LEFT JOIN users ub ON g.black_player_id = ub.id
            WHERE g.white_player_id = u.id OR g.black_player_id = u.id
            ORDER BY g.started_at DESC
            """;

    private static final String GAME_MOVES_SQL = """
            SELECT move_number, from_row, from_col, to_row, to_col, promotion_choice
            FROM game_moves
            WHERE game_id = ?
            ORDER BY move_number
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JwtUtil jwtUtil;

    public HistoryController(JdbcTemplate jdbcTemplate, JwtUtil jwtUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/users/{username}/games")
    public ResponseEntity<Object> getUserGames(
            @PathVariable String username,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String tokenUsername = extractUsername(authHeader);
        if (tokenUsername == null || !tokenUsername.equals(username)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        List<GameSummaryDto> games = jdbcTemplate.query(USER_GAMES_SQL,
                (rs, rowNum) -> new GameSummaryDto(
                        rs.getLong("id"),
                        rs.getString("opponent"),
                        rs.getString("player_color"),
                        rs.getString("result"),
                        rs.getString("winner_color"),
                        rs.getString("mode"),
                        rs.getObject("started_at", java.time.OffsetDateTime.class),
                        rs.getObject("ended_at", java.time.OffsetDateTime.class)),
                username);

        return ResponseEntity.ok(games);
    }

    @GetMapping("/games/{gameId}/moves")
    public ResponseEntity<Object> getGameMoves(
            @PathVariable long gameId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (extractUsername(authHeader) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        List<GameMoveDto> moves = jdbcTemplate.query(GAME_MOVES_SQL,
                (rs, rowNum) -> new GameMoveDto(
                        rs.getInt("move_number"),
                        rs.getInt("from_row"),
                        rs.getInt("from_col"),
                        rs.getInt("to_row"),
                        rs.getInt("to_col"),
                        rs.getString("promotion_choice")),
                gameId);

        return ResponseEntity.ok(moves);
    }

    private String extractUsername(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        return jwtUtil.isValid(token) ? jwtUtil.extractUsername(token) : null;
    }
}

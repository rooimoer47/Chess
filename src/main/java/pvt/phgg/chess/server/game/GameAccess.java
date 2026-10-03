package pvt.phgg.chess.server.game;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pvt.phgg.chess.server.auth.JwtUtil;

import java.util.Optional;

// Who is asking, and may they see this game? Only the two players may.
@Component
public class GameAccess {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;

    public GameAccess(JwtUtil jwtUtil, JdbcTemplate jdbcTemplate) {
        this.jwtUtil = jwtUtil;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> username(HttpServletRequest request) {
        String token = jwtUtil.extractFromCookies(request.getCookies());
        return token != null && jwtUtil.isValid(token) ? Optional.of(jwtUtil.extractUsername(token)) : Optional.empty();
    }

    public boolean isPlayer(String username, long gameId) {
        Integer matches = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM games g JOIN users u ON u.username = ?
                WHERE g.id = ? AND (g.white_player_id = u.id OR g.black_player_id = u.id)
                """, Integer.class, username, gameId);
        return matches != null && matches > 0;
    }
}

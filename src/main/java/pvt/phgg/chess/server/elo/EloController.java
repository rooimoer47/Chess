package pvt.phgg.chess.server.elo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import pvt.phgg.chess.server.auth.JwtUtil;
import pvt.phgg.chess.server.user.AppUser;
import pvt.phgg.chess.server.user.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class EloController {

    private static final int PROVISIONAL_THRESHOLD = 30;
    private static final int MAX_HISTORY_LIMIT = 200;

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;

    public EloController(UserService userService, JwtUtil jwtUtil, JdbcTemplate jdbcTemplate) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{username:.+}/elo")
    public ResponseEntity<Object> getElo(@PathVariable String username, HttpServletRequest request) {
        if (!isAuthorised(username, request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return userService.findByUsername(username)
                .<ResponseEntity<Object>>map(u -> ResponseEntity.ok(
                        new EloSummaryDto(
                                u.getElo(), u.getGamesRated(), u.getGamesRated() < PROVISIONAL_THRESHOLD,
                                u.getElo960(), u.getGamesRated960(), u.getGamesRated960() < PROVISIONAL_THRESHOLD)))
                .orElse(ResponseEntity.notFound().build());
    }

    // elo_history carries no variant of its own — it is a property of the game
    // the entry came from, so it is joined in from games.variant (as the
    // Chess960 design anticipated). Without it the two rating tracks are
    // indistinguishable in one list, and elo_after jumps between them.
    private static final String ELO_HISTORY_SQL = """
            SELECT h.game_id, h.elo_after, h.delta, g.variant, h.recorded_at
            FROM elo_history h
            JOIN games g ON g.id = h.game_id
            WHERE h.user_id = ?
              AND (CAST(? AS TEXT) IS NULL OR g.variant = ?)
            ORDER BY h.recorded_at DESC
            LIMIT ?
            """;

    @GetMapping("/{username:.+}/elo-history")
    public ResponseEntity<Object> getEloHistory(
            @PathVariable String username,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String variant,
            HttpServletRequest request) {
        if (!isAuthorised(username, request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Long userId = userService.findByUsername(username).map(AppUser::getId).orElse(null);
        if (userId == null) {
            return ResponseEntity.notFound().build();
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_HISTORY_LIMIT));
        // variant absent → both tracks; "STANDARD"/"CHESS960" → only that one.
        String variantFilter = "STANDARD".equals(variant) || "CHESS960".equals(variant) ? variant : null;

        List<EloHistoryEntryDto> history = jdbcTemplate.query(ELO_HISTORY_SQL,
                (rs, i) -> new EloHistoryEntryDto(
                        rs.getLong("game_id"),
                        rs.getInt("elo_after"),
                        rs.getInt("delta"),
                        rs.getString("variant"),
                        rs.getObject("recorded_at", java.time.OffsetDateTime.class)),
                userId, variantFilter, variantFilter, safeLimit);
        return ResponseEntity.ok(history);
    }

    private boolean isAuthorised(String username, HttpServletRequest request) {
        String token = jwtUtil.extractFromCookies(request.getCookies());
        return token != null && jwtUtil.isValid(token) && username.equals(jwtUtil.extractUsername(token));
    }
}

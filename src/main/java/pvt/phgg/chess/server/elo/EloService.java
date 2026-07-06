package pvt.phgg.chess.server.elo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class EloService {

    private static final int K_NEW  = 40;   // fewer than 30 rated games
    private static final int K_MID  = 20;   // 30+ games, elo < 2100
    private static final int K_HIGH = 10;   // elo >= 2100

    private final JdbcTemplate jdbcTemplate;
    private final EloProperties eloProperties;

    public EloService(JdbcTemplate jdbcTemplate, EloProperties eloProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.eloProperties = eloProperties;
    }

    int kFactor(int elo, int gamesRated) {
        if (gamesRated < 30) return K_NEW;
        if (elo < 2100)      return K_MID;
        return K_HIGH;
    }

    double expectedScore(int playerElo, int opponentElo) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentElo - playerElo) / 400.0));
    }

    int newRating(int elo, int gamesRated, double actualScore, int opponentElo) {
        int k = kFactor(elo, gamesRated);
        double expected = expectedScore(elo, opponentElo);
        int updated = (int) Math.round(elo + k * (actualScore - expected));
        return Math.max(updated, eloProperties.min());
    }

    @Transactional
    public void recordResult(long gameId) {
        Map<String, Object> game = jdbcTemplate.queryForMap(
                "SELECT white_player_id, black_player_id, mode, winner_color FROM games WHERE id = ?", gameId);

        if (!"HUMAN".equals(game.get("mode"))) return;

        Long whiteId = (Long) game.get("white_player_id");
        Long blackId = (Long) game.get("black_player_id");
        if (whiteId == null || blackId == null) return;

        String winnerColor = (String) game.get("winner_color");
        double whiteScore;
        double blackScore;
        if ("WHITE".equals(winnerColor)) {
            whiteScore = 1.0; blackScore = 0.0;
        } else if ("BLACK".equals(winnerColor)) {
            whiteScore = 0.0; blackScore = 1.0;
        } else {
            whiteScore = 0.5; blackScore = 0.5;
        }

        Map<String, Object> whiteRow = jdbcTemplate.queryForMap(
                "SELECT elo, games_rated FROM users WHERE id = ?", whiteId);
        Map<String, Object> blackRow = jdbcTemplate.queryForMap(
                "SELECT elo, games_rated FROM users WHERE id = ?", blackId);

        int whiteElo   = ((Number) whiteRow.get("elo")).intValue();
        int whiteGames = ((Number) whiteRow.get("games_rated")).intValue();
        int blackElo   = ((Number) blackRow.get("elo")).intValue();
        int blackGames = ((Number) blackRow.get("games_rated")).intValue();

        int newWhiteElo = newRating(whiteElo, whiteGames, whiteScore, blackElo);
        int newBlackElo = newRating(blackElo, blackGames, blackScore, whiteElo);

        jdbcTemplate.update(
                "UPDATE users SET elo = ?, games_rated = games_rated + 1 WHERE id = ?",
                newWhiteElo, whiteId);
        jdbcTemplate.update(
                "UPDATE users SET elo = ?, games_rated = games_rated + 1 WHERE id = ?",
                newBlackElo, blackId);

        jdbcTemplate.update(
                "UPDATE games SET white_elo_before = ?, black_elo_before = ?, white_elo_after = ?, black_elo_after = ? WHERE id = ?",
                whiteElo, blackElo, newWhiteElo, newBlackElo, gameId);

        jdbcTemplate.update(
                "INSERT INTO elo_history (user_id, game_id, elo_before, elo_after, delta) VALUES (?, ?, ?, ?, ?)",
                whiteId, gameId, whiteElo, newWhiteElo, newWhiteElo - whiteElo);
        jdbcTemplate.update(
                "INSERT INTO elo_history (user_id, game_id, elo_before, elo_after, delta) VALUES (?, ?, ?, ?, ?)",
                blackId, gameId, blackElo, newBlackElo, newBlackElo - blackElo);
    }
}

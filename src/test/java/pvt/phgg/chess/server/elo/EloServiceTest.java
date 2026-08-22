package pvt.phgg.chess.server.elo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EloServiceTest {

    @Mock JdbcTemplate jdbcTemplate;

    private EloService service;

    @BeforeEach
    void setUp() {
        EloProperties props = new EloProperties(1000, 100, 400, 2800, 60);
        service = new EloService(jdbcTemplate, props);
    }

    // ---- kFactor ----

    @Test void kFactor_newPlayer_returns40()        { assertEquals(40, service.kFactor(1500,    0)); }
    @Test void kFactor_29games_returns40()          { assertEquals(40, service.kFactor(1500,   29)); }
    @Test void kFactor_30games_returns20()          { assertEquals(20, service.kFactor(1500,   30)); }
    @Test void kFactor_highRatedVeteran_returns20() { assertEquals(20, service.kFactor(2099,  200)); }
    @Test void kFactor_2100elo_returns10()          { assertEquals(10, service.kFactor(2100,  100)); }
    @Test void kFactor_highEloBut5Games_returns40() { assertEquals(40, service.kFactor(2500,    5)); }

    // ---- expectedScore ----

    @Test
    void expectedScore_equalRatings_isHalf() {
        assertEquals(0.5, service.expectedScore(1500, 1500), 0.001);
    }

    @Test
    void expectedScore_400advantage_isAbout909() {
        assertEquals(0.909, service.expectedScore(1900, 1500), 0.002);
    }

    @Test
    void expectedScore_400disadvantage_isAbout091() {
        assertEquals(0.091, service.expectedScore(1500, 1900), 0.002);
    }

    @Test
    void expectedScore_mirroredPairSumsToOne() {
        double a = service.expectedScore(1600, 1400);
        double b = service.expectedScore(1400, 1600);
        assertEquals(1.0, a + b, 0.0001);
    }

    // ---- newRating ----

    @Test
    void newRating_winVsEqualWithK20_raisesBy10() {
        // K=20 (50 games, elo 1500), expected=0.5, actual=1.0 → +10
        assertEquals(1510, service.newRating(1500, 50, 1.0, 1500));
    }

    @Test
    void newRating_lossVsEqualWithK20_dropsByT10() {
        assertEquals(1490, service.newRating(1500, 50, 0.0, 1500));
    }

    @Test
    void newRating_drawVsEqualRating_unchanged() {
        assertEquals(1500, service.newRating(1500, 50, 0.5, 1500));
    }

    @Test
    void newRating_upsetWinGivesMorePointsThanExpectedWin() {
        int upsetWin    = service.newRating(1200, 0, 1.0, 1600);
        int expectedWin = service.newRating(1200, 0, 1.0, 1200);
        assertTrue(upsetWin > expectedWin);
    }

    @Test
    void newRating_clampedAtConfiguredMinimum() {
        // 101 ELO new player (K=40) loses to 2800: huge loss, must stay ≥ 100
        int result = service.newRating(101, 0, 0.0, 2800);
        assertTrue(result >= 100, "Rating must not drop below minimum (100)");
    }

    // ---- recordResult: guards ----

    @Test
    void recordResult_botMode_skipsAllDbWrites() {
        when(jdbcTemplate.queryForMap(anyString(), eq(1L))).thenReturn(
                Map.of("white_player_id", 10L, "black_player_id", 20L,
                        "mode", "BOT", "winner_color", "WHITE"));

        service.recordResult(1L);

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void recordResult_nullWhiteId_skipsAllDbWrites() {
        Map<String, Object> row = new HashMap<>();
        row.put("mode", "HUMAN");
        row.put("winner_color", "WHITE");
        // white_player_id absent → null
        when(jdbcTemplate.queryForMap(anyString(), eq(1L))).thenReturn(row);

        service.recordResult(1L);

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    // ---- recordResult: correct ELO arithmetic ----

    @Test
    void recordResult_whiteWinsAgainstEqualOpponent_correctDeltas() {
        // Use deliberately asymmetric ratings so each player's expected update is unambiguous
        stubGame(10L, 1L, 2L, "WHITE");
        stubUser(1L, 1600, 50);  // white 1600, K=20
        stubUser(2L, 1400, 50);  // black 1400, K=20

        service.recordResult(10L);

        // White 1600 wins vs 1400: expected ≈ 0.76, gain = round(20*(1-0.76)) = round(4.8) = 5 → 1605
        // Black 1400 loses to 1600: expected ≈ 0.24, loss = round(20*(0-0.24)) = round(-4.8) = -5 → 1395
        int expectedWhiteElo = service.newRating(1600, 50, 1.0, 1400);
        int expectedBlackElo = service.newRating(1400, 50, 0.0, 1600);

        verify(jdbcTemplate).update(
                eq("UPDATE users SET elo = ?, games_rated = games_rated + 1 WHERE id = ?"),
                eq(expectedWhiteElo), eq(1L));
        verify(jdbcTemplate).update(
                eq("UPDATE users SET elo = ?, games_rated = games_rated + 1 WHERE id = ?"),
                eq(expectedBlackElo), eq(2L));
    }

    @Test
    void recordResult_blackWins_correctDeltas() {
        stubGame(11L, 1L, 2L, "BLACK");
        stubUser(1L, 1600, 50);
        stubUser(2L, 1400, 50);

        service.recordResult(11L);

        int expectedWhiteElo = service.newRating(1600, 50, 0.0, 1400);
        int expectedBlackElo = service.newRating(1400, 50, 1.0, 1600);

        verify(jdbcTemplate).update(
                eq("UPDATE users SET elo = ?, games_rated = games_rated + 1 WHERE id = ?"),
                eq(expectedWhiteElo), eq(1L));
        verify(jdbcTemplate).update(
                eq("UPDATE users SET elo = ?, games_rated = games_rated + 1 WHERE id = ?"),
                eq(expectedBlackElo), eq(2L));
    }

    @Test
    void recordResult_draw_bothRatingsUnchangedForEqualOpponents() {
        stubGame(12L, 1L, 2L, null);
        stubUser(1L, 1500, 50);
        stubUser(2L, 1500, 50);

        service.recordResult(12L);

        verify(jdbcTemplate).update(
                eq("UPDATE users SET elo = ?, games_rated = games_rated + 1 WHERE id = ?"),
                eq(1500), eq(1L));
        verify(jdbcTemplate).update(
                eq("UPDATE users SET elo = ?, games_rated = games_rated + 1 WHERE id = ?"),
                eq(1500), eq(2L));
    }

    @Test
    void recordResult_insertsEloHistoryRowForEachPlayer() {
        stubGame(13L, 1L, 2L, "WHITE");
        stubUser(1L, 1600, 50);
        stubUser(2L, 1400, 50);

        service.recordResult(13L);

        verify(jdbcTemplate, times(2)).update(contains("INSERT INTO elo_history"),
                any(), any(), any(), any(), any());
    }

    @Test
    void recordResult_updatesGamesEloColumns() {
        stubGame(14L, 1L, 2L, "WHITE");
        stubUser(1L, 1600, 50);
        stubUser(2L, 1400, 50);

        service.recordResult(14L);

        verify(jdbcTemplate).update(contains("UPDATE games SET white_elo_before"),
                any(), any(), any(), any(), any());
    }

    // ---- recordResult: variant rating pools ----

    @Test
    void recordResult_chess960Game_updatesElo960Columns() {
        stubGameVariant(20L, 1L, 2L, "WHITE", "CHESS960");
        stubUser960(1L, 1600, 50);
        stubUser960(2L, 1400, 50);

        service.recordResult(20L);

        int expectedWhiteElo = service.newRating(1600, 50, 1.0, 1400);
        verify(jdbcTemplate).update(
                eq("UPDATE users SET elo_960 = ?, games_rated_960 = games_rated_960 + 1 WHERE id = ?"),
                eq(expectedWhiteElo), eq(1L));
        // The standard rating pool must be left untouched.
        verify(jdbcTemplate, never()).update(
                eq("UPDATE users SET elo = ?, games_rated = games_rated + 1 WHERE id = ?"),
                any(), any());
    }

    @Test
    void recordResult_standardGame_updatesStandardColumnsOnly() {
        stubGameVariant(21L, 1L, 2L, "WHITE", "STANDARD");
        stubUser(1L, 1600, 50);
        stubUser(2L, 1400, 50);

        service.recordResult(21L);

        verify(jdbcTemplate).update(
                eq("UPDATE users SET elo = ?, games_rated = games_rated + 1 WHERE id = ?"),
                any(), eq(1L));
        verify(jdbcTemplate, never()).update(
                eq("UPDATE users SET elo_960 = ?, games_rated_960 = games_rated_960 + 1 WHERE id = ?"),
                any(), any());
    }

    // ---- helpers ----

    private void stubGame(long gameId, long whiteId, long blackId, String winner) {
        stubGameVariant(gameId, whiteId, blackId, winner, "STANDARD");
    }

    private void stubGameVariant(long gameId, long whiteId, long blackId, String winner, String variant) {
        Map<String, Object> row = new HashMap<>();
        row.put("white_player_id", whiteId);
        row.put("black_player_id", blackId);
        row.put("mode", "HUMAN");
        row.put("winner_color", winner);
        row.put("variant", variant);
        when(jdbcTemplate.queryForMap(anyString(), eq(gameId))).thenReturn(row);
    }

    private void stubUser(long userId, int elo, int gamesRated) {
        when(jdbcTemplate.queryForMap(anyString(), eq(userId)))
                .thenReturn(Map.of("elo", elo, "games_rated", gamesRated));
    }

    private void stubUser960(long userId, int elo, int gamesRated) {
        when(jdbcTemplate.queryForMap(anyString(), eq(userId)))
                .thenReturn(Map.of("elo_960", elo, "games_rated_960", gamesRated));
    }
}

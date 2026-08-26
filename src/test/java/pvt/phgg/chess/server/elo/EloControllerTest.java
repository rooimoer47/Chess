package pvt.phgg.chess.server.elo;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pvt.phgg.chess.server.auth.JwtUtil;
import pvt.phgg.chess.server.user.AppUser;
import pvt.phgg.chess.server.user.UserService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EloControllerTest {

    @Mock UserService userService;
    @Mock JwtUtil jwtUtil;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock HttpServletRequest request;

    private EloController controller;

    @BeforeEach
    void setUp() {
        controller = new EloController(userService, jwtUtil, jdbcTemplate);
    }

    /** The two rating tracks are separate, so history must be filterable to one of them. */
    @Test
    void getEloHistory_chess960_filtersToThatVariant() {
        authoriseAlice();

        controller.getEloHistory("alice", 50, "CHESS960", request);

        Object[] args = capturedArgs();
        assertEquals("CHESS960", args[1], "The variant must be bound to the filter");
        assertEquals("CHESS960", args[2], "…on both sides of the NULL check");
    }

    @Test
    void getEloHistory_noVariant_returnsBothTracks() {
        authoriseAlice();

        controller.getEloHistory("alice", 50, null, request);

        Object[] args = capturedArgs();
        assertNull(args[1], "Absent variant must leave the filter open");
        assertNull(args[2]);
    }

    /**
     * The variant reaches a SQL parameter, never string concatenation, and only
     * after passing a two-value whitelist — anything else is treated as absent.
     */
    @Test
    void getEloHistory_unrecognisedVariant_isIgnoredRatherThanBound() {
        authoriseAlice();

        controller.getEloHistory("alice", 50, "'; DROP TABLE games; --", request);

        Object[] args = capturedArgs();
        assertNull(args[1], "An unrecognised variant must not be bound at all");
        assertNull(args[2]);
    }

    /**
     * elo_history has no variant column of its own — it has to come from the
     * game the entry was recorded for, or the two tracks can't be told apart.
     */
    @Test
    void getEloHistory_readsVariantFromTheGame() {
        authoriseAlice();

        controller.getEloHistory("alice", 50, "STANDARD", request);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(), any(), any(), any());
        assertTrue(sql.getValue().contains("JOIN games"), "must join the game the entry came from");
        assertTrue(sql.getValue().contains("g.variant"), "and select its variant");
    }

    @Test
    void getEloHistory_limitIsCappedAndNeverBelowOne() {
        authoriseAlice();

        controller.getEloHistory("alice", 100_000, null, request);
        assertEquals(200, capturedArgs()[3], "Limit must be capped");

        reset(jdbcTemplate);
        controller.getEloHistory("alice", 0, null, request);
        assertEquals(1, capturedArgs()[3], "Limit must never drop below 1");
    }

    // ---- helpers ----

    private void authoriseAlice() {
        when(request.getCookies()).thenReturn(new Cookie[]{ new Cookie("jwt", "token") });
        when(jwtUtil.extractFromCookies(any())).thenReturn("token");
        when(jwtUtil.isValid("token")).thenReturn(true);
        when(jwtUtil.extractUsername("token")).thenReturn("alice");

        AppUser alice = mock(AppUser.class);
        when(alice.getId()).thenReturn(7L);
        when(userService.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    private Object[] capturedArgs() {
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class),
                args.capture(), args.capture(), args.capture(), args.capture());
        return args.getAllValues().toArray();
    }
}

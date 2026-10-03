package pvt.phgg.chess.server.game;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import pvt.phgg.chess.server.auth.JwtUtil;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameAccessTest {

    @Mock JwtUtil jwtUtil;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock HttpServletRequest request;

    private GameAccess gameAccess;
    private final Cookie[] cookies = { new Cookie("token", "abc") };

    @BeforeEach
    void setUp() {
        gameAccess = new GameAccess(jwtUtil, jdbcTemplate);
        when(request.getCookies()).thenReturn(cookies);
    }

    @Test
    void noTokenCookie() {
        when(jwtUtil.extractFromCookies(cookies)).thenReturn(null);

        assertEquals(Optional.empty(), gameAccess.username(request));
    }

    @Test
    void invalidToken() {
        when(jwtUtil.extractFromCookies(cookies)).thenReturn("abc");
        when(jwtUtil.isValid("abc")).thenReturn(false);

        assertEquals(Optional.empty(), gameAccess.username(request));
    }

    @Test
    void validToken() {
        when(jwtUtil.extractFromCookies(cookies)).thenReturn("abc");
        when(jwtUtil.isValid("abc")).thenReturn(true);
        when(jwtUtil.extractUsername("abc")).thenReturn("alice");

        assertEquals(Optional.of("alice"), gameAccess.username(request));
    }
}

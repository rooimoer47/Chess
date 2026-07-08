package pvt.phgg.chess.server;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pvt.phgg.chess.server.auth.JwtUtil;
import pvt.phgg.chess.server.dto.ActiveGameSummary;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveGamesControllerTest {

    @Mock GameSessionManager sessionManager;
    @Mock JwtUtil jwtUtil;
    @Mock HttpServletRequest request;

    private ActiveGamesController controller;

    @BeforeEach
    void setUp() {
        controller = new ActiveGamesController(sessionManager, jwtUtil);
    }

    @Test
    void getActiveGames_noCookie_returnsForbidden() {
        when(request.getCookies()).thenReturn(null);

        ResponseEntity<List<ActiveGameSummary>> result = controller.getActiveGames("alice", request);

        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    void getActiveGames_usernameDoesNotMatchToken_returnsForbidden() {
        Cookie[] cookies = { new Cookie("jwt", "token123") };
        when(request.getCookies()).thenReturn(cookies);
        when(jwtUtil.extractFromCookies(cookies)).thenReturn("token123");
        when(jwtUtil.isValid("token123")).thenReturn(true);
        when(jwtUtil.extractUsername("token123")).thenReturn("bob");

        ResponseEntity<List<ActiveGameSummary>> result = controller.getActiveGames("alice", request);

        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    void getActiveGames_authorised_returnsSessionManagerResult() {
        Cookie[] cookies = { new Cookie("jwt", "token123") };
        when(request.getCookies()).thenReturn(cookies);
        when(jwtUtil.extractFromCookies(cookies)).thenReturn("token123");
        when(jwtUtil.isValid("token123")).thenReturn(true);
        when(jwtUtil.extractUsername("token123")).thenReturn("alice");
        List<ActiveGameSummary> games = List.of(
                new ActiveGameSummary(1L, "BOT", "alan", null, "WHITE", "IN_PROGRESS"));
        when(sessionManager.activeGamesFor("alice")).thenReturn(games);

        ResponseEntity<List<ActiveGameSummary>> result = controller.getActiveGames("alice", request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(games, result.getBody());
    }
}

package pvt.phgg.chess.server.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import pvt.phgg.chess.server.elo.EloProperties;
import pvt.phgg.chess.server.user.AppUser;
import pvt.phgg.chess.server.user.UserService;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerValidationTest {

    @Mock AuthenticationManager authManager;
    @Mock JwtUtil jwtUtil;
    @Mock UserService userService;
    @Mock HttpServletResponse response;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        EloProperties eloProps = new EloProperties(1000, 100, 400, 2800, 60);
        controller = new AuthController(authManager, jwtUtil, userService, eloProps);
    }

    // ---- register: username validation ----

    @Test
    void register_emptyUsername_returns400() {
        var result = controller.register(new AuthRequest("", "password", null), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_whitespaceOnlyUsername_returns400() {
        var result = controller.register(new AuthRequest("  ", "password", null), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_usernameTooLong_returns400() {
        String tooLong = "a".repeat(101);
        var result = controller.register(new AuthRequest(tooLong, "password", null), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_usernameWithSlash_returns400() {
        var result = controller.register(new AuthRequest("user/name", "password", null), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_usernameWithBackslash_returns400() {
        var result = controller.register(new AuthRequest("user\\name", "password", null), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_usernameWithQuestionMark_returns400() {
        var result = controller.register(new AuthRequest("user?name", "password", null), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_usernameWithHash_returns400() {
        var result = controller.register(new AuthRequest("user#name", "password", null), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_usernameWithInternalSpace_returns400() {
        var result = controller.register(new AuthRequest("user name", "password", null), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_emailStyleUsername_isAccepted() {
        stubSuccessfulRegistration("user@example.com");
        var result = controller.register(new AuthRequest("user@example.com", "password", null), response);
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void register_maxLength100Username_isAccepted() {
        String maxUsername = "a".repeat(100);
        stubSuccessfulRegistration(maxUsername);
        var result = controller.register(new AuthRequest(maxUsername, "password", null), response);
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    // ---- register: password validation ----

    @Test
    void register_emptyPassword_returns400() {
        var result = controller.register(new AuthRequest("alice", "", null), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_passwordTooLong_returns400() {
        String tooLong = "p".repeat(65);
        var result = controller.register(new AuthRequest("alice", tooLong, null), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_maxLength64Password_isAccepted() {
        String maxPassword = "p".repeat(64);
        stubSuccessfulRegistration("alice");
        var result = controller.register(new AuthRequest("alice", maxPassword, null), response);
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    // ---- register: ELO validation ----

    @Test
    void register_startingEloBelowMin_returns400() {
        var result = controller.register(new AuthRequest("alice", "password", 399), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_startingEloAboveMax_returns400() {
        var result = controller.register(new AuthRequest("alice", "password", 2801), response);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_startingEloAtMinBoundary_isAccepted() {
        stubSuccessfulRegistration("alice");
        var result = controller.register(new AuthRequest("alice", "password", 400), response);
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void register_startingEloAtMaxBoundary_isAccepted() {
        stubSuccessfulRegistration("alice");
        var result = controller.register(new AuthRequest("alice", "password", 2800), response);
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void register_nullStartingElo_isAccepted() {
        stubSuccessfulRegistration("alice");
        var result = controller.register(new AuthRequest("alice", "password", null), response);
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    // ---- register: duplicate username ----

    @Test
    void register_duplicateUsername_returns409() {
        when(userService.register(eq("alice"), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Username already taken"));

        var result = controller.register(new AuthRequest("alice", "password", null), response);

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
    }

    // ---- login ----

    @Test
    void login_invalidCredentials_returns401() {
        doThrow(new BadCredentialsException("bad"))
                .when(authManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        var result = controller.login(new AuthRequest("alice", "wrong", null), response);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void login_validCredentials_returns200WithUsername() {
        when(jwtUtil.generateToken("alice")).thenReturn("token");
        when(jwtUtil.createAuthCookie("token")).thenReturn(
                org.springframework.http.ResponseCookie.from("jwt", "token").build());

        var result = controller.login(new AuthRequest("alice", "password", null), response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().toString().contains("alice"));
    }

    // ---- helpers ----

    private void stubSuccessfulRegistration(String username) {
        AppUser registered = mock(AppUser.class);
        when(userService.register(eq(username), anyString(), any())).thenReturn(registered);
        when(jwtUtil.generateToken(username)).thenReturn("token");
        when(jwtUtil.createAuthCookie("token")).thenReturn(
                org.springframework.http.ResponseCookie.from("jwt", "token").build());
    }
}

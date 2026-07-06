package pvt.phgg.chess.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import pvt.phgg.chess.server.user.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String USERNAME_KEY = "username";

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody AuthRequest request, HttpServletResponse response) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
        String token = jwtUtil.generateToken(request.username());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtUtil.createAuthCookie(token).toString());
        return ResponseEntity.ok(Map.of(USERNAME_KEY, request.username()));
    }

    @PostMapping("/register")
    public ResponseEntity<Object> register(@RequestBody AuthRequest request, HttpServletResponse response) {
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();

        if (username.isEmpty() || username.length() > 100
                || username.chars().anyMatch(c -> Character.isWhitespace(c) || "/\\?#".indexOf(c) >= 0)) {
            return ResponseEntity.badRequest().body("Username must be 1-100 characters, no spaces or / \\ ? #");
        }
        if (password.isEmpty() || password.length() > 64) {
            return ResponseEntity.badRequest().body("Password must not be empty (max 64 characters)");
        }

        try {
            userService.register(username, password);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
        String token = jwtUtil.generateToken(username);
        response.addHeader(HttpHeaders.SET_COOKIE, jwtUtil.createAuthCookie(token).toString());
        return ResponseEntity.ok(Map.of(USERNAME_KEY, username));
    }

    @GetMapping("/me")
    public ResponseEntity<Object> me(HttpServletRequest request) {
        String token = jwtUtil.extractFromCookies(request.getCookies());
        if (token == null || !jwtUtil.isValid(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }
        return ResponseEntity.ok(Map.of(USERNAME_KEY, jwtUtil.extractUsername(token)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, jwtUtil.clearAuthCookie().toString());
        return ResponseEntity.ok().build();
    }
}

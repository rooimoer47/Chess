package pvt.phgg.chess.server.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pvt.phgg.chess.server.auth.JwtUtil;

import java.util.Set;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Set<String> VALID_COLOR_PREFERENCES = Set.of("WHITE", "BLACK", "RANDOM");
    private static final Set<String> VALID_BOARD_THEMES = Set.of("classic", "forest", "ocean", "walnut");

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/{username}/preferences")
    public ResponseEntity<Object> getPreferences(@PathVariable String username, HttpServletRequest request) {
        if (!isAuthorised(username, request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userService.getPreferences(username));
    }

    @PutMapping("/{username}/preferences")
    public ResponseEntity<Void> updatePreferences(@PathVariable String username,
                                                  @RequestBody UserPreferencesDto dto,
                                                  HttpServletRequest request) {
        if (!isAuthorised(username, request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (dto.theme() == null || dto.theme().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (!VALID_COLOR_PREFERENCES.contains(dto.colorPreference())) {
            return ResponseEntity.badRequest().build();
        }
        if (!VALID_BOARD_THEMES.contains(dto.boardTheme())) {
            return ResponseEntity.badRequest().build();
        }
        userService.updatePreferences(username, dto.theme(), dto.colorPreference(), dto.boardTheme());
        return ResponseEntity.noContent().build();
    }

    private boolean isAuthorised(String username, HttpServletRequest request) {
        String token = jwtUtil.extractFromCookies(request.getCookies());
        return token != null && jwtUtil.isValid(token) && username.equals(jwtUtil.extractUsername(token));
    }
}

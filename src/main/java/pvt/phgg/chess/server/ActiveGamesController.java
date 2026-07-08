package pvt.phgg.chess.server;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pvt.phgg.chess.server.auth.JwtUtil;
import pvt.phgg.chess.server.dto.ActiveGameSummary;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class ActiveGamesController {

    private final GameSessionManager sessionManager;
    private final JwtUtil jwtUtil;

    public ActiveGamesController(GameSessionManager sessionManager, JwtUtil jwtUtil) {
        this.sessionManager = sessionManager;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/{username:.+}/active-games")
    public ResponseEntity<List<ActiveGameSummary>> getActiveGames(@PathVariable String username, HttpServletRequest request) {
        if (!isAuthorised(username, request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(sessionManager.activeGamesFor(username));
    }

    private boolean isAuthorised(String username, HttpServletRequest request) {
        String token = jwtUtil.extractFromCookies(request.getCookies());
        return token != null && jwtUtil.isValid(token) && username.equals(jwtUtil.extractUsername(token));
    }
}

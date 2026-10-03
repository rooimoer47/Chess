package pvt.phgg.chess.server.analysis;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pvt.phgg.chess.server.game.GameAccess;

import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisQueue queue;
    private final GameAccess gameAccess;

    public AnalysisController(AnalysisQueue queue, GameAccess gameAccess) {
        this.queue = queue;
        this.gameAccess = gameAccess;
    }

    // Queues a game for analysis: the "Analyse" button, mainly for games played before analysis
    // existed. Safe to repeat. Only the game's players may ask; anyone else gets 404, so game ids
    // can't be probed.
    @PostMapping("/games/{gameId}/analysis")
    public ResponseEntity<Object> requestAnalysis(@PathVariable long gameId, HttpServletRequest request) {
        Optional<String> username = gameAccess.username(request);
        if (username.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (!gameAccess.isPlayer(username.get(), gameId)) {
            return ResponseEntity.notFound().build();
        }
        return switch (queue.enqueueGame(gameId)) {
            case QUEUED -> ResponseEntity.accepted().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case NOT_ENDED -> ResponseEntity.status(HttpStatus.CONFLICT).body("Game is still in progress");
            case TOO_SHORT -> ResponseEntity.unprocessableContent().body("Game is too short to analyse");
            case UNREADABLE -> ResponseEntity.unprocessableContent().body("Game can't be replayed");
        };
    }
}

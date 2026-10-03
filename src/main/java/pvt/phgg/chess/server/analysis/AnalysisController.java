package pvt.phgg.chess.server.analysis;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final GameAnalysisService analysisService;
    private final GameAccess gameAccess;

    public AnalysisController(AnalysisQueue queue, GameAnalysisService analysisService, GameAccess gameAccess) {
        this.queue = queue;
        this.analysisService = analysisService;
        this.gameAccess = gameAccess;
    }

    // The game's review, for the replay viewer. Only the game's players, and only once it has ended:
    // anything else is 404, so neither game ids nor live games can be probed.
    @GetMapping("/games/{gameId}/analysis")
    public ResponseEntity<Object> getAnalysis(@PathVariable long gameId, HttpServletRequest request) {
        Optional<String> username = gameAccess.username(request);
        if (username.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (!gameAccess.isPlayer(username.get(), gameId)) {
            return ResponseEntity.notFound().build();
        }
        return analysisService.analysis(gameId)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
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

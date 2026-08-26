package pvt.phgg.chess.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import pvt.phgg.chess.server.game.Game;
import pvt.phgg.chess.server.game.GameMove;
import pvt.phgg.chess.server.game.GameRecorder;
import pvt.phgg.chess.server.user.AppUser;
import pvt.phgg.chess.server.user.UserService;

import java.util.List;

// Moves are durably logged as they're played (see GameRecorder), so a game
// still IN_PROGRESS when the JVM restarts isn't actually lost — it's just
// missing from memory. On startup, replay each one back into a live
// GameSession so reconnecting after a redeploy works the same as
// reconnecting after a phone lock.
@Service
public class GameRestorationService {

    private static final Logger log = LoggerFactory.getLogger(GameRestorationService.class);

    private final GameRecorder gameRecorder;
    private final GameSessionManager sessionManager;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public GameRestorationService(GameRecorder gameRecorder, GameSessionManager sessionManager,
                                   UserService userService, ObjectMapper objectMapper) {
        this.gameRecorder = gameRecorder;
        this.sessionManager = sessionManager;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    // ContextRefreshedEvent — not @PostConstruct — because Flyway's
    // migration also runs during context refresh; a @PostConstruct here
    // raced it and tried to query the games table before it existed.
    // This still fires before the embedded web server starts accepting
    // connections, so no client can race the restoration itself.
    @EventListener(ContextRefreshedEvent.class)
    public void restoreInProgressGames() {
        List<Game> inProgress = gameRecorder.findInProgressGames();
        int restored = 0;
        for (Game game : inProgress) {
            try {
                restore(game);
                restored++;
            } catch (Exception e) {
                log.error("Failed to restore game {} after restart", game.getId(), e);
            }
        }
        if (restored > 0) {
            log.info("Restored {} in-progress game(s) after restart", restored);
        }
    }

    private void restore(Game game) {
        List<GameMove> moves = gameRecorder.findMoves(game.getId());
        String whiteUsername = resolveUsername(game.getWhitePlayerId());
        String blackUsername = resolveUsername(game.getBlackPlayerId());

        GameSession session = GameSession.restore(objectMapper, gameRecorder, game.getId(),
                game.getMode(), game.getBotType(), game.getVariant(), game.getStartingPosition(),
                whiteUsername, game.getWhitePlayerId(),
                blackUsername, game.getBlackPlayerId(),
                moves);

        // The crash may have landed between recording the human's move and
        // computing the bot's reply — nothing else will ever prompt the bot
        // to move on its own, since that normally happens inline right
        // after the triggering human move is handled.
        if (session.isBotTurn()) {
            session.makeBotMove();
        }

        sessionManager.restoreSession(session);
    }

    private String resolveUsername(Long playerId) {
        if (playerId == null) return "BOT";
        return userService.findById(playerId).map(AppUser::getUsername).orElse("BOT");
    }
}

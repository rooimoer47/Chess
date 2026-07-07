package pvt.phgg.chess.server.game;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pvt.phgg.chess.Position;
import pvt.phgg.chess.server.elo.EloService;

@Service
public class GameRecorder {

    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EloService eloService;

    public GameRecorder(GameRepository gameRepository, GameMoveRepository gameMoveRepository,
                        JdbcTemplate jdbcTemplate, EloService eloService) {
        this.gameRepository = gameRepository;
        this.gameMoveRepository = gameMoveRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.eloService = eloService;
    }

    public long startGame(Long whitePlayerId, Long blackPlayerId, String mode) {
        return gameRepository.save(new Game(whitePlayerId, blackPlayerId, mode)).getId();
    }

    public void recordMove(long gameId, int moveNumber, Position from, Position to, String promotionChoice) {
        gameMoveRepository.save(new GameMove(gameId, moveNumber,
                from.getRow(), from.getCol(), to.getRow(), to.getCol(), promotionChoice));
    }

    public void endGame(long gameId, String result, String winnerColor) {
        jdbcTemplate.update(
                "UPDATE games SET result = ?, winner_color = ?, ended_at = now() WHERE id = ?",
                result, winnerColor, gameId);
        eloService.scheduleEloUpdate(gameId);
    }
}

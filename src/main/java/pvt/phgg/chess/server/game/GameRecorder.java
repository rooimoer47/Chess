package pvt.phgg.chess.server.game;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pvt.phgg.chess.Position;

@Service
public class GameRecorder {

    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;
    private final JdbcTemplate jdbcTemplate;

    public GameRecorder(GameRepository gameRepository, GameMoveRepository gameMoveRepository, JdbcTemplate jdbcTemplate) {
        this.gameRepository = gameRepository;
        this.gameMoveRepository = gameMoveRepository;
        this.jdbcTemplate = jdbcTemplate;
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
    }
}

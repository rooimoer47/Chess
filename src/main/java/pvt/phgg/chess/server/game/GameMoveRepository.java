package pvt.phgg.chess.server.game;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface GameMoveRepository extends CrudRepository<GameMove, Long> {
    List<GameMove> findByGameIdOrderByMoveNumber(long gameId);
}

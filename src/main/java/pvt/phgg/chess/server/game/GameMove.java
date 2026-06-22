package pvt.phgg.chess.server.game;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("game_moves")
public class GameMove {

    @Id
    private Long id;
    private long gameId;
    private int moveNumber;
    private int fromRow;
    private int fromCol;
    private int toRow;
    private int toCol;
    private String promotionChoice;
    private OffsetDateTime playedAt;

    GameMove() {}

    public GameMove(long gameId, int moveNumber, int fromRow, int fromCol, int toRow, int toCol, String promotionChoice) {
        this.gameId = gameId;
        this.moveNumber = moveNumber;
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
        this.promotionChoice = promotionChoice;
        this.playedAt = OffsetDateTime.now();
    }
}

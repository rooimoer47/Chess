package pvt.phgg.chess.server.game;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Table("games")
public class Game {

    @Id
    private Long id;
    private Long whitePlayerId;
    private Long blackPlayerId;
    private String mode;
    private String result;
    private String winnerColor;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;

    Game() {}

    public Game(Long whitePlayerId, Long blackPlayerId, String mode) {
        this.whitePlayerId = whitePlayerId;
        this.blackPlayerId = blackPlayerId;
        this.mode = mode;
        this.startedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getWhitePlayerId() { return whitePlayerId; }
    public Long getBlackPlayerId() { return blackPlayerId; }
    public String getMode() { return mode; }
    public String getResult() { return result; }
    public String getWinnerColor() { return winnerColor; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
}

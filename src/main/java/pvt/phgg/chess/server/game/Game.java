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
    private String botType;
    private String variant;
    private String startingPosition;
    private String result;
    private String winnerColor;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;

    Game() {}

    public Game(Long whitePlayerId, Long blackPlayerId, String mode, String botType) {
        this(whitePlayerId, blackPlayerId, mode, botType, "STANDARD", "RNBQKBNR");
    }

    public Game(Long whitePlayerId, Long blackPlayerId, String mode, String botType,
                String variant, String startingPosition) {
        this.whitePlayerId = whitePlayerId;
        this.blackPlayerId = blackPlayerId;
        this.mode = mode;
        this.botType = botType;
        this.variant = variant;
        this.startingPosition = startingPosition;
        this.startedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getWhitePlayerId() { return whitePlayerId; }
    public Long getBlackPlayerId() { return blackPlayerId; }
    public String getMode() { return mode; }
    public String getBotType() { return botType; }
    public String getVariant() { return variant; }
    public String getStartingPosition() { return startingPosition; }
    public String getResult() { return result; }
    public String getWinnerColor() { return winnerColor; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
}

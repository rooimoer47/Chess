package pvt.phgg.chess.server.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Table("users")
public class AppUser {

    @Id
    private Long id;
    private String username;
    private String passwordHash;
    private String theme = "classic";
    private String colorPreference = "RANDOM";
    private String boardTheme = "classic";
    private int elo = 1000;
    private int gamesRated = 0;
    // Explicit column names: the naming strategy converts camelCase humps (gamesRated → games_rated)
    // but not digit boundaries, so elo960 would otherwise map to "elo960" not "elo_960".
    @Column("elo_960")
    private int elo960 = 1000;
    @Column("games_rated_960")
    private int gamesRated960 = 0;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastActiveAt;

    AppUser() {}

    public AppUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastActiveAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public AppUser(String username, String passwordHash, int startingElo) {
        this(username, passwordHash);
        this.elo = startingElo;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getTheme() { return theme; }
    public String getColorPreference() { return colorPreference; }
    public String getBoardTheme() { return boardTheme; }
    public int getElo() { return elo; }
    public int getGamesRated() { return gamesRated; }
    public int getElo960() { return elo960; }
    public int getGamesRated960() { return gamesRated960; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastActiveAt() { return lastActiveAt; }
}

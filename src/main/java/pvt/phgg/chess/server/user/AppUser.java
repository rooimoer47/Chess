package pvt.phgg.chess.server.user;

import org.springframework.data.annotation.Id;
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
    private OffsetDateTime createdAt;
    private OffsetDateTime lastActiveAt;

    AppUser() {}

    public AppUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastActiveAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getTheme() { return theme; }
    public String getColorPreference() { return colorPreference; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastActiveAt() { return lastActiveAt; }
}

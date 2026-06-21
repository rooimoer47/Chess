package pvt.phgg.chess.server.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("users")
public class AppUser {

    @Id
    private Long id;
    private String username;
    private String passwordHash;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastActiveAt;

    AppUser() {}

    public AppUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = OffsetDateTime.now();
        this.lastActiveAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public OffsetDateTime getLastActiveAt() { return lastActiveAt; }
}

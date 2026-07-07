package pvt.phgg.chess.server.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    public java.util.Optional<AppUser> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public AppUser register(String username, String password, Integer startingElo) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already taken");
        }
        AppUser user = startingElo != null
                ? new AppUser(username, passwordEncoder.encode(password), startingElo)
                : new AppUser(username, passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    public void updateLastActive(long userId) {
        jdbcTemplate.update("UPDATE users SET last_active_at = now() WHERE id = ?", userId);
    }

    public UserPreferencesDto getPreferences(String username) {
        return userRepository.findByUsername(username)
                .map(u -> new UserPreferencesDto(u.getTheme(), u.getColorPreference(), u.getBoardTheme()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    public void updatePreferences(String username, String theme, String colorPreference, String boardTheme) {
        jdbcTemplate.update(
                "UPDATE users SET theme = ?, color_preference = ?, board_theme = ? WHERE username = ?",
                theme, colorPreference, boardTheme, username);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .roles("PLAYER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}

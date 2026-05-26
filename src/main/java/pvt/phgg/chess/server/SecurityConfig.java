package pvt.phgg.chess.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${chess.player1.username}")
    private String player1Username;
    @Value("${chess.player1.password}")
    private String player1Password;
    @Value("${chess.player2.username}")
    private String player2Username;
    @Value("${chess.player2.password}")
    private String player2Password;

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails p1 = User.withUsername(player1Username)
                .password("{noop}" + player1Password)
                .roles("PLAYER")
                .build();
        UserDetails p2 = User.withUsername(player2Username)
                .password("{noop}" + player2Password)
                .roles("PLAYER")
                .build();
        return new InMemoryUserDetailsManager(p1, p2);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}

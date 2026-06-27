package pvt.phgg.chess.server.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import pvt.phgg.chess.server.user.UserService;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtUtil jwtUtil;
    private final UserService userService;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            var req = servletRequest.getServletRequest();
            String token = jwtUtil.extractFromCookies(req.getCookies());
            if (token == null) {
                LOGGER.warn("WS handshake rejected: no jwt cookie (cookies={})", req.getCookies() == null ? "null" : req.getCookies().length);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            if (!jwtUtil.isValid(token)) {
                LOGGER.warn("WS handshake rejected: jwt token invalid");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            String username = jwtUtil.extractUsername(token);
            attributes.put("username", username);
            String botParam = req.getParameter("bot");
            attributes.put("botType", botParam != null ? botParam : "none");
            userService.findByUsername(username)
                    .ifPresent(u -> attributes.put("userId", u.getId()));
            LOGGER.debug("WS handshake accepted for user: {}", username);
            return true;
        }
        LOGGER.warn("WS handshake rejected: request is not a ServletServerHttpRequest");
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // no post-handshake action needed
    }
}

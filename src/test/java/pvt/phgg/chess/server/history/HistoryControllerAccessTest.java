package pvt.phgg.chess.server.history;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import pvt.phgg.chess.server.game.GameAccess;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Replays are private to the game's two players, including games still being played. */
@ExtendWith(MockitoExtension.class)
class HistoryControllerAccessTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock GameAccess gameAccess;
    @Mock HttpServletRequest request;

    private HistoryController controller;

    @BeforeEach
    void setUp() {
        controller = new HistoryController(jdbcTemplate, gameAccess);
    }

    @Test
    void notLoggedIn() {
        when(gameAccess.username(request)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.UNAUTHORIZED, controller.getGameMoves(7L, request).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, controller.getGameBoards(7L, request).getStatusCode());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void otherUsersCannotReadTheGame() {
        when(gameAccess.username(request)).thenReturn(Optional.of("mallory"));
        when(gameAccess.isPlayer("mallory", 7L)).thenReturn(false);

        assertEquals(HttpStatus.NOT_FOUND, controller.getGameMoves(7L, request).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.getGameBoards(7L, request).getStatusCode());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void playersCanReadTheGame() {
        when(gameAccess.username(request)).thenReturn(Optional.of("alice"));
        when(gameAccess.isPlayer("alice", 7L)).thenReturn(true);

        assertEquals(HttpStatus.OK, controller.getGameMoves(7L, request).getStatusCode());
    }
}

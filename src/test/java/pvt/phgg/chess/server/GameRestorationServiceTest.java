package pvt.phgg.chess.server;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pvt.phgg.chess.server.dto.ActiveGameSummary;
import pvt.phgg.chess.server.game.Game;
import pvt.phgg.chess.server.game.GameRecorder;
import pvt.phgg.chess.server.user.AppUser;
import pvt.phgg.chess.server.user.UserService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameRestorationServiceTest {

    @Mock GameRecorder gameRecorder;
    @Mock GameSessionManager sessionManager;
    @Mock UserService userService;

    private GameRestorationService service;

    @BeforeEach
    void setUp() {
        service = new GameRestorationService(gameRecorder, sessionManager, userService, new ObjectMapper());
    }

    private AppUser userWithUsername(String username) {
        AppUser user = mock(AppUser.class);
        lenient().when(user.getUsername()).thenReturn(username);
        return user;
    }

    @Test
    void restoreInProgressGames_registersOneSessionPerInProgressGame() {
        Game game = new Game(1L, 2L, "HUMAN", null);
        setId(game, 10L);
        when(gameRecorder.findInProgressGames()).thenReturn(List.of(game));
        when(gameRecorder.findMoves(10L)).thenReturn(List.of());
        AppUser alice = userWithUsername("alice");
        when(userService.findById(1L)).thenReturn(Optional.of(alice));
        AppUser bob = userWithUsername("bob");
        when(userService.findById(2L)).thenReturn(Optional.of(bob));

        service.restoreInProgressGames();

        ArgumentCaptor<GameSession> captor = ArgumentCaptor.forClass(GameSession.class);
        verify(sessionManager).restoreSession(captor.capture());
        assertEquals(10L, captor.getValue().getGameId());
    }

    @Test
    void restoreInProgressGames_noInProgressGames_registersNothing() {
        when(gameRecorder.findInProgressGames()).thenReturn(List.of());

        service.restoreInProgressGames();

        verifyNoInteractions(sessionManager);
    }

    @Test
    void restoreInProgressGames_botSideResolvesToBotUsername() {
        // whitePlayerId is null, so the bot must be white and the human
        // (resolved from blackPlayerId) must be black.
        Game game = new Game(null, 2L, "BOT", "alan");
        setId(game, 11L);
        when(gameRecorder.findInProgressGames()).thenReturn(List.of(game));
        when(gameRecorder.findMoves(11L)).thenReturn(List.of());
        AppUser carol = userWithUsername("carol");
        when(userService.findById(2L)).thenReturn(Optional.of(carol));

        service.restoreInProgressGames();

        ArgumentCaptor<GameSession> captor = ArgumentCaptor.forClass(GameSession.class);
        verify(sessionManager).restoreSession(captor.capture());
        ActiveGameSummary summary = captor.getValue().summarizeFor("carol");
        assertNotNull(summary);
        assertEquals("BLACK", summary.color());
        assertEquals("alan", summary.botType());
    }

    @Test
    void restoreInProgressGames_botsTurnAfterRestore_makesTheBotMove() {
        // white_player_id null means the bot is white and moves first —
        // nothing else will ever prompt it after a restart.
        Game game = new Game(null, 2L, "BOT", "random");
        setId(game, 12L);
        when(gameRecorder.findInProgressGames()).thenReturn(List.of(game));
        when(gameRecorder.findMoves(12L)).thenReturn(List.of());
        AppUser carol = userWithUsername("carol");
        when(userService.findById(2L)).thenReturn(Optional.of(carol));

        service.restoreInProgressGames();

        ArgumentCaptor<GameSession> captor = ArgumentCaptor.forClass(GameSession.class);
        verify(sessionManager).restoreSession(captor.capture());
        assertFalse(captor.getValue().isBotTurn(), "The bot's opening move must already have been made");
    }

    @Test
    void restoreInProgressGames_oneGameFailsToRestore_othersStillRegistered() {
        Game broken = new Game(1L, 2L, "HUMAN", null);
        setId(broken, 20L);
        Game healthy = new Game(3L, 4L, "HUMAN", null);
        setId(healthy, 21L);
        when(gameRecorder.findInProgressGames()).thenReturn(List.of(broken, healthy));
        when(gameRecorder.findMoves(20L)).thenThrow(new RuntimeException("boom"));
        when(gameRecorder.findMoves(21L)).thenReturn(List.of());
        AppUser dave = userWithUsername("dave");
        when(userService.findById(3L)).thenReturn(Optional.of(dave));
        AppUser erin = userWithUsername("erin");
        when(userService.findById(4L)).thenReturn(Optional.of(erin));

        service.restoreInProgressGames();

        ArgumentCaptor<GameSession> captor = ArgumentCaptor.forClass(GameSession.class);
        verify(sessionManager).restoreSession(captor.capture());
        assertEquals(21L, captor.getValue().getGameId());
    }

    private void setId(Game game, long id) {
        try {
            var field = Game.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(game, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

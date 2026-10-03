package pvt.phgg.chess.server.analysis;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import pvt.phgg.chess.server.analysis.AnalysisQueue.EnqueueResult;
import pvt.phgg.chess.server.analysis.GameAnalysisService.GameAnalysis;
import pvt.phgg.chess.server.game.GameAccess;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisControllerTest {

    @Mock AnalysisQueue queue;
    @Mock GameAnalysisService service;
    @Mock GameAccess gameAccess;
    @Mock HttpServletRequest request;

    private AnalysisController controller;

    @BeforeEach
    void setUp() {
        controller = new AnalysisController(queue, service, gameAccess);
    }

    private void loggedInAs(String username, boolean player) {
        when(gameAccess.username(request)).thenReturn(Optional.of(username));
        when(gameAccess.isPlayer(username, 7L)).thenReturn(player);
    }

    @Test
    void notLoggedIn() {
        when(gameAccess.username(request)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.UNAUTHORIZED, controller.getAnalysis(7L, request).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, controller.requestAnalysis(7L, request).getStatusCode());
        verifyNoInteractions(service, queue);
    }

    @Test
    void someoneElsesGameLooksMissing() {
        loggedInAs("mallory", false);

        assertEquals(HttpStatus.NOT_FOUND, controller.getAnalysis(7L, request).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.requestAnalysis(7L, request).getStatusCode());
        verifyNoInteractions(service, queue);
    }

    @Test
    void liveGameHasNoReview() {
        loggedInAs("alice", true);
        when(service.analysis(7L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.getAnalysis(7L, request).getStatusCode());
    }

    @Test
    void playerGetsTheReview() {
        loggedInAs("alice", true);
        GameAnalysis analysis = new GameAnalysis("DONE", 3, 3, List.of(), List.of());
        when(service.analysis(7L)).thenReturn(Optional.of(analysis));

        var response = controller.getAnalysis(7L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(analysis, response.getBody());
    }

    @Test
    void requestAnalysisOutcomes() {
        loggedInAs("alice", true);

        when(queue.enqueueGame(7L)).thenReturn(EnqueueResult.QUEUED);
        assertEquals(HttpStatus.ACCEPTED, controller.requestAnalysis(7L, request).getStatusCode());
        when(queue.enqueueGame(7L)).thenReturn(EnqueueResult.NOT_ENDED);
        assertEquals(HttpStatus.CONFLICT, controller.requestAnalysis(7L, request).getStatusCode());
        when(queue.enqueueGame(7L)).thenReturn(EnqueueResult.TOO_SHORT);
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, controller.requestAnalysis(7L, request).getStatusCode());
    }
}

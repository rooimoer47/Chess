package pvt.phgg.chess.server.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import pvt.phgg.chess.server.analysis.AnalysisQueue;
import pvt.phgg.chess.server.elo.EloService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameRecorderTest {

    @Mock GameRepository gameRepository;
    @Mock GameMoveRepository gameMoveRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock EloService eloService;
    @Mock AnalysisQueue analysisQueue;
    @InjectMocks GameRecorder recorder;

    /** Every way a game ends goes through endGame, so this one call is what gets games reviewed. */
    @Test
    void endingAGameRecordsTheResultAndQueuesRatingAndAnalysis() {
        recorder.endGame(5L, "CHECKMATE", "WHITE");

        verify(jdbcTemplate).update(contains("UPDATE games SET result"), eq("CHECKMATE"), eq("WHITE"), eq(5L));
        verify(eloService).scheduleEloUpdate(5L);
        verify(analysisQueue).requestAnalysis(5L);
    }
}

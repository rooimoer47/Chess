package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.GameReplay.RecordedMove;
import pvt.phgg.chess.GameReplay.ReplayedPosition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static pvt.phgg.chess.FenSerializerTest.moves;

class GameReplayTest {

    @Test
    void onePositionPerPlyEachWithTheMovePlayedFromIt() {
        List<ReplayedPosition> positions = GameReplay.replay(GameEngine.STANDARD_BACK_RANK, false, moves("e2e4 e7e5"));

        assertEquals(3, positions.size());
        assertEquals(List.of(0, 1, 2), positions.stream().map(ReplayedPosition::ply).toList());
        assertEquals("e2e4", positions.get(0).playedUci());
        assertEquals("e7e5", positions.get(1).playedUci());
        assertNull(positions.get(2).playedUci(), "nothing is played from the final position");
    }

    @Test
    void emptyGameIsJustTheStartPosition() {
        List<ReplayedPosition> positions = GameReplay.replay("BBQNNRKR", true, List.of());

        assertEquals(1, positions.size());
        assertEquals("bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf -", positions.getFirst().epd());
    }

    @Test
    void illegalRecordedMoveIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GameReplay.replay(GameEngine.STANDARD_BACK_RANK, false, moves("e2e4 e2e4")));
        assertTrue(e.getMessage().contains("move 2"));
    }

    @Test
    void promotionWithoutChoiceIsRejected() {
        List<RecordedMove> noChoice = moves("a2a4 b7b5 a4b5 a7a6 b5a6 c8b7 a6b7 b8c6 b7a8");
        assertThrows(IllegalArgumentException.class,
                () -> GameReplay.replay(GameEngine.STANDARD_BACK_RANK, false, noChoice));
    }
}

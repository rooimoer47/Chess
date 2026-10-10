package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.GameReplay.ReplayedPosition;
import pvt.phgg.chess.OpeningBook.Opening;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static pvt.phgg.chess.FenSerializerTest.moves;

class OpeningBookTest {

    private static final OpeningBook BOOK = OpeningBook.standard();

    private static List<String> epds(String backRank, boolean chess960, String uciMoves) {
        return GameReplay.replay(backRank, chess960, moves(uciMoves)).stream().map(ReplayedPosition::epd).toList();
    }

    private static List<String> epds(String uciMoves) {
        return epds(GameEngine.STANDARD_BACK_RANK, false, uciMoves);
    }

    @Test
    void everyLineOfTheDatasetIsPlayable() {
        // Loading throws on a move it can't play, so getting here means all of them were.
        // The dataset has one row per position.
        assertEquals(3865, BOOK.size());
    }

    @Test
    void findsAnOpeningByItsPosition() {
        List<String> italian = epds("e2e4 e7e5 g1f3 b8c6 f1c4");
        assertEquals(Optional.of(new Opening("C50", "Italian Game")), BOOK.find(italian.get(5)));
    }

    @Test
    void anotherMoveOrderReachesTheSameOpening() {
        // 1.Nf3 Nc6 2.e4 e5 3.Bc4 is the Italian by transposition.
        List<String> transposed = epds("g1f3 b8c6 e2e4 e7e5 f1c4");
        assertEquals("Italian Game", BOOK.find(transposed.get(5)).orElseThrow().name());
    }

    @Test
    void castlingLinesArePlayed() {
        // 1.e4 e5 2.Nf3 Nc6 3.Bb5 a6 4.Ba4 Nf6 5.O-O
        List<String> ruyLopez = epds("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1");
        assertTrue(BOOK.find(ruyLopez.get(9)).orElseThrow().name().startsWith("Ruy Lopez"), BOOK.find(ruyLopez.get(9)).toString());
    }

    @Test
    void aGameKeepsItsLastNamedOpeningOnceItLeavesTheBook() {
        // The Italian, then two moves no opening is named after.
        List<String> game = epds("e2e4 e7e5 g1f3 b8c6 f1c4 h7h5 a2a4");
        assertEquals(5, BOOK.lastNamedPly(game));
        assertEquals("Italian Game", BOOK.at(game, 7).orElseThrow().name());
        // Before the bishop comes out it is still only a king's knight opening.
        assertNotEquals("Italian Game", BOOK.at(game, 4).orElseThrow().name());
        assertEquals(Optional.empty(), BOOK.at(game, 0));
    }

    @Test
    void aGameFromAnotherStartPositionHasNoOpening() {
        List<String> chess960 = epds("BBQNNRKR", true, "e2e4 e7e5");
        assertEquals(Optional.empty(), BOOK.at(chess960, 2));
        assertEquals(0, BOOK.lastNamedPly(chess960));
        // The standard back rank played as Chess960 writes castling rights differently: no match either.
        List<String> standardAs960 = epds(GameEngine.STANDARD_BACK_RANK, true, "e2e4 e7e5");
        assertEquals(Optional.empty(), BOOK.at(standardAs960, 2));
    }
}

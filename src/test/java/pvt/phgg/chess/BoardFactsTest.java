package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.BoardFacts.PieceOn;
import pvt.phgg.chess.BoardFacts.Pin;
import pvt.phgg.chess.BoardFacts.Threat;
import pvt.phgg.chess.piece.PieceType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static pvt.phgg.chess.FenSerializerTest.moves;

class BoardFactsTest {

    private static GameEngine after(String uciMoves) {
        GameReplay.ReplayedGame game = GameReplay.replayGame(GameEngine.STANDARD_BACK_RANK, false, moves(uciMoves));
        return game.position(game.positions().size() - 1);
    }

    @Test
    void materialCountsStandardValues() {
        GameEngine start = new GameEngine();
        assertEquals(39, BoardFacts.material(start, true));
        assertEquals(39, BoardFacts.material(start, false));
        // 1.e4 d5 2.exd5: Black is a pawn down.
        assertEquals(38, BoardFacts.material(after("e2e4 d7d5 e4d5"), false));
    }

    @Test
    void pieceAt() {
        GameEngine start = new GameEngine();
        assertEquals(Optional.of(new PieceOn(PieceType.KNIGHT, false, "g8")), BoardFacts.pieceAt(start, "g8"));
        assertEquals(Optional.empty(), BoardFacts.pieceAt(start, "e4"));
    }

    @Test
    void undefendedPieceIsAThreat() {
        // 1.e4 d5 2.Qg4?: the bishop on c8 takes the queen for free.
        List<Threat> threats = BoardFacts.threats(after("e2e4 d7d5 d1g4"));
        assertEquals(new Threat(new PieceOn(PieceType.QUEEN, true, "g4"), new PieceOn(PieceType.BISHOP, false, "c8"), false),
                threats.getFirst());
    }

    @Test
    void defendedPieceAttackedByACheaperOneIsAThreat() {
        // ...Nd4: defended by the e5 pawn, but the c3 pawn takes a knight for a pawn. The e5 pawn
        // itself is left undefended against the f3 knight; the knight is listed first, being worth more.
        List<Threat> threats = BoardFacts.threats(after("e2e4 e7e5 c2c3 b8c6 g1f3 c6d4"));
        assertEquals(List.of(
                new Threat(new PieceOn(PieceType.KNIGHT, false, "d4"), new PieceOn(PieceType.PAWN, true, "c3"), true),
                new Threat(new PieceOn(PieceType.PAWN, false, "e5"), new PieceOn(PieceType.KNIGHT, true, "f3"), false)),
                threats);
    }

    @Test
    void defendedPieceAttackedByAnEqualOneIsNotAThreat() {
        // ...Nd4 without c3: the f3 knight can take, but the e5 pawn takes back.
        assertTrue(BoardFacts.threats(after("e2e4 e7e5 g1f3 b8c6 b1c3 c6d4")).stream()
                .noneMatch(t -> t.piece().square().equals("d4")));
    }

    @Test
    void aPieceInFrontOfAMoreValuableOneIsPinned() {
        // 1.e4 e5 2.Nf3 d6 3.Nc3 Bg4: the knight on f3 shields the queen.
        GameEngine position = after("e2e4 e7e5 g1f3 d7d6 b1c3 c8g4");
        assertEquals(List.of(new Pin(new PieceOn(PieceType.KNIGHT, true, "f3"), new PieceOn(PieceType.BISHOP, false, "g4"),
                new PieceOn(PieceType.QUEEN, true, "d1"))), BoardFacts.pins(position, true));
        assertEquals(List.of(), BoardFacts.pins(position, false));
        // 4.Be2: now a bishop stands right behind the knight, and it is worth no more.
        assertEquals(List.of(), BoardFacts.pins(after("e2e4 e7e5 g1f3 d7d6 b1c3 c8g4 f1e2"), true));
    }

    @Test
    void whatAPieceAttacksAndWhetherItIsDefended() {
        // 4.Ng5 Be7 5.Nxf7: the knight hits the queen, the rook and the e5 pawn, and the bishop covers it.
        GameEngine position = after("e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 f3g5 f8e7 g5f7");
        assertEquals(Set.of(new PieceOn(PieceType.QUEEN, false, "d8"), new PieceOn(PieceType.ROOK, false, "h8"),
                new PieceOn(PieceType.PAWN, false, "e5")), Set.copyOf(BoardFacts.attackedFrom(position, "f7")));
        assertTrue(BoardFacts.isDefended(position, "f7"));
        assertFalse(BoardFacts.isDefended(position, "e4"));
        assertFalse(BoardFacts.canTake(position, "e8", "f7"), "the king can't take a defended piece");
        assertTrue(BoardFacts.canTake(position, "f6", "e4"));
    }

    @Test
    void playLineStopsAtAnIllegalMove() {
        GameEngine start = new GameEngine();
        List<GameEngine> line = BoardFacts.playLine(start, List.of("e2e4", "e7e5", "e4e5"));
        assertEquals(2, line.size());
        assertTrue(BoardFacts.pieceAt(start, "e4").isEmpty(), "the starting position is left alone");
    }
}

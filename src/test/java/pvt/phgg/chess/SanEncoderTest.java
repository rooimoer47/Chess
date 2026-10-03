package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.GameReplay.MoveDisplay;
import pvt.phgg.chess.GameReplay.ReplayedGame;
import pvt.phgg.chess.GameReplay.ReplayedPosition;
import pvt.phgg.chess.piece.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static pvt.phgg.chess.FenSerializerTest.moves;

class SanEncoderTest {

    private static List<String> sans(String backRank, boolean chess960, String uciMoves) {
        return GameReplay.replay(backRank, chess960, moves(uciMoves)).stream()
                .map(ReplayedPosition::playedSan)
                .filter(san -> san != null)
                .toList();
    }

    private static List<String> sans(String uciMoves) {
        return sans(GameEngine.STANDARD_BACK_RANK, false, uciMoves);
    }

    @Test
    void piecesPawnsAndCastling() {
        assertEquals(List.of("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "O-O"),
                sans("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 e1g1"));
    }

    @Test
    void queensideCastling() {
        assertEquals("O-O-O", sans("d2d4 d7d5 b1c3 b8c6 c1f4 c8f5 d1d2 d8d7 e1c1").getLast());
    }

    @Test
    void captures() {
        assertEquals(List.of("e4", "d5", "exd5", "Qxd5", "Nc3"), sans("e2e4 d7d5 e4d5 d8d5 b1c3"));
    }

    @Test
    void enPassant() {
        assertEquals("exd6", sans("e2e4 a7a6 e4e5 d7d5 e5d6").getLast());
    }

    @Test
    void promotionWithCapture() {
        assertEquals("bxa8=Q", sans("a2a4 b7b5 a4b5 a7a6 b5a6 c8b7 a6b7 b8c6 b7a8q").getLast());
    }

    @Test
    void checkAndMate() {
        assertEquals("Qh5+", sans("e2e4 f7f6 d1h5").getLast());
        assertEquals("Qxf7#", sans("e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7").getLast());
    }

    @Test
    void fileDisambiguation() {
        // Knights on b5 and f3 can both go to d4.
        assertEquals("Nfd4", sans("g1f3 a7a6 b1c3 h7h6 c3b5 h6h5 f3d4").getLast());
    }

    private static APiece[][] boardWithKings() {
        APiece[][] b = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                b[r][c] = new EmptySquare(new Position(r, c));
        b[0][7] = new King(new Position(0, 7), true);
        b[0][7].moved();
        b[7][7] = new King(new Position(7, 7), false);
        b[7][7].moved();
        return b;
    }

    @Test
    void rankDisambiguation() {
        // Rooks on a1 and a5 can both go to a3.
        APiece[][] b = boardWithKings();
        b[0][0] = new Rook(new Position(0, 0), true);
        b[4][0] = new Rook(new Position(4, 0), true);

        assertEquals("R1a3", SanEncoder.apply(new GameEngine(b, true), "a1a3").san());
    }

    @Test
    void fileAndRankDisambiguation() {
        // Queens on a1, c1 and a3 can all go to b2: neither the file nor the rank alone picks a1.
        // Black's king moves to h7 so that Qb2 isn't check along the long diagonal.
        APiece[][] b = boardWithKings();
        b[6][7] = b[7][7];
        b[6][7].setCurrentPosition(new Position(6, 7));
        b[7][7] = new EmptySquare(new Position(7, 7));
        b[0][0] = new Queen(new Position(0, 0), true);
        b[0][2] = new Queen(new Position(0, 2), true);
        b[2][0] = new Queen(new Position(2, 0), true);

        assertEquals("Qa1b2", SanEncoder.apply(new GameEngine(b, true), "a1b2").san());
    }

    @Test
    void chess960CastleIsWrittenAsCastling() {
        String clear = "g1f3 a7a6 g2g3 a6a5 f1g2 h7h6 e2e4 h6h5 e1e2 b7b6 d1h1";
        assertEquals("O-O", sans("RNBKQBNR", true, clear).getLast());
    }

    @Test
    void describeDrawsA960CastleToTheKingsDestination() {
        ReplayedGame game = GameReplay.replayGame("RNBKQBNR", true,
                moves("g1f3 a7a6 g2g3 a6a5 f1g2 h7h6 e2e4 h6h5 e1e2 b7b6"));

        assertEquals(new MoveDisplay("O-O", "d1", "g1"), game.describe(10, "d1h1"));
    }

    @Test
    void lineFromAPosition() {
        ReplayedGame game = GameReplay.replayGame(GameEngine.STANDARD_BACK_RANK, false, moves("e2e4 e7e5"));

        assertEquals(List.of("Nf3", "Nc6", "Bb5"), game.sanLine(2, List.of("g1f3", "b8c6", "f1b5")));
        assertEquals(List.of("Nf3"), game.sanLine(2, List.of("g1f3", "e2e4")), "stops at the first illegal move");
    }
}

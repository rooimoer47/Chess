package pvt.phgg.chess.server.analysis;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.GameReplay;
import pvt.phgg.chess.GameReplay.ReplayedGame;
import pvt.phgg.chess.server.analysis.Motifs.Found;
import pvt.phgg.chess.server.analysis.Motifs.Motif;
import pvt.phgg.chess.server.analysis.Motifs.Tag;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The motifs found in an engine line: which ones, and how they are put. */
class MotifsTest {

    // 1.e4 e5 2.Nf3 Nc6 3.Bc4 Nf6 4.Ng5
    private static final String TWO_KNIGHTS = "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 f3g5";
    // 1.e4 e5 2.Nf3 Nf6 3.Nxe5 d6 4.Nf3 Nxe4 5.d3 Nf6 6.Bg5 Be7 7.Bxf6 Bxf6 8.Be2 O-O 9.O-O Re8
    // 10.Re1 Rxe2 11.Rxe2 Qd7 12.Qe1: only Black's queen still covers e8.
    private static final String BACK_RANK = "e2e4 e7e5 g1f3 g8f6 f3e5 d7d6 e5f3 f6e4 d2d3 e4f6 c1g5 f8e7 g5f6 e7f6 "
            + "f1e2 e8g8 e1g1 f8e8 f1e1 e8e2 e1e2 d8d7 d1e1";

    // The motifs in `line`, played from the position after `game`: its first move is the one played
    // and the rest the engine's reply.
    private static List<Found> allowed(String game, String line, boolean material) {
        ReplayedGame replay = GameReplay.replayGame(GameEngine.STANDARD_BACK_RANK, false, TestGames.moves(game));
        int ply = replay.positions().size() - 1;
        List<String> ucis = Arrays.asList(line.split(" "));
        return withoutGists(Motifs.allowed(replay.position(ply), ucis, replay.sanLine(ply, ucis), ply, material));
    }

    // The same for the engine's best line from the position after `game`.
    private static List<Found> missed(String game, String line, boolean material) {
        ReplayedGame replay = GameReplay.replayGame(GameEngine.STANDARD_BACK_RANK, false, TestGames.moves(game));
        int ply = replay.positions().size() - 1;
        List<String> ucis = Arrays.asList(line.split(" "));
        return withoutGists(Motifs.missed(replay.position(ply), ucis, replay.sanLine(ply, ucis), ply, material));
    }

    private static List<Found> one(Motif motif, boolean missed, String fact) {
        return List.of(new Found(new Tag(motif, missed), fact, null));
    }

    // Most tests are about the tag and the fact; the gists have a test of their own.
    private static List<Found> withoutGists(List<Found> found) {
        return found.stream().map(f -> new Found(f.tag(), f.fact(), null)).toList();
    }

    private static String gist(boolean missed, String game, String line, boolean material) {
        ReplayedGame replay = GameReplay.replayGame(GameEngine.STANDARD_BACK_RANK, false, TestGames.moves(game));
        int ply = replay.positions().size() - 1;
        List<String> ucis = Arrays.asList(line.split(" "));
        List<Found> found = missed
                ? Motifs.missed(replay.position(ply), ucis, replay.sanLine(ply, ucis), ply, material)
                : Motifs.allowed(replay.position(ply), ucis, replay.sanLine(ply, ucis), ply, material);
        return found.getFirst().gist();
    }

    @Test
    void everyMotifIsAlsoToldWithoutSquaresOrMoveNumbers() {
        assertEquals("White left a queen hanging: it was taken for nothing by Black's bishop.",
                gist(false, "e2e4 d7d5", "d1g4 c8g4", true));
        assertEquals("Black missed a hanging queen: it could have been taken for nothing by Black's bishop.",
                gist(true, "e2e4 d7d5 d1g4", "c8g4", true));
        assertEquals("Black allowed a fork: White's knight attacked Black's queen and rook at once.",
                gist(false, TWO_KNIGHTS, "f8e7 g5f7 d7d5 f7d8", true));
        assertEquals("White missed a fork: the knight could have attacked Black's queen and rook at once.",
                gist(true, TWO_KNIGHTS + " f8e7", "g5f7 d7d5 f7d8", true));
        assertEquals("White allowed a pin: the knight was pinned to the queen by Black's bishop.",
                gist(false, "e2e4 e7e5 g1f3 d7d6", "b1c3 c8g4 f1e2", true));
        assertEquals("White moved a pinned knight, and Black's bishop took the queen behind it.",
                gist(false, "e2e4 e7e5 g1f3 d7d6 d2d4 c8g4", "f3e5 g4d1", true));
        assertEquals("Black overlooked a pin: the pawn was pinned to the king by White's queen and could not take back.",
                gist(false, "e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4 b8d7 d1e2", "g8f6 e4d6", false));
        assertEquals("Black allowed a back-rank mate: the king was shut in by its own pieces.",
                gist(false, BACK_RANK, "d7g4 e2e8", false));
        assertEquals("White missed a back-rank mate: Black's king was shut in by its own pieces.",
                gist(true, BACK_RANK + " d7g4", "e2e8", false));
        assertEquals("MISSED_FORK", new Tag(Motif.FORK, true).code());
        assertEquals("BACK_RANK", new Tag(Motif.BACK_RANK, false).code());
    }

    @Test
    void aPieceTakenForNothingWasLeftHanging() {
        assertEquals(one(Motif.HANGING_PIECE, false,
                        "Hanging piece: 2.Qg4 leaves White's queen on g4 to be taken for nothing by Black's bishop on c8."),
                allowed("e2e4 d7d5", "d1g4 c8g4", true));
        // 3.Nxe5? Nxe5: a knight for a pawn.
        assertEquals(one(Motif.HANGING_PIECE, false,
                        "Hanging piece: 3.Nxe5 leaves White's knight on e5 to be taken for nothing by Black's knight on c6."),
                allowed("e2e4 e7e5 g1f3 b8c6", "f3e5 c6e5", true));
    }

    @Test
    void anExchangeLeavesNothingHanging() {
        // 4.Bxc6 dxc6: the bishop is taken, but it took a knight first.
        assertEquals(List.of(), allowed("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6", "b5c6 d7c6", true));
    }

    @Test
    void aPieceTakenAtThePriceOfABiggerOneWasNotHanging() {
        // The Elephant Trap: 6.Nxd5 Nxd5 7.Bxd8. The knight is lost, but not for nothing.
        assertEquals(List.of(), allowed("d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5 b8d7 c4d5 e6d5", "c3d5 f6d5 g5d8 f8b4", true));
    }

    @Test
    void aPieceThatCouldBeTakenForNothingWasMissed() {
        // 2.Qg4 and now 2...a6?: the queen was there for the taking.
        assertEquals(one(Motif.HANGING_PIECE, true,
                        "Missed hanging piece: White's queen on g4 could be taken for nothing by Black's bishop on c8."),
                missed("e2e4 d7d5 d1g4", "c8g4", true));
    }

    @Test
    void aKnightAttackingQueenAndRookIsAFork() {
        // 4...Be7? 5.Nxf7, and the queen has no square to go to.
        assertEquals(one(Motif.FORK, false,
                        "Fork: with 5.Nxf7 White's knight on f7 attacks Black's queen on d8 and rook on h8 at once."),
                allowed(TWO_KNIGHTS, "f8e7 g5f7 d7d5 f7d8", true));
        assertEquals(one(Motif.FORK, true,
                        "Missed fork: with 5.Nxf7 White's knight on f7 would attack Black's queen on d8 and rook on h8 at once."),
                missed(TWO_KNIGHTS + " f8e7", "g5f7 d7d5 f7d8", true));
    }

    @Test
    void noForkWhenTheLineDoesNotCashIn() {
        assertEquals(List.of(), allowed(TWO_KNIGHTS, "f8e7 g5f7 d7d5 c4d5", true));
    }

    @Test
    void noForkWhenTheForkingPieceIsSimplyTaken() {
        // The same knight jump with the bishop on e2: nothing defends f7, and the king takes.
        assertEquals(List.of(), allowed("e2e4 e7e5 g1f3 b8c6 f1e2 g8f6 f3g5", "h7h6 g5f7 e8f7", true));
    }

    @Test
    void aBishopPinningAKnightToTheQueenIsAPin() {
        assertEquals(one(Motif.PIN, false,
                        "Pin: after 3...Bg4 White's knight on f3 is pinned to the queen on d1 by Black's bishop on g4."),
                allowed("e2e4 e7e5 g1f3 d7d6", "b1c3 c8g4 f1e2", true));
    }

    @Test
    void aKingThatTakesCanLeaveAPieceInFrontOfItPinned() {
        // The Fried Liver: 5...Nxd5 6.Nxf7 Kxf7, and the knight on d5 can't move.
        assertEquals(one(Motif.PIN, false,
                        "Pin: after 6.Nxf7 Kxf7 Black's knight on d5 is pinned to the king on f7 by White's bishop on c4."),
                allowed(TWO_KNIGHTS + " d7d5 e4d5", "f6d5 g5f7 e8f7 d1f3", true));
    }

    @Test
    void aPieceBlockingACheckIsNotCountedAsPinned() {
        // 3.Bb5+ Nc6: true as far as it goes, but a normal move and no tactic.
        assertEquals(List.of(), allowed("e2e4 e7e5 g1f3", "d7d6 f1b5 b8c6", true));
    }

    @Test
    void movingAPinnedPieceLosesWhatStoodBehindIt() {
        // 3...Bg4 4.Nxe5?? Bxd1. Told as a pin, not as a queen left hanging.
        assertEquals(one(Motif.PIN, false,
                        "Pin: White's knight on f3 was pinned to the queen on d1 by Black's bishop on g4, "
                                + "so after 4.Nxe5 the bishop takes the queen."),
                allowed("e2e4 e7e5 g1f3 d7d6 d2d4 c8g4", "f3e5 g4d1", true));
    }

    @Test
    void aPinnedPawnCannotTakeTheMatingKnight() {
        // The Caro-Kann smothered mate. A mate is at stake, so nothing about material is looked for.
        assertEquals(one(Motif.PIN, false,
                        "Pin: Black's pawn on e7 is pinned to the king on e8 by White's queen on e2, "
                                + "so it cannot take on d6 after 6.Nd6#."),
                allowed("e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4 b8d7 d1e2", "g8f6 e4d6", false));
    }

    @Test
    void aRookMatingAKingBehindItsPawnsIsABackRankMate() {
        assertEquals(one(Motif.BACK_RANK, false,
                        "Back-rank mate: 13.Re8# is checkmate because Black's king on g8 is shut in on the back rank by its own pieces."),
                allowed(BACK_RANK, "d7g4 e2e8", false));
        assertEquals(one(Motif.BACK_RANK, true,
                        "Missed back-rank mate: 13.Re8# would be checkmate because Black's king on g8 is shut in on the back rank by its own pieces."),
                missed(BACK_RANK + " d7g4", "e2e8", false));
    }

    @Test
    void aMateElsewhereIsNotABackRankMate() {
        // Scholar's mate: the queen mates on f7.
        assertEquals(List.of(), allowed("e2e4 e7e5 f1c4 b8c6 d1h5", "g8f6 h5f7", false));
    }
}

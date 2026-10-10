package pvt.phgg.chess.server.analysis;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.GameReplay;
import pvt.phgg.chess.GameReplay.ReplayedGame;
import pvt.phgg.chess.server.analysis.GameAnalysisService.LoadedGame;
import pvt.phgg.chess.server.analysis.KeyMoves.KeyMove;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Choosing the moves to comment on, the facts given for them, and the check on the answer. */
class CommentaryUnitTest {

    // 1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6?? 4.Qxf7#
    private static final String SCHOLARS_MATE = "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7";

    private static PositionEval cp(int centipawns, String pv) {
        return new PositionEval(centipawns, null, pv.split(" ")[0], pv, 16);
    }

    private static PositionEval mate(int mateIn, String pv) {
        return new PositionEval(null, mateIn, pv == null ? null : pv.split(" ")[0], pv, 16);
    }

    // evals by ply; a missing ply has no evaluation.
    private static LoadedGame game(String uciMoves, Map<Integer, PositionEval> evals, boolean whiteHuman, boolean blackHuman) {
        ReplayedGame replay = GameReplay.replayGame(GameEngine.STANDARD_BACK_RANK, false, TestGames.moves(uciMoves));
        Map<String, PositionEval> byEpd = new HashMap<>();
        evals.forEach((ply, eval) -> byEpd.put(replay.positions().get(ply).epd(), eval));
        return new LoadedGame(replay, byEpd, whiteHuman, blackHuman);
    }

    private static LoadedGame scholarsMate(boolean whiteHuman, boolean blackHuman) {
        Map<Integer, PositionEval> evals = new HashMap<>();
        evals.put(0, cp(30, "e2e4 e7e5"));
        evals.put(1, cp(30, "e7e5 g1f3"));
        evals.put(2, cp(30, "g1f3 b8c6"));
        evals.put(3, cp(10, "b8c6 d1h5"));
        evals.put(4, cp(20, "d1h5 g8f6"));
        evals.put(5, cp(40, "g7g6 h5f3 g8f6"));
        evals.put(6, mate(1, "h5f7"));
        evals.put(7, mate(0, null));
        return game(SCHOLARS_MATE, evals, whiteHuman, blackHuman);
    }

    // --- KeyMoves ---

    @Test
    void decisiveMoveIsWhereTheWinnerTookOverForGood() {
        List<PositionEval> evals = new ArrayList<>(Arrays.asList(
                cp(20, "a2a3"), cp(400, "a2a3"), cp(0, "a2a3"), cp(-30, "a2a3"), cp(-500, "a2a3"), null, mate(-2, "a2a3")));
        // Black is clearly winning from ply 4 on; the gap at ply 5 doesn't break the run.
        assertEquals(3, KeyMoves.decisiveMove(evals));
    }

    @Test
    void noDecisiveMoveWhenTheEndIsLevel() {
        assertEquals(-1, KeyMoves.decisiveMove(List.of(cp(20, "a2a3"), cp(500, "a2a3"), cp(10, "a2a3"))));
    }

    @Test
    void selectsTheBlunderAndTheDecidingMove() {
        List<KeyMove> moves = KeyMoves.select(scholarsMate(true, true), 12);
        assertEquals(List.of(new KeyMove(5, MoveClassification.BLUNDER, true)), moves);
    }

    @Test
    void botMistakesAreSkippedUnlessTheyDecideTheGame() {
        // 1.e4 e5 2.Qh5? (a mistake by White, the bot) ... Black is the human.
        Map<Integer, PositionEval> evals = new HashMap<>();
        evals.put(0, cp(30, "e2e4"));
        evals.put(1, cp(30, "e7e5"));
        evals.put(2, cp(30, "g1f3"));
        evals.put(3, cp(-150, "b8c6"));
        evals.put(4, cp(-150, "g2g3"));
        LoadedGame game = game("e2e4 e7e5 d1h5 b8c6", evals, false, true);
        assertEquals(List.of(), KeyMoves.select(game, 12));
        assertEquals(1, KeyMoves.select(game("e2e4 e7e5 d1h5 b8c6", evals, true, true), 12).size());
    }

    @Test
    void keepsTheCostliestWhenThereAreTooMany() {
        // White loses ground on every move: win-chance drops of about 0.18, 0.45 and 0.36.
        Map<Integer, PositionEval> evals = new HashMap<>();
        evals.put(0, cp(0, "e2e4"));
        evals.put(1, cp(-100, "e7e5"));
        evals.put(2, cp(-100, "d2d4"));
        evals.put(3, cp(-400, "d7d5"));
        evals.put(4, cp(-400, "d2d4"));
        evals.put(5, cp(-1500, "d7d5"));
        LoadedGame game = game("a2a3 a7a6 b2b3 b7b6 c2c3", evals, true, false);
        List<KeyMove> picked = KeyMoves.select(game, 2);
        // The decisive move (ply 3 onwards is clearly lost) always gets one; then the largest drop.
        assertEquals(List.of(2, 4), picked.stream().map(KeyMove::index).toList());
    }

    // --- MoveFacts ---

    @Test
    void factsForAMoveThatAllowsMate() {
        MoveContext context = MoveFacts.describe(scholarsMate(true, true), 5, MoveClassification.BLUNDER, true);
        assertEquals("3...Nf6", context.move());
        assertEquals("Black", context.mover());
        assertEquals("+0.4 (about equal)", context.evalBefore());
        assertEquals("White can force checkmate in 1", context.evalAfter());
        // With a mate on the board, nothing about material.
        assertEquals(List.of(
                "3...Nf6 allows White to force checkmate in 1.",
                "White's best reply is 4.Qxf7#.",
                "The engine's best move was g6, with the line 3...g6 4.Qf3 Nf6."), context.facts());
    }

    @Test
    void factsForACaptureThatLosesMaterialBack() {
        // 1.e4 d5 2.exd5 Qxd5 3.Nc3: White takes, Black takes back.
        Map<Integer, PositionEval> evals = new HashMap<>();
        evals.put(2, cp(40, "e4d5 d8d5"));
        evals.put(3, cp(0, "d8d5 b1c3"));
        LoadedGame game = game("e2e4 d7d5 e4d5 d8d5", evals, true, true);
        MoveContext context = MoveFacts.describe(game, 2, MoveClassification.GOOD, false);
        assertTrue(context.facts().contains("2.exd5 captures Black's pawn on d5."), context.facts().toString());
        assertTrue(context.facts().contains("2.exd5 was the engine's best move."), context.facts().toString());
        // Black's best reply takes the pawn back, so the trade nets out: no material fact.
        assertTrue(context.facts().contains("Black's best reply is 2...Qxd5 3.Nc3."), context.facts().toString());
        assertTrue(context.facts().stream().noneMatch(f -> f.contains("of material.") && f.contains("that line")),
                context.facts().toString());
    }

    @Test
    void factsNameWhatALineCapturesAndSettleTheExchange() {
        // 1.e4 d5 2.Qg4??: the reply line ends with Black's bishop on g4 still to be taken back...
        // except that nothing can take it, so White is a queen down.
        Map<Integer, PositionEval> evals = new HashMap<>();
        evals.put(2, cp(70, "e4d5 g8f6"));
        evals.put(3, cp(-960, "c8g4 e4e5"));
        MoveContext context = MoveFacts.describe(game("e2e4 d7d5 d1g4", evals, true, true), 2,
                MoveClassification.BLUNDER, false);
        assertTrue(context.facts().contains("After 2.Qg4, White's queen on g4 can be taken by Black's bishop on c8 and is not defended."),
                context.facts().toString());
        assertTrue(context.facts().contains("Along 2.Qg4 and that line, Black takes the queen on g4."), context.facts().toString());
        assertTrue(context.facts().contains("Once the captures are over, White ends up 9 pawns' worth of material behind where it was before 2.Qg4."),
                context.facts().toString());
    }

    @Test
    void anExchangeCutOffByTheLineIsFinished() {
        // 1.e4 e5 2.Nf3 Nc6 3.Bc4 Nd4 4.Nxe5? with a line that stops at 4...Qg5 5.Nxf7, where White
        // has taken two pawns. But the g2 pawn hangs to the queen, so settling the position takes it:
        // White is one pawn up, not two.
        Map<Integer, PositionEval> evals = new HashMap<>();
        evals.put(6, cp(120, "f3d4 e5d4"));
        evals.put(7, cp(-10, "d8g5 e5f7"));
        MoveContext context = MoveFacts.describe(game("e2e4 e7e5 g1f3 b8c6 f1c4 c6d4 f3e5", evals, true, true), 6,
                MoveClassification.MISTAKE, false);
        assertTrue(context.facts().contains("Along 4.Nxe5 and that line, White takes the pawn on e5 and the pawn on f7."),
                context.facts().toString());
        assertTrue(context.facts().contains("Once the captures are over, White ends up a pawn's worth of material ahead, so what makes 4.Nxe5 bad is not material but the position that follows."),
                context.facts().toString());
    }

    @Test
    void theOpeningIsNamedOnlyWhileTheGameIsStillInIt() {
        // 1.e4 e5 2.Bc4 is the last named position; after it come rook-pawn and knight-pawn moves.
        LoadedGame game = game("e2e4 e7e5 f1c4 h7h6 a2a3 a7a6 h2h3 b7b6 b2b3 g7g6 g2g3 d7d6 d2d3", Map.of(), true, true);
        assertEquals("Bishop's Opening (C23)", MoveFacts.describe(game, 3, MoveClassification.GOOD, false).opening());
        assertEquals("Bishop's Opening (C23)", MoveFacts.describe(game, 10, MoveClassification.GOOD, false).opening());
        assertNull(MoveFacts.describe(game, 11, MoveClassification.GOOD, false).opening());
        // The move that reaches a named position is already in that opening.
        assertEquals("King's Pawn Game (C20)", MoveFacts.describe(game, 1, MoveClassification.GOOD, false).opening());
    }

    @Test
    void numberedLines() {
        assertEquals("1.e4 e5 2.Nf3", MoveFacts.numbered(List.of("e4", "e5", "Nf3"), 0));
        assertEquals("3...Nf6 4.Qxf7#", MoveFacts.numbered(List.of("Nf6", "Qxf7#"), 5));
    }

    // --- CommentaryPrompt ---

    @Test
    void promptListsTheFactsAndAsksForTheVerdict() {
        MoveContext context = MoveFacts.describe(scholarsMate(true, true), 5, MoveClassification.BLUNDER, false);
        String prompt = CommentaryPrompt.user(context);
        assertTrue(prompt.startsWith("Move: 3...Nf6, played by Black.\nOpening: Bishop's Opening (C23).\nEngine verdict: blunder.\n"), prompt);
        assertTrue(prompt.contains("- White's best reply is 4.Qxf7#.\n"), prompt);
        assertTrue(prompt.endsWith("Explain why 3...Nf6 was a blunder."), prompt);
        assertFalse(prompt.contains("/"), "no FEN in the prompt");

        String decisive = CommentaryPrompt.user(new MoveContext(6, "3...Nf6", "Nf6", "Black", MoveClassification.BLUNDER,
                true, null, null, "+0.4", "mate", List.of(), List.of()));
        assertTrue(decisive.endsWith("Explain why 3...Nf6 decided the game."), decisive);
        assertFalse(decisive.contains("Opening:"), "no opening line without an opening");
        String inaccuracy = CommentaryPrompt.user(new MoveContext(6, "3...Nf6", "Nf6", "Black", MoveClassification.INACCURACY,
                false, null, null, "+0.4", "+0.9", List.of(), List.of()));
        assertTrue(inaccuracy.endsWith("was an inaccuracy."), inaccuracy);
    }

    @Test
    void promptGivesAMatchingNoteAfterTheFacts() {
        MoveContext context = MoveFacts.describe(scholarsMate(true, true), 5, MoveClassification.BLUNDER, false);
        assertFalse(CommentaryPrompt.user(context).contains("well-known pattern"), "no note unless one was found");

        String prompt = CommentaryPrompt.user(context.withOpeningNote("Scholar's Mate: the queen takes on f7."));
        assertTrue(prompt.contains("""
                - The engine's best move was g6, with the line 3...g6 4.Qf3 Nf6.

                The facts above match a well-known pattern: Scholar's Mate: the queen takes on f7.
                Explain the move with this pattern, giving its name if it has one, and still say what concretely happens.

                Explain why 3...Nf6 was a blunder."""), prompt);
    }

    @Test
    void theMovesOfTheEngineLinesAreKeptForMatchingNotes() {
        MoveContext context = MoveFacts.describe(scholarsMate(true, true), 5, MoveClassification.BLUNDER, false);
        assertEquals("Nf6", context.san());
        assertEquals(List.of("Qxf7#", "g6", "Qf3", "Nf6"), context.lineMoves());
    }

    // --- OpeningNotes ---

    private static MoveContext played(String san, String... lineMoves) {
        return new MoveContext(6, "3..." + san, san, "Black", MoveClassification.BLUNDER, false, "Bishop's Opening (C23)",
                null, "+0.4", "mate", List.of(), List.of(lineMoves));
    }

    @Test
    void aNoteAppliesWhenTheMoveAndTheLinesShowItsKeyMoves() {
        OpeningNotes.Note scholars = new OpeningNotes.Note("Bishop's Opening", "Qh5 Qf3 Nf6 Qxf7#", "Scholar's Mate");
        assertTrue(OpeningNotes.confirmedBy(scholars, played("Nf6", "Qxf7#", "g6")));
        assertTrue(OpeningNotes.confirmedBy(scholars, played("Nf6", "d3", "Qxf7+")), "check marks don't count");
        assertFalse(OpeningNotes.confirmedBy(scholars, played("Nf6", "d3", "Nc3")), "the lines show none of it");
        assertFalse(OpeningNotes.confirmedBy(scholars, played("d6", "Qxf7#")), "the move played isn't part of it");
        assertFalse(OpeningNotes.confirmedBy(scholars, played("Nf6", "Nf6", "d3")),
                "the played move turning up again in a line confirms nothing");
    }

    @Test
    void everyNoteHasAKnownOpeningAndKeyMovesInSan() throws Exception {
        List<OpeningNotes.Note> notes = OpeningNotes.load();
        assertTrue(notes.size() >= 20, "notes: " + notes.size());
        List<String> names;
        try (var in = getClass().getResourceAsStream("/openings/openings.tsv")) {
            names = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                    .map(line -> line.split("\t")[1]).toList();
        }
        for (OpeningNotes.Note note : notes) {
            assertTrue(note.opening().equals("*") || names.stream().anyMatch(name -> name.contains(note.opening())),
                    "no opening is called " + note.opening());
            for (String move : note.moves().split(" ")) {
                assertTrue(move.matches("([KQRBN][a-h1-8]?x?[a-h][1-8]|[a-h](x[a-h])?[1-8](=[QRBN])?|O-O(-O)?)[+#]?"),
                        "not a move in SAN: " + move + " (" + note.opening() + ")");
            }
            assertTrue(note.text().length() <= 300, "too long for a prompt: " + note.text());
        }
    }

    @Test
    void checkAcceptsAGroundedAnswer() {
        String prompt = "Move: 3...Nf6\nFacts:\n- White's best reply is 4.Qxf7#.\n- The engine's best move was g6.";
        assertEquals(Optional.of("Nf6 blocks nothing, and White mates at once with Qxf7#. Playing g6 first covers the threat."),
                CommentaryPrompt.check("  \"Nf6 blocks nothing, and White mates at once with Qxf7#.\n Playing g6 first covers the threat.\" ", prompt));
    }

    @Test
    void checkRejectsSquaresNotInTheFacts() {
        String prompt = "Facts:\n- White's best reply is 4.Qxf7#.";
        assertEquals(Optional.empty(), CommentaryPrompt.check("The bishop on c4 supports Qxf7#.", prompt));
    }

    @Test
    void checkAllowsTheSquaresOfAConfirmedNote() {
        MoveContext context = MoveFacts.describe(scholarsMate(true, true), 5, MoveClassification.BLUNDER, false);
        String answer = "This is Scholar's Mate; Black had to put the queen on e7.";
        assertEquals(Optional.empty(), CommentaryPrompt.check(answer, CommentaryPrompt.user(context)));
        assertTrue(CommentaryPrompt.check(answer, CommentaryPrompt.user(context.withOpeningNote(
                "Scholar's Mate: the queen takes on f7. Black stops it with the queen on e7."))).isPresent());
    }

    @Test
    void checkRejectsEmptyLongAndRamblingAnswers() {
        assertEquals(Optional.empty(), CommentaryPrompt.check("   ", "x"));
        assertEquals(Optional.empty(), CommentaryPrompt.check("word ".repeat(100), "x"));
        assertEquals(Optional.empty(), CommentaryPrompt.check("One. Two. Three. Four.", "x"));
    }

    @Test
    void checkDropsLeakedThinking() {
        assertEquals(Optional.of("It loses a pawn."),
                CommentaryPrompt.check("<think>maybe h7 is weak</think>It loses a pawn.", "x"));
    }
}

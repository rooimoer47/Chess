package pvt.phgg.chess;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class Chess960GeneratorTest {

    @Test
    void generatedPositionsAlwaysSatisfyChess960Invariants() {
        Random rnd = new Random(42); // seeded for reproducibility
        for (int i = 0; i < 5000; i++) {
            assertValidBackRank(Chess960Generator.generateBackRank(rnd));
        }
    }

    @Test
    void standardBackRankSatisfiesTheSameInvariants() {
        // Standard chess is a legal Chess960 position; it must pass the exact same checks.
        assertValidBackRank(GameEngine.STANDARD_BACK_RANK);
    }

    @Test
    void generatorProducesVariety() {
        // Over many draws it should not keep emitting the same arrangement.
        Random rnd = new Random(7);
        String first = Chess960Generator.generateBackRank(rnd);
        boolean sawDifferent = false;
        for (int i = 0; i < 100; i++) {
            if (!Chess960Generator.generateBackRank(rnd).equals(first)) {
                sawDifferent = true;
                break;
            }
        }
        assertTrue(sawDifferent, "Generator should produce more than one arrangement");
    }

    private static void assertValidBackRank(String rank) {
        assertEquals(8, rank.length(), "Back rank must be 8 files: " + rank);

        int king = -1;
        int firstRook = -1;
        int lastRook = -1;
        int lightBishop = -1;
        int darkBishop = -1;
        int queens = 0;
        int knights = 0;

        for (int col = 0; col < 8; col++) {
            switch (rank.charAt(col)) {
                case 'K' -> king = col;
                case 'R' -> {
                    if (firstRook == -1) firstRook = col;
                    lastRook = col;
                }
                case 'B' -> {
                    if (col % 2 == 0) lightBishop = col; else darkBishop = col;
                }
                case 'Q' -> queens++;
                case 'N' -> knights++;
                default -> fail("Unexpected piece '" + rank.charAt(col) + "' in " + rank);
            }
        }

        assertTrue(lightBishop >= 0 && darkBishop >= 0,
                "Bishops must be on opposite-coloured squares: " + rank);
        assertEquals(1, queens, "Exactly one queen: " + rank);
        assertEquals(2, knights, "Exactly two knights: " + rank);
        assertTrue(firstRook < king && king < lastRook,
                "King must stand strictly between the two rooks: " + rank);
    }
}

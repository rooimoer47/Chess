package pvt.phgg.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates a random legal Chess960 (Fischer Random) back rank as an 8-character string over
 * files a-h. Every generated position satisfies the Chess960 rules: the two bishops sit on
 * opposite-coloured squares, and the king stands strictly between the two rooks (so both castling
 * rights are always available). Standard chess ("RNBQKBNR") is one — astronomically unlikely —
 * output of the same rules.
 */
public final class Chess960Generator {

    private static final int FILES = 8;

    private Chess960Generator() {
    }

    public static String generateBackRank() {
        return generateBackRank(ThreadLocalRandom.current());
    }

    // Package-private seam so tests can drive it with a seeded Random for reproducibility.
    static String generateBackRank(Random rnd) {
        char[] files = new char[FILES];
        for (int i = 0; i < FILES; i++) {
            files[i] = '.';
        }

        placeOnParity(files, 0, rnd); // one bishop on a light square (even file index)
        placeOnParity(files, 1, rnd); // one bishop on a dark square (odd file index)
        placeOnEmpty(files, 'Q', rnd);
        placeOnEmpty(files, 'N', rnd);
        placeOnEmpty(files, 'N', rnd);

        // Exactly three empty files remain, in ascending order; king goes between the rooks.
        List<Integer> remaining = emptyFiles(files);
        files[remaining.get(0)] = 'R';
        files[remaining.get(1)] = 'K';
        files[remaining.get(2)] = 'R';

        return new String(files);
    }

    private static void placeOnParity(char[] files, int parity, Random rnd) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < FILES; i++) {
            if (files[i] == '.' && i % 2 == parity) {
                candidates.add(i);
            }
        }
        files[candidates.get(rnd.nextInt(candidates.size()))] = 'B';
    }

    private static void placeOnEmpty(char[] files, char piece, Random rnd) {
        List<Integer> empties = emptyFiles(files);
        files[empties.get(rnd.nextInt(empties.size()))] = piece;
    }

    private static List<Integer> emptyFiles(char[] files) {
        List<Integer> empties = new ArrayList<>();
        for (int i = 0; i < FILES; i++) {
            if (files[i] == '.') {
                empties.add(i);
            }
        }
        return empties;
    }
}

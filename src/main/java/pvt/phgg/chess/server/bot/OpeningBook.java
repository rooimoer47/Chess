package pvt.phgg.chess.server.bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// Coordinate encoding: each move = fromRow+fromCol+toRow+toCol as a 4-digit string.
// History key = concatenated moves from both sides since game start.
final class OpeningBook {

    private static final Map<String, List<int[]>> BOOK = new HashMap<>();

    static {
        // ── First moves (white) ────────────────────────────────────────
        add("",           1,4,3,4,  1,3,3,3);          // e4, d4

        // ── Black responses to 1.e4 ───────────────────────────────────
        add("1434",       6,4,4,4,  6,2,4,2,  6,4,5,4,  6,2,5,2);
        //                e5        c5        e6 (French) c6 (Caro-Kann)

        // ── Black responses to 1.d4 ───────────────────────────────────
        add("1333",       6,3,4,3,  7,6,5,5);           // d5, Nf6

        // ── White 2nd moves ───────────────────────────────────────────
        add("14346444",   0,6,2,5);                      // 1.e4 e5  → Nf3
        add("14346242",   0,6,2,5);                      // 1.e4 c5  → Nf3
        add("14346454",   1,3,3,3);                      // 1.e4 e6  → d4
        add("14346252",   1,3,3,3);                      // 1.e4 c6  → d4
        add("13336343",   1,2,3,2);                      // 1.d4 d5  → c4
        add("13337655",   1,2,3,2);                      // 1.d4 Nf6 → c4

        // ── Black 2nd moves ───────────────────────────────────────────
        add("143464440625",   7,1,5,2);                  // 1.e4 e5 2.Nf3   → Nc6
        add("143462420625",   6,3,5,3,  7,1,5,2);        // 1.e4 c5 2.Nf3   → d6, Nc6
        add("143464541333",   6,3,4,3);                  // 1.e4 e6 2.d4    → d5
        add("143462521333",   6,3,4,3);                  // 1.e4 c6 2.d4    → d5
        add("133363431232",   6,4,5,4,  6,2,5,2);        // 1.d4 d5 2.c4    → e6, c6
        add("133376551232",   6,6,5,6,  6,4,5,4);        // 1.d4 Nf6 2.c4   → g6, e6

        // ── White 3rd moves ───────────────────────────────────────────
        // 1.e4 e5 2.Nf3 Nc6 → Bc4 (Italian) or Bb5 (Ruy Lopez)
        add("1434644406257152",   0,5,3,2,  0,5,4,1);
        // 1.e4 c5 2.Nf3 d6 → d4
        add("1434624206256353",   1,3,3,3);
        // 1.e4 c5 2.Nf3 Nc6 → d4, Bb5
        add("1434624206257152",   1,3,3,3,  0,5,4,1);
        // 1.e4 e6 2.d4 d5 → Nc3
        add("1434645413336343",   0,1,2,2);
        // 1.e4 c6 2.d4 d5 → Nc3, e5
        add("1434625213336343",   0,1,2,2,  3,4,4,4);
        // 1.d4 d5 2.c4 e6 → Nc3
        add("1333634312326454",   0,1,2,2);
        // 1.d4 d5 2.c4 c6 → Nf3
        add("1333634312326252",   0,6,2,5);
        // 1.d4 Nf6 2.c4 g6 → Nc3
        add("1333765512326656",   0,1,2,2);
        // 1.d4 Nf6 2.c4 e6 → Nc3, Nf3
        add("1333765512326454",   0,1,2,2,  0,6,2,5);

        // ── Black 3rd moves ───────────────────────────────────────────
        // 1.e4 e5 2.Nf3 Nc6 3.Bc4 → Nf6 (Two Knights), Bc5 (Giuoco Piano)
        add("14346444062571520532",   7,6,5,5,  7,5,4,2);
        // 1.e4 e5 2.Nf3 Nc6 3.Bb5 → a6 (Morphy), Nf6 (Berlin)
        add("14346444062571520541",   6,0,5,0,  7,6,5,5);

        // ── White 4th moves ───────────────────────────────────────────
        // Giuoco Piano: 3...Bc5 → c3
        add("143464440625715205327542",   1,2,2,2);
        // Two Knights: 3...Nf6 → Ng5
        add("143464440625715205327655",   2,5,4,6);
        // Ruy Lopez 3...a6 → Ba4
        add("143464440625715205416050",   4,1,3,0);
        // Ruy Lopez Berlin 3...Nf6 → 0-0
        add("143464440625715205417655",   0,4,0,6);
    }

    @SuppressWarnings("java:S2245")
    static int[] lookup(String moveHistory) {
        List<int[]> candidates = BOOK.get(moveHistory);
        if (candidates == null || candidates.isEmpty()) return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private static void add(String key, int... moves) {
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i + 3 < moves.length; i += 4) {
            list.add(new int[]{moves[i], moves[i + 1], moves[i + 2], moves[i + 3]});
        }
        BOOK.put(key, list);
    }

    private OpeningBook() {}
}

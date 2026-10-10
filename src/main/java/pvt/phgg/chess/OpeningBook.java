package pvt.phgg.chess;

import pvt.phgg.chess.piece.APiece;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Named openings, found by exact position. The data is the Lichess chess-openings set
// (github.com/lichess-org/chess-openings, CC0): its a.tsv to e.tsv joined under one header, each
// row an ECO code, a name and the moves in SAN. Every line is played out here and stored under the
// EPD of its last position, so a game that reaches the position by another move order still matches.
// Standard chess only.
public final class OpeningBook {

    public record Opening(String eco, String name) {
    }

    private static final String RESOURCE = "/openings/openings.tsv";

    private final Map<String, Opening> byEpd;
    private final String startEpd = FenSerializer.toEpd(new GameEngine(), false);

    private OpeningBook(Map<String, Opening> byEpd) {
        this.byEpd = Map.copyOf(byEpd);
    }

    // Built once, on first use: playing out the lines takes a moment.
    public static OpeningBook standard() {
        return Holder.BOOK;
    }

    private static final class Holder {
        private static final OpeningBook BOOK = load();
    }

    static OpeningBook load() {
        Map<String, Opening> byEpd = new HashMap<>();
        try (InputStream in = OpeningBook.class.getResourceAsStream(RESOURCE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.readLine();  // the header
            String row;
            while ((row = reader.readLine()) != null) {
                String[] columns = row.split("\t");
                byEpd.put(epdAfter(columns[2]), new Opening(columns[0], columns[1]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new OpeningBook(byEpd);
    }

    public int size() {
        return byEpd.size();
    }

    public Optional<Opening> find(String epd) {
        return Optional.ofNullable(byEpd.get(epd));
    }

    // The opening a game is in at `ply`: the last named position it passed through up to there.
    // Empty if the game didn't start from the standard position or never reached a named one.
    public Optional<Opening> at(List<String> epds, int ply) {
        if (epds.isEmpty() || !epds.get(0).equals(startEpd)) return Optional.empty();
        for (int i = Math.min(ply, epds.size() - 1); i > 0; i--) {
            Opening opening = byEpd.get(epds.get(i));
            if (opening != null) return Optional.of(opening);
        }
        return Optional.empty();
    }

    // The ply of the last named position in the game, or 0 if there is none.
    public int lastNamedPly(List<String> epds) {
        if (epds.isEmpty() || !epds.get(0).equals(startEpd)) return 0;
        for (int i = epds.size() - 1; i > 0; i--) {
            if (byEpd.containsKey(epds.get(i))) return i;
        }
        return 0;
    }

    // `pgn` is numbered SAN, e.g. "1. e4 e5 2. Nf3".
    private static String epdAfter(String pgn) {
        GameEngine engine = new GameEngine();
        for (String token : pgn.split(" ")) {
            if (token.endsWith(".")) continue;
            engine = play(engine, token, pgn);
        }
        return FenSerializer.toEpd(engine, false);
    }

    // SAN names the destination, so only the pieces that can move there need trying; the one whose
    // move is written the same way is the move meant.
    private static GameEngine play(GameEngine engine, String san, String pgn) {
        String wanted = stripCheck(san);
        boolean castle = wanted.startsWith("O-O");
        Position target = castle ? null : UciMoveCodec.parseSquare(wanted.substring(wanted.length() - 2));
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                APiece piece = engine.getPiece(row, col);
                if (!piece.isPositionOccupied() || piece.isWhite() != engine.isWhiteTurn()) continue;
                if (castle && !piece.isKing()) continue;
                Position from = new Position(row, col);
                for (Position to : engine.getLegalMoves(from)) {
                    if (!castle && (to.getRow() != target.getRow() || to.getCol() != target.getCol())) continue;
                    SanEncoder.AppliedMove applied = SanEncoder.apply(engine,
                            UciMoveCodec.square(row, col) + UciMoveCodec.square(to.getRow(), to.getCol()));
                    if (stripCheck(applied.san()).equals(wanted)) return applied.after();
                }
            }
        }
        throw new IllegalStateException("Opening line \"" + pgn + "\": no legal move " + san);
    }

    private static String stripCheck(String san) {
        return san.endsWith("+") || san.endsWith("#") ? san.substring(0, san.length() - 1) : san;
    }
}

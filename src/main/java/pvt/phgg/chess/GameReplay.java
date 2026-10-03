package pvt.phgg.chess;

import java.util.ArrayList;
import java.util.List;

// Replays a recorded game and describes every position it passed through, in the form the engine
// analysis needs: FEN for Stockfish, EPD for the cache key, and the move played from it in UCI and SAN.
public final class GameReplay {

    public record RecordedMove(Position from, Position to, PromotionChoice promotion) {
    }

    // `playedUci`/`playedSan` describe the move played from this position; null for the final position.
    public record ReplayedPosition(int ply, String fen, String epd, String playedUci, String playedSan) {
    }

    // A move shown to a person: SAN plus the squares to draw it between. A castle goes from the
    // king's square to its destination, even in Chess960 where UCI says king-onto-rook.
    public record MoveDisplay(String san, String from, String to) {
    }

    // The replayed positions, plus a snapshot of the engine at each one so other moves (Stockfish's
    // best move and line) can be put into SAN from the same position.
    public static final class ReplayedGame {
        private final List<ReplayedPosition> positions;
        private final List<GameEngine> snapshots;

        private ReplayedGame(List<ReplayedPosition> positions, List<GameEngine> snapshots) {
            this.positions = List.copyOf(positions);
            this.snapshots = snapshots;
        }

        public List<ReplayedPosition> positions() {
            return positions;
        }

        // Describes `uci` as played from the position at `ply`. Throws if it isn't legal there.
        public MoveDisplay describe(int ply, String uci) {
            SanEncoder.AppliedMove applied = SanEncoder.apply(snapshots.get(ply), uci);
            return new MoveDisplay(applied.san(),
                    UciMoveCodec.square(applied.from().getRow(), applied.from().getCol()),
                    UciMoveCodec.square(applied.to().getRow(), applied.to().getCol()));
        }

        // SAN for a line of UCI moves played from the position at `ply`; stops at the first bad move.
        public List<String> sanLine(int ply, List<String> uciMoves) {
            return SanEncoder.line(snapshots.get(ply), uciMoves);
        }
    }

    private GameReplay() {
    }

    public static List<ReplayedPosition> replay(String backRank, boolean chess960, List<RecordedMove> moves) {
        return replayGame(backRank, chess960, moves).positions();
    }

    public static ReplayedGame replayGame(String backRank, boolean chess960, List<RecordedMove> moves) {
        GameEngine engine = new GameEngine(backRank);
        List<GameEngine> snapshots = new ArrayList<>();
        List<String> fens = new ArrayList<>();
        List<String> epds = new ArrayList<>();
        List<String> ucis = new ArrayList<>();
        List<String> sans = new ArrayList<>();
        snapshots.add(new GameEngine(engine));
        fens.add(FenSerializer.toFen(engine, 0, chess960));
        epds.add(FenSerializer.toEpd(engine, chess960));

        for (int i = 0; i < moves.size(); i++) {
            RecordedMove move = moves.get(i);
            MoveResult result = engine.applyMove(move.from(), move.to());
            if (result.type() == MoveResult.Type.INVALID) {
                throw new IllegalArgumentException("Recorded move " + (i + 1) + " is not legal in its position");
            }
            PromotionChoice promotion = null;
            if (result.type() == MoveResult.Type.PROMOTION_NEEDED) {
                if (move.promotion() == null) {
                    throw new IllegalArgumentException("Recorded move " + (i + 1) + " promotes without a promotion choice");
                }
                promotion = move.promotion();
                engine.applyPromotion(engine.getLastMoveTarget(), promotion);
            }
            String uci = UciMoveCodec.encodeLastMove(engine, move.from(), promotion, chess960);
            ucis.add(uci);
            sans.add(SanEncoder.apply(snapshots.get(i), uci).san());
            snapshots.add(new GameEngine(engine));
            fens.add(FenSerializer.toFen(engine, i + 1, chess960));
            epds.add(FenSerializer.toEpd(engine, chess960));
        }

        List<ReplayedPosition> positions = new ArrayList<>();
        for (int ply = 0; ply < fens.size(); ply++) {
            boolean hasMove = ply < ucis.size();
            positions.add(new ReplayedPosition(ply, fens.get(ply), epds.get(ply),
                    hasMove ? ucis.get(ply) : null, hasMove ? sans.get(ply) : null));
        }
        return new ReplayedGame(positions, snapshots);
    }
}

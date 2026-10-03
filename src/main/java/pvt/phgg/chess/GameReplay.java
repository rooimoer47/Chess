package pvt.phgg.chess;

import java.util.ArrayList;
import java.util.List;

// Replays a recorded game and describes every position it passed through, in the form the engine
// analysis needs: FEN for Stockfish, EPD for the cache key, and the move played from it in UCI.
public final class GameReplay {

    public record RecordedMove(Position from, Position to, PromotionChoice promotion) {
    }

    // `playedUci` is the move played from this position; null for the final position.
    public record ReplayedPosition(int ply, String fen, String epd, String playedUci) {
    }

    private GameReplay() {
    }

    public static List<ReplayedPosition> replay(String backRank, boolean chess960, List<RecordedMove> moves) {
        GameEngine engine = new GameEngine(backRank);
        List<String> fens = new ArrayList<>();
        List<String> epds = new ArrayList<>();
        List<String> ucis = new ArrayList<>();
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
            ucis.add(UciMoveCodec.encodeLastMove(engine, move.from(), promotion, chess960));
            fens.add(FenSerializer.toFen(engine, i + 1, chess960));
            epds.add(FenSerializer.toEpd(engine, chess960));
        }

        List<ReplayedPosition> positions = new ArrayList<>();
        for (int ply = 0; ply < fens.size(); ply++) {
            positions.add(new ReplayedPosition(ply, fens.get(ply), epds.get(ply),
                    ply < ucis.size() ? ucis.get(ply) : null));
        }
        return positions;
    }
}

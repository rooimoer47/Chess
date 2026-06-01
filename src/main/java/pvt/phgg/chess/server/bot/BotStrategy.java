package pvt.phgg.chess.server.bot;

import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.Position;

public interface BotStrategy {
    /**
     * Choose a move for the given side. Returns [from, to], or null if no legal moves exist.
     */
    Position[] chooseMove(GameEngine engine, boolean isWhite);
}

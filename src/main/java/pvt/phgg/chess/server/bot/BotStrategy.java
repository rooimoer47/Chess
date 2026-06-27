package pvt.phgg.chess.server.bot;

import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.Position;

public interface BotStrategy {
    Position[] chooseMove(GameEngine engine, boolean isWhite);

    default void recordOpponentMove(int fromRow, int fromCol, int toRow, int toCol) {}
}

package pvt.phgg.chess.server.bot;

import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.Position;
import pvt.phgg.chess.piece.APiece;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomBotStrategy implements BotStrategy {

    @Override
    public Position[] chooseMove(GameEngine engine, boolean isWhite) {
        List<Position[]> moves = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                APiece piece = engine.getPiece(row, col);
                if (piece.isPositionOccupied() && piece.isWhite() == isWhite) {
                    Position from = new Position(row, col);
                    for (Position to : engine.getLegalMoves(from)) {
                        moves.add(new Position[]{from, to});
                    }
                }
            }
        }
        if (moves.isEmpty()) return new Position[0];
        return moves.get(ThreadLocalRandom.current().nextInt(moves.size()));
    }
}

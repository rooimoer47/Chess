package pvt.phgg.chess.server.bot;

import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.MoveResult;
import pvt.phgg.chess.Position;
import pvt.phgg.chess.piece.APiece;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomBotStrategy implements BotStrategy {

    @Override
    public Position[] chooseMove(GameEngine engine, boolean isWhite) {
        List<Position[]> allMoves = new ArrayList<>();
        List<Position[]> checkMoves = new ArrayList<>();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                APiece piece = engine.getPiece(row, col);
                if (piece.isPositionOccupied() && piece.isWhite() == isWhite) {
                    Position from = new Position(row, col);
                    for (Position to : engine.getLegalMoves(from)) {
                        Position[] move = {from, to};
                        MoveResult.Type result = engine.peekMoveResult(from, to);
                        if (result == MoveResult.Type.CHECKMATE) {
                            return move;
                        }
                        if (result == MoveResult.Type.CHECK) {
                            checkMoves.add(move);
                        }
                        allMoves.add(move);
                    }
                }
            }
        }

        if (allMoves.isEmpty()) return new Position[0];
        if (!checkMoves.isEmpty()) {
            return checkMoves.get(ThreadLocalRandom.current().nextInt(checkMoves.size()));
        }
        return allMoves.get(ThreadLocalRandom.current().nextInt(allMoves.size()));
    }
}

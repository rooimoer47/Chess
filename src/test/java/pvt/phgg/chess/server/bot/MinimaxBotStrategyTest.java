package pvt.phgg.chess.server.bot;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.Chess960Generator;
import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.Position;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MinimaxBotStrategyTest {

    @Test
    void bookEnabled_standardStart_playsAKnownBookOpening() {
        MinimaxBotStrategy bot = new MinimaxBotStrategy(1, true);
        Position[] move = bot.chooseMove(new GameEngine(), true);

        // Opening book's first white move is e4 (1,4)->(3,4) or d4 (1,3)->(3,3).
        assertTrue(isMove(move, 1, 4, 3, 4) || isMove(move, 1, 3, 3, 3),
                "Book-enabled bot should play a book opening on the standard start");
    }

    @Test
    void bookDisabled_chess960_alwaysReturnsALegalMove() {
        // Regression guard for the silent-hang bug: the opening book is hardcoded for the standard
        // back rank, so if it were consulted in a Chess960 game it could return a move that is
        // illegal in the actual position. With the book gated off, every move must be legal.
        for (int i = 0; i < 40; i++) {
            String backRank = Chess960Generator.generateBackRank();
            GameEngine engine = new GameEngine(backRank);
            MinimaxBotStrategy bot = new MinimaxBotStrategy(1, false);

            Position[] move = bot.chooseMove(engine, true);
            assertEquals(2, move.length, "Bot should produce a move for " + backRank);

            List<Position> legal = engine.getLegalMoves(move[0]);
            assertTrue(legal.stream().anyMatch(p -> p.getRow() == move[1].getRow()
                            && p.getCol() == move[1].getCol()),
                    "Bot move " + describe(move) + " must be legal for back rank " + backRank);
        }
    }

    private static boolean isMove(Position[] move, int fr, int fc, int tr, int tc) {
        return move.length == 2
                && move[0].getRow() == fr && move[0].getCol() == fc
                && move[1].getRow() == tr && move[1].getCol() == tc;
    }

    private static String describe(Position[] move) {
        return "(" + move[0].getRow() + "," + move[0].getCol() + ")->("
                + move[1].getRow() + "," + move[1].getCol() + ")";
    }
}

package pvt.phgg.chess;

import pvt.phgg.chess.player.HumanPlayer;
import pvt.phgg.chess.player.Player;

import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class Chess {

    private final Board board;

    private Chess() {
        Player whitePlayer = new HumanPlayer(true);
        Player blackPlayer = new HumanPlayer(false);
        board = new Board("Chess", whitePlayer, blackPlayer);
        board.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    private void start() {
        board.setVisible(true);
    }

    public static void main(String [] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Chess chess = new Chess();
                chess.start();
            } catch (Exception e) {
                System.err.println("Error initializing chess game: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}

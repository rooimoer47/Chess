package pvt.phgg.chess;

import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class Chess {

    private final Board board;

    private Chess() {
        GameEngine engine = new GameEngine();
        board = new Board("Chess", engine);
        board.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    private void start() {
        board.setVisible(true);
    }

    public static void main(String[] args) {
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

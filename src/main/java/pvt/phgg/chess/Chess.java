package pvt.phgg.chess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class Chess {

    private static final Logger LOGGER = LoggerFactory.getLogger(Chess.class);

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
                LOGGER.error("Error initializing chess game", e);
            }
        });
    }
}

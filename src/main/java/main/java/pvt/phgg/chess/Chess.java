package main.java.pvt.phgg.chess;

import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class Chess {

    Player whitePlayer;
    Player blackPlayer;

    public Chess() {
        SwingUtilities.invokeLater(this::initializeBoard);
    }

    private void initializeBoard() {
        whitePlayer = new HumanPlayer(true);
        blackPlayer = new HumanPlayer(false);
        Board board = new Board("Chess", whitePlayer, blackPlayer);
        board.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        board.setVisible(true);
        System.out.println("Window size after setVisible: " + board.getWidth() + "x" + board.getHeight());
    }

    public static void main(String [] args) {
        SwingUtilities.invokeLater(Chess::new);
    }
}

package main.java.pvt.phgg.chess;

import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class Chess {

    public static void main(String [] args) {
        SwingUtilities.invokeLater(() -> {
            Board board = new Board("Chess");
            board.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            board.setVisible(true);
            System.out.println("Window size after setVisible: " + board.getWidth() + "x" + board.getHeight());
        });
    }
}

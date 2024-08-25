package main.java.pvt.phgg.chess;

import javax.swing.*;
import java.awt.*;

public class Board extends JPanel{
    private static final int X = 20;
    private static final int Y = X;
    private static final int WIDTH = 50;
    private static final int HEIGHT = WIDTH;
    private static final int BOARD_SIZE = 8;

    private static int [][] BOARD = new int[BOARD_SIZE][BOARD_SIZE];

    public void init() {
        SwingUtilities.invokeLater(this::createBoard);
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0 ; col < BOARD_SIZE; col++) {
                BOARD[row][col] = 0;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // draw here
        g.drawRect(X, Y, WIDTH, HEIGHT);
        int curX = X;
        int curY = Y;
        int colorFlip = 2;
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0 ; col < BOARD_SIZE; col++) {
                if (colorFlip == 1) {
                    g.setColor(Color.BLACK);
                } else {
                    g.setColor(Color.WHITE);
                }
                colorFlip = 3 - colorFlip;
                g.fillRect(curX, curY, WIDTH, HEIGHT);
                curX += WIDTH;
            }
            curY += HEIGHT;
            curX = X;
            colorFlip = 3 - colorFlip;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        // make gui big enough
        return new Dimension(WIDTH * 9, HEIGHT * 9);
    }

    private void createBoard() {
        JFrame frameBoard = new JFrame("Chess");
        Board panelBoard = new Board();
        frameBoard.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frameBoard.getContentPane().add(panelBoard);
        frameBoard.pack();
        frameBoard.setLocationByPlatform(true);
        frameBoard.setVisible(true);
    }
}

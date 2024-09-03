package main.java.pvt.phgg.chess;

import javax.swing.*;
import java.awt.*;

public class Board extends JPanel{
    private static final int X = 20;
    private static final int Y = X;
    private static final int WIDTH = 50;
    private static final int HEIGHT = WIDTH;
    private static final int BOARD_SIZE = 8;

    private static final APiece [][] BOARD = new APiece[BOARD_SIZE][BOARD_SIZE];

    public void init() {
        SwingUtilities.invokeLater(this::createBoard);
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0 ; col < BOARD_SIZE; col++) {
                if (row == 1) {
                    BOARD[row][col] = new Pawn(Color.WHITE);
                } else if (row == 7) {
                    BOARD[row][col] = new Pawn(Color.BLACK);
                } else {
                    BOARD[row][col] = null;
                }
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
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0 ; col < BOARD_SIZE; col++) {
                if ((row + col) % 2 == 0) {
                    g.setColor(Color.WHITE);
                }
                else {
                    g.setColor(Color.BLACK);
                }
                g.fillRect(curX, curY, WIDTH, HEIGHT);
                if (BOARD[row][col] != null) {
                    g.drawImage(BOARD[row][col].getImage(), curX, curY, WIDTH, HEIGHT, this);
                }
                curX += WIDTH;
            }
            curY += HEIGHT;
            curX = X;
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

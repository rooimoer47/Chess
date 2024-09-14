package main.java.pvt.phgg.chess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Board extends JFrame {

    private static final Logger LOGGER = LoggerFactory.getLogger(Board.class);
    private static final int SQUARE_SIZE_PIXELS = 50;
    private static final int BOARD_SIZE = 8;

    private static final APiece [][] BOARD = new APiece[BOARD_SIZE][BOARD_SIZE];

    public Board(String title) {
        super(title);
        LOGGER.trace("Initializing board");
        setLayout(new GridLayout(BOARD_SIZE, BOARD_SIZE));
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0 ; col < BOARD_SIZE; col++) {
                if (row == 1) {
                    BOARD[row][col] = new Pawn(Color.WHITE);
                } else if (row == 6) {
                    BOARD[row][col] = new Pawn(Color.BLACK);
                } else {
                    BOARD[row][col] = null;
                }
                // paint
                int finalRow = row;
                int finalCol = col;
                JPanel square = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        if ((finalRow + finalCol) % 2 == 0) {
                            g.setColor(Color.WHITE);
                        }
                        else {
                            g.setColor(Color.BLACK);
                        }
                        g.fillRect(0, 0, getWidth(), getHeight());

                        if (BOARD[finalRow][finalCol] != null) {
                            g.drawImage(BOARD[finalRow][finalCol].getImage(), 0, 0, getWidth(), getHeight(), this);
                        }
                    }
                };
                square.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        System.out.println("i am a clicky boi");
                    }
                });
                square.setPreferredSize(new Dimension(SQUARE_SIZE_PIXELS, SQUARE_SIZE_PIXELS));
                add(square);
            }
        }
        pack();
        setLocationRelativeTo(null);
        System.out.println("Window size after pack: " + getWidth() + "x" + getHeight());
    }

    @Override
    public Dimension getPreferredSize() {
        // make gui big enough
        return new Dimension(SQUARE_SIZE_PIXELS * 9, HEIGHT * 9);
    }
}

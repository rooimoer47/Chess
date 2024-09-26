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
import java.awt.image.BufferedImage;
import java.util.List;

public class Board extends JFrame {

    private static final Logger LOGGER = LoggerFactory.getLogger(Board.class);
    private static final int SQUARE_SIZE_PIXELS = 50;
    private static final int BOARD_SIZE = 8;

    private static final APiece [][] BOARD = new APiece[BOARD_SIZE][BOARD_SIZE];

    private static boolean PIECE_SELECTED = false;
    private static int PIECE_SELECTED_ROW = -1;
    private static int PIECE_SELECTED_COL = -1;

    public Board(String title) {
        super(title);
        LOGGER.trace("Initializing board");
        setLayout(new GridLayout(BOARD_SIZE, BOARD_SIZE));
        int frameSize = BOARD_SIZE * SQUARE_SIZE_PIXELS;
        setSize(frameSize, frameSize);
        setResizable(false);
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0 ; col < BOARD_SIZE; col++) {
                if (row == 0 && (col == 0 || col == 7)) {
                    BOARD[row][col] = new Rook(new Position(row, col), true);
                } else if (row == 0 && (col == 1 || col == 6)) {
                    BOARD[row][col] = new Knight(new Position(row, col), true);
                } else if (row == 0 && (col == 2 || col == 5)) {
                    BOARD[row][col] = new Bishop(new Position(row, col), true);
                } else if (row == 0 && col == 3) {
                    BOARD[row][col] = new Queen(new Position(row, col), true);
                } else if (row == 0) {
                    BOARD[row][col] = new King(new Position(row, col), true);
                } else if (row == 1) {
                    BOARD[row][col] = new Pawn(new Position(row, col), true);
                } else if (row == 6) {
                    BOARD[row][col] = new Pawn(new Position(row, col), false);
                } else if (row == 7 && (col == 0 || col == 7)) {
                    BOARD[row][col] = new Rook(new Position(row, col), false);
                } else if (row == 7 && (col == 1 || col == 6)) {
                    BOARD[row][col] = new Knight(new Position(row, col), false);
                } else if (row == 7 && (col == 2 || col == 5)) {
                    BOARD[row][col] = new Bishop(new Position(row, col), false);
                } else if (row == 7 && col == 3) {
                    BOARD[row][col] = new Queen(new Position(row, col), false);
                } else if (row == 7) {
                    BOARD[row][col] = new King(new Position(row, col), false);
                } else {
                    BOARD[row][col] = new APiece() {
                        @Override
                        public BufferedImage getImage() {
                            return null;
                        }

                        @Override
                        public List<Position> getValidPositions(APiece[][] board) {
                            return null;
                        }
                    };
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

                        if (BOARD[finalRow][finalCol].getImage() != null) {
                            g.drawImage(BOARD[finalRow][finalCol].getImage(), 0, 0, getWidth(), getHeight(), this);
                        }

                        if (BOARD[finalRow][finalCol].isMarked()) {
                            g.setColor(Color.BLUE);
                            g.fillOval(getWidth()/2, getWidth()/2, getWidth()/10, getHeight()/10);
                        }
                    }
                };
                square.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        System.out.println("clicked row " + finalRow + " col " + finalCol);
                        if (PIECE_SELECTED) {
                            // a piece was selected, so move it
                            APiece selectedPiece = BOARD[PIECE_SELECTED_ROW][PIECE_SELECTED_COL];
                            List<Position> moves = selectedPiece.getValidPositions(BOARD);
                            Position selectedPosition = new Position(finalRow, finalCol);

                            for (Position pos : moves) {
                                if (pos.equals(selectedPosition)) {
                                    // found valid move
                                    BOARD[finalRow][finalCol] = selectedPiece;
                                    BOARD[finalRow][finalCol].moved();
                                    BOARD[PIECE_SELECTED_ROW][PIECE_SELECTED_COL] = new APiece() {
                                        @Override
                                        public BufferedImage getImage() {
                                            return null;
                                        }

                                        @Override
                                        public List<Position> getValidPositions(APiece[][] board) {
                                            return null;
                                        }
                                    };
                                    repaint();
                                    BOARD[finalRow][finalCol].setCurrentPosition(selectedPosition);
                                    break;
                                }
                            }
                            selectedPiece.toggleSelected();
                            for (int row = 0; row < BOARD_SIZE; row++) {
                                for (int col = 0; col < BOARD_SIZE; col++) {
                                    BOARD[row][col].unMark();
                                }
                            }
                            repaint();
                            PIECE_SELECTED_ROW = -1;
                            PIECE_SELECTED_COL = -1;
                            PIECE_SELECTED = false;
                        }
                        else {
                            // no piece selected before click, so select it if it is a piece
                            if (BOARD[finalRow][finalCol].getImage() != null) {
                                BOARD[finalRow][finalCol].toggleSelected();
                                List<Position> moves = BOARD[finalRow][finalCol].getValidPositions(BOARD);
                                for (Position pos : moves) {
                                    BOARD[pos.getRow()][pos.getCol()].mark();
                                }
                                repaint();
                                PIECE_SELECTED_ROW = finalRow;
                                PIECE_SELECTED_COL = finalCol;
                                PIECE_SELECTED = true;
                            }
                        }
                    }
                });
                square.setPreferredSize(new Dimension(SQUARE_SIZE_PIXELS, SQUARE_SIZE_PIXELS));
                add(square);
            }
        }
        setLocationRelativeTo(null);
        System.out.println("Window size after pack: " + getWidth() + "x" + getHeight());
    }

    public static boolean isOccupied(Position position) {
        return BOARD[position.getRow()][position.getCol()].getImage() != null;
    }

    public static boolean isOnBoard(Position pos) {
        return pos.getRow() >= 0 && pos.getRow() < 8 && pos.getCol() >= 0 && pos.getCol() < 8;
    }

    @Override
    public Dimension getPreferredSize() {
        // make gui big enough
        return new Dimension(SQUARE_SIZE_PIXELS * 9, HEIGHT * 9);
    }
}

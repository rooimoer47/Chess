package pvt.phgg.chess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pvt.phgg.chess.piece.*;
import pvt.phgg.chess.player.Player;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class Board extends JFrame {

    private static final Logger LOGGER = LoggerFactory.getLogger(Board.class);
    private static final int SQUARE_SIZE_PIXELS = 50;
    private static final int BOARD_SIZE = 8;
    private static final Color LIGHT_SQUARE_COLOR = Color.WHITE;
    private static final Color DARK_SQUARE_COLOR = Color.BLACK;
    private static final Color MARKER_COLOR = Color.BLUE;

    private final APiece[][] board = new APiece[BOARD_SIZE][BOARD_SIZE];
    private final BoardState boardState = new BoardState();

    private boolean pieceSelected = false;
    private int selectedRow = -1;
    private int selectedCol = -1;

    private Player currentPlayer;
    private final Player whitePlayer;
    private final Player blackPlayer;

    private final Metrics metrics;

    public Board(String title, Player whitePlayer, Player blackPlayer) {
        super(title);
        LOGGER.trace("Initializing board");
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        currentPlayer = whitePlayer;
        this.metrics = new Metrics();

        initializeBoard();
        createBoardUI();

        setLocationRelativeTo(null);
        System.out.println("Window size after pack: " + getWidth() + "x" + getHeight());
    }

    private void initializeBoard() {
        board[0][0] = new Rook(new Position(0, 0), true);
        board[0][7] = new Rook(new Position(0, 7), true);
        board[0][1] = new Knight(new Position(0, 1), true);
        board[0][6] = new Knight(new Position(0, 6), true);
        board[0][2] = new Bishop(new Position(0, 2), true);
        board[0][5] = new Bishop(new Position(0, 5), true);
        board[0][3] = new Queen(new Position(0, 3), true);
        board[0][4] = new King(new Position(0, 4), true);

        for (int col = 0; col < BOARD_SIZE; col++) {
            board[1][col] = new Pawn(new Position(1, col), true);
        }

        for (int row = 2; row < 6; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                board[row][col] = createEmptyPiece(row, col);
            }
        }

        for (int col = 0; col < BOARD_SIZE; col++) {
            board[6][col] = new Pawn(new Position(6, col), false);
        }

        board[7][0] = new Rook(new Position(7, 0), false);
        board[7][7] = new Rook(new Position(7, 7), false);
        board[7][1] = new Knight(new Position(7, 1), false);
        board[7][6] = new Knight(new Position(7, 6), false);
        board[7][2] = new Bishop(new Position(7, 2), false);
        board[7][5] = new Bishop(new Position(7, 5), false);
        board[7][3] = new Queen(new Position(7, 3), false);
        board[7][4] = new King(new Position(7, 4), false);
    }

    private void createBoardUI() {
        setLayout(new GridLayout(BOARD_SIZE, BOARD_SIZE));
        int frameSize = BOARD_SIZE * SQUARE_SIZE_PIXELS;
        setSize(frameSize, frameSize);
        setResizable(false);

        for (int row = BOARD_SIZE - 1; row >= 0; row--) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                add(createSquarePanel(row, col));
            }
        }
    }

    private JPanel createSquarePanel(int row, int col) {
        JPanel square = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                g.setColor((row + col) % 2 == 0 ? LIGHT_SQUARE_COLOR : DARK_SQUARE_COLOR);
                g.fillRect(0, 0, getWidth(), getHeight());

                BufferedImage image = board[row][col].getImage();
                if (image != null) {
                    g.drawImage(image, 0, 0, getWidth(), getHeight(), this);
                }

                if (board[row][col].isMarked()) {
                    g.setColor(MARKER_COLOR);
                    g.fillOval(getWidth()/2, getHeight()/2, getWidth()/10, getHeight()/10);
                }
            }
        };

        square.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleSquareClick(row, col);
            }
        });

        square.setPreferredSize(new Dimension(SQUARE_SIZE_PIXELS, SQUARE_SIZE_PIXELS));
        return square;
    }

    private void handleSquareClick(int row, int col) {
        LOGGER.trace("Clicked row {} col {}", row, col);

        if (pieceSelected) {
            handleMoveAttempt(row, col);
        } else {
            handlePieceSelection(row, col);
        }
    }

    private void handlePieceSelection(int row, int col) {
        // First click: no piece selected before click, so select it if it is a piece
        APiece clickedPiece = board[row][col];

        if (clickedPiece.getImage() != null && clickedPiece.isWhite() == currentPlayer.isWhite()) {
            clickedPiece.toggleSelected();

            List<Position> moves = clickedPiece.getLegalPositions(board, boardState);
            for (Position pos : moves) {
                board[pos.getRow()][pos.getCol()].mark();
            }

            repaint();
            selectedRow = row;
            selectedCol = col;
            pieceSelected = true;
            metrics.recordSelection();
        }
    }

    private void handleMoveAttempt(int row, int col) {
        // Second click: a piece was selected, so move it if valid
        APiece selectedPiece = board[selectedRow][selectedCol];
        List<Position> moves = selectedPiece.getLegalPositions(board);
        Position targetPosition = new Position(row, col);

        for (Position pos : moves) {
            if (pos.equals(targetPosition)) {
                // Found valid move
                metrics.recordMove(currentPlayer.isWhite(), boardState.isOccupied(board, pos));
                move(selectedPiece, selectedPiece.getCurrentPosition(), pos);

                // Check for pawn promotion
                if (selectedPiece.isPawn() &&
                        ((selectedPiece.isWhite() && row == 7) || (!selectedPiece.isWhite() && row == 0))) {
                    showPromotionDialog(targetPosition, selectedPiece.isWhite());
                }

                switchPlayers();
                break;
            }
        }
        selectedPiece.toggleSelected();
        clearAllMarkers();
        repaint();

        selectedRow = -1;
        selectedCol = -1;
        pieceSelected = false;
    }

    private void switchPlayers() {
        currentPlayer = currentPlayer.isWhite() ? blackPlayer : whitePlayer;

        // Check if game is over
        if (!boardState.canMove(board, currentPlayer.isWhite())) {
            String message;
            String title = "Game Over";
            if (boardState.isInCheck(board, currentPlayer.isWhite())) {
                String winner = currentPlayer.isWhite() ? "Black" : "White";
                message = String.format("Checkmate! %s player wins.", winner);
            } else {
                message = "Draw - Stalemate!";
            }
            LOGGER.info(message);
            repaint();
            JOptionPane.showMessageDialog(this,
                    message,
                    title,
                    JOptionPane.INFORMATION_MESSAGE);
            LOGGER.info("Total move count: " + metrics.getTotalMoveCount());
            LOGGER.info("Total white pieces captured: " + metrics.getWhitePiecesCaptured());
            LOGGER.info("Total black pieces captured: " + metrics.getBlackPiecesCaptured());

        }
    }

    private void showPromotionDialog(Position pos, boolean isWhite) {
        Promo promo = new Promo(pos, isWhite);
        promo.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                repaint();
            }
        });
        promo.setVisible(true);
    }

    private void clearAllMarkers() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                board[r][c].unMark();
            }
        }
    }

    private APiece createEmptyPiece(int row, int col) {
        return new APiece(new Position(row, col)) {
            @Override
            public BufferedImage getImage() {
                return null;
            }

            @Override
            public List<Position> getValidPositions(APiece[][] board, BoardState boardState) {
                return new ArrayList<>();
            }
        };
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SQUARE_SIZE_PIXELS * BOARD_SIZE, HEIGHT * BOARD_SIZE);
    }

    public void move(APiece piece, Position from, Position to) {
        board[to.getRow()][to.getCol()] = piece;
        board[to.getRow()][to.getCol()].moved();
        clear(from);
        if (to.isEnPassant()) {
            int capturedPawnRow = to.getRow() + (piece.isWhite() ? -1 : 1);
            clear(new Position(capturedPawnRow, to.getCol()));
        }
        if (to.isCastle()) {
            handleCastling(to);
        }

        board[to.getRow()][to.getCol()].setCurrentPosition(to);

        if (piece.isPawn() && Math.abs(from.getRow() - to.getRow()) == 2) {
            ((Pawn) piece).setJumped();
        } else if (piece.isPawn()) {
            ((Pawn) piece).unsetJumped();
        }
    }

    private void handleCastling(Position kingPosition) {
        int row = kingPosition.getRow();
        int col = kingPosition.getCol();
        if (col > 4) {
            APiece rook = board[row][7];
            move(rook, rook.getCurrentPosition(), new Position(row, col - 1));
        }
        else {
            APiece rook = board[row][0];
            move(rook, rook.getCurrentPosition(), new Position(row, col + 1));
        }
    }

    public void clear(Position pos) {
        LOGGER.trace("Clearing row {} col {}", pos.getRow(), pos.getCol());
        board[pos.getRow()][pos.getCol()] = createEmptyPiece(pos.getRow(), pos.getCol());
    }

    class Promo extends JFrame {
        private static final int PROMOTION_WIDTH = 200;
        private static final int PROMOTION_HEIGHT = 100;

        public Promo(Position pos, boolean isWhite) {
            super("Promote Pawn");
            this.setLayout(new GridLayout(1, 4));
            setSize(PROMOTION_WIDTH, PROMOTION_HEIGHT);
            setResizable(false);

            APiece[] options = {
                    new Knight(pos, isWhite),
                    new Bishop(pos, isWhite),
                    new Rook(pos, isWhite),
                    new Queen(pos, isWhite)
            };

            for (APiece option : options) {
                add(createPromotionOption(option, pos));
            }

            setLocationRelativeTo(Board.this);
        }

        private JPanel createPromotionOption(APiece piece, Position pos) {
            JPanel square = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(Color.GRAY);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.drawImage(piece.getImage(), 0, 0, getWidth(), getHeight(), this);
                }
            };

            square.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    board[pos.getRow()][pos.getCol()] = piece;
                    dispose();
                }
            });

            square.setPreferredSize(new Dimension(SQUARE_SIZE_PIXELS, SQUARE_SIZE_PIXELS));
            return square;
        }
    }

    private static class Metrics {
        private final long gameStartTimeMillis;
        private long lastMoveTimestampMillis;

        private int totalMoveCount;
        private int whiteMoveCount;
        private int blackMoveCount;

        private int whitePiecesCaptured;
        private int blackPiecesCaptured;

        private int selectionsMade;

        public Metrics() {
            this.gameStartTimeMillis = System.currentTimeMillis();
            this.lastMoveTimestampMillis = this.gameStartTimeMillis;
            this.totalMoveCount = 0;
            this.whiteMoveCount = 0;
            this.blackMoveCount = 0;
            this.whitePiecesCaptured = 0;
            this.blackPiecesCaptured = 0;
            this.selectionsMade = 0;
        }

        public void recordMove(boolean isWhite, boolean captureOccurred) {
            long now = System.currentTimeMillis();
            long moveDuration = now - lastMoveTimestampMillis;
            lastMoveTimestampMillis = now;

            totalMoveCount++;
            if (isWhite) {
                whiteMoveCount++;
                if (captureOccurred) blackPiecesCaptured++; // White captured a black piece
            } else {
                blackMoveCount++;
                if (captureOccurred) whitePiecesCaptured++; // Black captured a white piece
            }
             LOGGER.debug("Move #{} ({}): {}ms. Capture: {}", totalMoveCount, isWhite ? "W" : "B", moveDuration, captureOccurred);
        }

        public void recordSelection() {
            this.selectionsMade++;
        }

        public long getGameElapsedTimeMillis() {
            return System.currentTimeMillis() - gameStartTimeMillis;
        }

        public int getTotalMoveCount() { return totalMoveCount; }
        public int getWhitePiecesCaptured() { return whitePiecesCaptured; }
        public int getBlackPiecesCaptured() { return blackPiecesCaptured; }

        public String getCurrentStatus() {
            long elapsedSeconds = getGameElapsedTimeMillis() / 1000;
            return String.format(
                    "Time: %ds | Moves: %d (W:%d B:%d) | Captures by W:%d, by B:%d | Selections: %d",
                    elapsedSeconds,
                    totalMoveCount, whiteMoveCount, blackMoveCount,
                    blackPiecesCaptured, // # of black pieces captured by white
                    whitePiecesCaptured, // # of white pieces captured by black
                    selectionsMade
            );
        }

        @Override
        public String toString() {
            return getCurrentStatus();
        }
    }
}

package pvt.phgg.chess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pvt.phgg.chess.piece.APiece;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Board extends JFrame {

    private static final Logger LOGGER = LoggerFactory.getLogger(Board.class);
    private static final int SQUARE_SIZE_PIXELS = 50;
    private static final int BOARD_SIZE = 8;
    private static final Color LIGHT_SQUARE_COLOR = Color.WHITE;
    private static final Color DARK_SQUARE_COLOR = Color.BLACK;
    private static final Color MARKER_COLOR = Color.BLUE;

    private final GameEngine engine;

    private Position selectedPosition = null;
    private final Set<Position> markedPositions = new HashSet<>();

    private final Metrics metrics = new Metrics();

    public Board(String title, GameEngine engine) {
        super(title);
        this.engine = engine;
        createBoardUI();
        setLocationRelativeTo(null);
        System.out.println("Window size after pack: " + getWidth() + "x" + getHeight());
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

                APiece piece = engine.getPiece(row, col);
                Position pos = new Position(row, col);
                boolean isSelected = pos.equals(selectedPosition);
                BufferedImage image = piece.getImage(isSelected);
                if (image != null) {
                    g.drawImage(image, 0, 0, getWidth(), getHeight(), this);
                }

                if (markedPositions.contains(pos)) {
                    g.setColor(MARKER_COLOR);
                    g.fillOval(getWidth() / 2, getHeight() / 2, getWidth() / 10, getHeight() / 10);
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
        if (selectedPosition != null) {
            handleMoveAttempt(row, col);
        } else {
            handlePieceSelection(row, col);
        }
    }

    private void handlePieceSelection(int row, int col) {
        Position clicked = new Position(row, col);
        List<Position> moves = engine.getLegalMoves(clicked);

        if (!moves.isEmpty()) {
            selectedPosition = clicked;
            markedPositions.addAll(moves);
            metrics.recordSelection();
            repaint();
        }
    }

    private void handleMoveAttempt(int row, int col) {
        Position target = new Position(row, col);
        MoveResult result = engine.applyMove(selectedPosition, target);

        clearSelection();

        if (!result.isValid()) {
            repaint();
            return;
        }

        metrics.recordMove(result.wasWhiteMove(), result.isCaptureOccurred());
        repaint();

        if (result.getType() == MoveResult.Type.PROMOTION_NEEDED) {
            showPromotionDialog(target);
            return;
        }

        if (result.isGameOver()) {
            showGameOverDialog(result);
        }
    }

    private void clearSelection() {
        selectedPosition = null;
        markedPositions.clear();
    }

    private void showPromotionDialog(Position pos) {
        Promo promo = new Promo(pos);
        promo.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                repaint();
            }
        });
        promo.setVisible(true);
    }

    private void showGameOverDialog(MoveResult result) {
        String message;
        if (result.getType() == MoveResult.Type.CHECKMATE) {
            String winner = result.wasWhiteMove() ? "White" : "Black";
            message = String.format("Checkmate! %s player wins.", winner);
        } else {
            message = "Draw - Stalemate!";
        }
        LOGGER.info(message);
        LOGGER.info("Total move count: {}", metrics.getTotalMoveCount());
        LOGGER.info("Total white pieces captured: {}", metrics.getWhitePiecesCaptured());
        LOGGER.info("Total black pieces captured: {}", metrics.getBlackPiecesCaptured());
        JOptionPane.showMessageDialog(this, message, "Game Over", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SQUARE_SIZE_PIXELS * BOARD_SIZE, SQUARE_SIZE_PIXELS * BOARD_SIZE);
    }

    // --- Promotion dialog ---

    class Promo extends JFrame {
        private static final int PROMOTION_WIDTH = 200;
        private static final int PROMOTION_HEIGHT = 100;

        public Promo(Position pos) {
            super("Promote Pawn");
            setLayout(new GridLayout(1, 4));
            setSize(PROMOTION_WIDTH, PROMOTION_HEIGHT);
            setResizable(false);

            for (PromotionChoice choice : PromotionChoice.values()) {
                add(createPromotionOption(pos, choice));
            }

            setLocationRelativeTo(Board.this);
        }

        private JPanel createPromotionOption(Position pos, PromotionChoice choice) {
            boolean isWhite = engine.getPiece(pos.getRow(), pos.getCol()).isWhite();

            // Render a preview piece without affecting game state
            APiece preview = switch (choice) {
                case QUEEN  -> new pvt.phgg.chess.piece.Queen(pos, isWhite);
                case ROOK   -> new pvt.phgg.chess.piece.Rook(pos, isWhite);
                case BISHOP -> new pvt.phgg.chess.piece.Bishop(pos, isWhite);
                case KNIGHT -> new pvt.phgg.chess.piece.Knight(pos, isWhite);
            };

            JPanel square = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(Color.GRAY);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.drawImage(preview.getImage(false), 0, 0, getWidth(), getHeight(), this);
                }
            };

            square.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    MoveResult result = engine.applyPromotion(pos, choice);
                    dispose();
                    repaint();
                    if (result.isGameOver()) {
                        showGameOverDialog(result);
                    }
                }
            });

            square.setPreferredSize(new Dimension(SQUARE_SIZE_PIXELS, SQUARE_SIZE_PIXELS));
            return square;
        }
    }

    // --- Metrics ---

    private static class Metrics {
        private final long gameStartTimeMillis = System.currentTimeMillis();
        private long lastMoveTimestampMillis = gameStartTimeMillis;

        private int totalMoveCount;
        private int whiteMoveCount;
        private int blackMoveCount;
        private int whitePiecesCaptured;
        private int blackPiecesCaptured;
        private int selectionsMade;

        public void recordMove(boolean wasWhite, boolean captureOccurred) {
            long now = System.currentTimeMillis();
            long moveDuration = now - lastMoveTimestampMillis;
            lastMoveTimestampMillis = now;

            totalMoveCount++;
            if (wasWhite) {
                whiteMoveCount++;
                if (captureOccurred) blackPiecesCaptured++;
            } else {
                blackMoveCount++;
                if (captureOccurred) whitePiecesCaptured++;
            }
            LOGGER.debug("Move #{} ({}): {}ms. Capture: {}", totalMoveCount, wasWhite ? "W" : "B", moveDuration, captureOccurred);
        }

        public void recordSelection() {
            selectionsMade++;
        }

        public int getTotalMoveCount() { return totalMoveCount; }
        public int getWhitePiecesCaptured() { return whitePiecesCaptured; }
        public int getBlackPiecesCaptured() { return blackPiecesCaptured; }
    }
}

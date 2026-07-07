package pvt.phgg.chess;

import org.junit.jupiter.api.Test;
import pvt.phgg.chess.piece.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic movement tests for Knight, Bishop, Rook, Queen, and King.
 * Uses custom board positions via the package-private GameEngine constructor.
 */
class PieceMoveTest {

    private static Position p(int row, int col) { return new Position(row, col); }

    private static GameEngine emptyBoard(boolean whiteTurn) {
        APiece[][] board = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                board[r][c] = new EmptySquare(p(r, c));
        return new GameEngine(board, whiteTurn);
    }

    private static void place(APiece[][] board, APiece piece, int row, int col) {
        board[row][col] = piece;
    }

    private static APiece[][] blankBoard() {
        APiece[][] board = new APiece[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                board[r][c] = new EmptySquare(p(r, c));
        return board;
    }

    // ---- Knight ----

    @Test
    void knight_allEightLShapedMoves_areValid() {
        int[][] targets = {{6,3},{6,5},{5,2},{5,6},{3,2},{3,6},{2,3},{2,5}};
        for (int[] t : targets) {
            APiece[][] board = blankBoard();
            board[4][4] = new Knight(p(4, 4), true);
            board[0][4] = new King(p(0, 4), true);
            board[7][4] = new King(p(7, 4), false);
            GameEngine engine = new GameEngine(board, true);
            MoveResult r = engine.applyMove(p(4, 4), p(t[0], t[1]));
            assertTrue(r.isValid(), "Knight should reach (" + t[0] + "," + t[1] + ")");
        }
    }

    @Test
    void knight_cannotMoveInStraightLine() {
        APiece[][] board = blankBoard();
        board[4][4] = new Knight(p(4, 4), true);
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        GameEngine engine = new GameEngine(board, true);

        assertFalse(engine.applyMove(p(4, 4), p(4, 6)).isValid(), "Knight cannot slide horizontally");
    }

    @Test
    void knight_canJumpOverPieces() {
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[1][4] = new Knight(p(1, 4), true);
        // Block all surrounding squares
        board[2][3] = new Pawn(p(2, 3), true);
        board[2][4] = new Pawn(p(2, 4), true);
        board[2][5] = new Pawn(p(2, 5), true);
        board[1][3] = new Pawn(p(1, 3), true);
        board[1][5] = new Pawn(p(1, 5), true);
        GameEngine engine = new GameEngine(board, true);

        // Knight on b2 should still jump to c4 (row 3, col 2)
        assertTrue(engine.applyMove(p(1, 4), p(3, 3)).isValid(), "Knight must jump over surrounding pieces");
    }

    // ---- Bishop ----

    @Test
    void bishop_diagonalMove_isValid() {
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[4][4] = new Bishop(p(4, 4), true);
        GameEngine engine = new GameEngine(board, true);

        assertTrue(engine.applyMove(p(4, 4), p(7, 7)).isValid(), "Bishop should reach h8 diagonally");
    }

    @Test
    void bishop_horizontalMove_isInvalid() {
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[4][4] = new Bishop(p(4, 4), true);
        GameEngine engine = new GameEngine(board, true);

        assertFalse(engine.applyMove(p(4, 4), p(4, 7)).isValid(), "Bishop cannot move horizontally");
    }

    @Test
    void bishop_blockedByOwnPiece_isInvalid() {
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[4][4] = new Bishop(p(4, 4), true);
        board[5][5] = new Pawn(p(5, 5), true);  // own pawn in the way
        GameEngine engine = new GameEngine(board, true);

        assertFalse(engine.applyMove(p(4, 4), p(6, 6)).isValid(), "Bishop cannot pass through own pieces");
    }

    // ---- Rook ----

    @Test
    void rook_horizontalMove_isValid() {
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[4][0] = new Rook(p(4, 0), true);
        GameEngine engine = new GameEngine(board, true);

        assertTrue(engine.applyMove(p(4, 0), p(4, 7)).isValid(), "Rook should slide horizontally");
    }

    @Test
    void rook_verticalMove_isValid() {
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[0][0] = new Rook(p(0, 0), true);
        GameEngine engine = new GameEngine(board, true);

        assertTrue(engine.applyMove(p(0, 0), p(7, 0)).isValid(), "Rook should slide vertically");
    }

    @Test
    void rook_diagonalMove_isInvalid() {
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[4][4] = new Rook(p(4, 4), true);
        GameEngine engine = new GameEngine(board, true);

        assertFalse(engine.applyMove(p(4, 4), p(6, 6)).isValid(), "Rook cannot move diagonally");
    }

    @Test
    void rook_blockedByOwnPiece_cannotPass() {
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[4][0] = new Rook(p(4, 0), true);
        board[4][3] = new Pawn(p(4, 3), true); // blocker
        GameEngine engine = new GameEngine(board, true);

        assertFalse(engine.applyMove(p(4, 0), p(4, 7)).isValid(), "Rook cannot pass through own pieces");
    }

    // ---- Queen ----

    @Test
    void queen_diagonalMove_isValid() {
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[4][4] = new Queen(p(4, 4), true);
        GameEngine engine = new GameEngine(board, true);

        assertTrue(engine.applyMove(p(4, 4), p(7, 7)).isValid(), "Queen should move diagonally like a bishop");
    }

    @Test
    void queen_horizontalMove_isValid() {
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[4][4] = new Queen(p(4, 4), true);
        GameEngine engine = new GameEngine(board, true);

        assertTrue(engine.applyMove(p(4, 4), p(4, 0)).isValid(), "Queen should move horizontally like a rook");
    }

    @Test
    void queen_knightMove_isInvalid() {
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[4][4] = new Queen(p(4, 4), true);
        GameEngine engine = new GameEngine(board, true);

        assertFalse(engine.applyMove(p(4, 4), p(6, 5)).isValid(), "Queen cannot make knight-style moves");
    }

    // ---- King ----

    @Test
    void king_oneSquareMove_isValid() {
        APiece[][] board = blankBoard();
        board[4][4] = new King(p(4, 4), true);
        board[7][4] = new King(p(7, 4), false);
        GameEngine engine = new GameEngine(board, true);

        assertTrue(engine.applyMove(p(4, 4), p(5, 4)).isValid(), "King should move one square forward");
    }

    @Test
    void king_twoSquareSlide_isInvalid() {
        APiece[][] board = blankBoard();
        board[4][4] = new King(p(4, 4), true);
        board[7][4] = new King(p(7, 4), false);
        GameEngine engine = new GameEngine(board, true);

        assertFalse(engine.applyMove(p(4, 4), p(6, 4)).isValid(), "King cannot move two squares (non-castling)");
    }

    @Test
    void king_cannotMoveIntoCheck() {
        // White king on e1 (0,4); black rook on d5 (4,3) covers the entire d-file
        // Moving king to d1 (0,3) steps onto col 3 which the rook guards
        APiece[][] board = blankBoard();
        board[0][4] = new King(p(0, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[4][3] = new Rook(p(4, 3), false);
        GameEngine engine = new GameEngine(board, true);

        assertFalse(engine.applyMove(p(0, 4), p(0, 3)).isValid(), "King cannot walk into rook's file");
    }

    @Test
    void king_capturesUndefendedOpponent() {
        APiece[][] board = blankBoard();
        board[4][4] = new King(p(4, 4), true);
        board[7][4] = new King(p(7, 4), false);
        board[5][5] = new Pawn(p(5, 5), false); // undefended black pawn
        GameEngine engine = new GameEngine(board, true);

        assertTrue(engine.applyMove(p(4, 4), p(5, 5)).isValid(), "King should capture undefended adjacent enemy");
    }
}

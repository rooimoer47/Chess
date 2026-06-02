package pvt.phgg.chess;

public class Position {
    private final int row;
    private final int col;
    private final boolean enPassant;
    private final boolean castle;

    public Position (int row, int col) {
        this.row = row;
        this.col = col;
        this.enPassant = false;
        this.castle = false;
    }

    public Position (int row, int col, SpecialMove move) {
        this.row = row;
        this.col = col;
        this.enPassant = move == SpecialMove.ENPASSANT;
        this.castle = move == SpecialMove.CASTLE;
    }

    public Position withRowOffset(int offset) {
        return new Position(this.row + offset, this.col, this.getSpecialMove());
    }

    public Position withColOffset(int offset) {
        return new Position(this.row, this.col + offset, this.getSpecialMove());
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public boolean isEnPassant() {
        return this.enPassant;
    }

    public boolean isCastle() {
        return this.castle;
    }

    private SpecialMove getSpecialMove() {
        if (this.enPassant) return SpecialMove.ENPASSANT;
        if (this.castle) return SpecialMove.CASTLE;
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return row == position.row && col == position.col;
    }

    @Override
    public int hashCode() {
        return 31 * row + col;
    }

    public enum SpecialMove {
        ENPASSANT,
        CASTLE
    }
}

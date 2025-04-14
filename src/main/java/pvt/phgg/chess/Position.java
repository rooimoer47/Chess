package pvt.phgg.chess;

public class Position {
    private int row;
    private int col;
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
        switch (move) {
            case ENPASSANT -> {
                this.enPassant = true;
                this.castle = false;
            }
            case CASTLE -> {
                this.enPassant = false;
                this.castle = true;
            }
            default -> {
                this.enPassant = false;
                this.castle = false;
            }
        }
    }

    public int getRow() {
        return row;
    }

    public void incRow() {
        this.row++;
    }

    public void incRow(int n) {
        this.row+=n;
    }

    public void decRow() {
        this.row--;
    }

    public int getCol() {
        return col;
    }

    public void incCol() {
        this.col++;
    }

    public void incCol(int n) {
        this.col+=n;
    }

    public boolean isEnPassant() {
        return this.enPassant;
    }

    public boolean isCastle() {
        return this.castle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return row == position.row && col == position.col;
    }

    public enum SpecialMove {
        ENPASSANT,
        CASTLE
    }
}

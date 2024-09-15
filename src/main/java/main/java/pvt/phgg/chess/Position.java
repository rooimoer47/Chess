package main.java.pvt.phgg.chess;

public class Position {
    private int row;
    private final int col;

    public Position (int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public void incRow() {
        this.row++;
    }

    public void decRow() {
        this.row--;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return row == position.row && col == position.col;
    }
}

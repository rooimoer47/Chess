package main.java.pvt.phgg.chess;

public class Position {
    private int row;
    private int col;

    public Position (int row, int col) {
        this.row = row;
        this.col = col;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return row == position.row && col == position.col;
    }
}

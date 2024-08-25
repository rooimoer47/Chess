package main.java.pvt.phgg.chess;

public class Chess {
    Chess () {
        Board board = new Board();
        board.init();
    }

    public static void main(String [] args) {
        new Chess();
    }
}

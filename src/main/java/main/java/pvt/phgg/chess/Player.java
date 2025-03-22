package main.java.pvt.phgg.chess;

public abstract class Player {
    private final boolean white;

    protected Player(boolean white) {
        this.white = white;
    }

    public boolean isWhite() {
        return white;
    }
}
package pvt.phgg.chess.server.elo;

record EloSummaryDto(int elo, int gamesRated, boolean provisional,
                     int elo960, int gamesRated960, boolean provisional960) {}

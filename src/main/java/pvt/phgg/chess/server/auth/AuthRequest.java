package pvt.phgg.chess.server.auth;

public record AuthRequest(String username, String password, Integer startingElo) {}

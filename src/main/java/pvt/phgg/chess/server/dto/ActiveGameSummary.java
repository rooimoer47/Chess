package pvt.phgg.chess.server.dto;

public record ActiveGameSummary(
        long gameId,
        String mode,
        String botType,
        String opponentUsername,
        String color,
        String status,
        String variant
) {}

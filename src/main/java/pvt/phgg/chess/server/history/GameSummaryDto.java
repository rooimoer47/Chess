package pvt.phgg.chess.server.history;

import java.time.OffsetDateTime;

public record GameSummaryDto(
        long id,
        String opponent,
        String playerColor,
        String result,
        String winnerColor,
        String mode,
        String variant,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt
) {}

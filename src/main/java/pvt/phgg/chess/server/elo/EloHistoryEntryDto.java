package pvt.phgg.chess.server.elo;

import java.time.OffsetDateTime;

record EloHistoryEntryDto(long gameId, int eloAfter, int delta, String variant, OffsetDateTime recordedAt) {}

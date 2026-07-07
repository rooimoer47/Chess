package pvt.phgg.chess.server.elo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chess.matchmaking.window")
public record MatchmakingProperties(
        int tier1Seconds,
        int tier1Spread,
        int tier2Seconds,
        int tier2Spread,
        int tier3Seconds,
        int tier3Spread
) {}

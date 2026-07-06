package pvt.phgg.chess.server.elo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chess.elo")
public record EloProperties(
        int defaultElo,
        int min,
        int selfReportMin,
        int selfReportMax,
        int disconnectGraceSeconds
) {}

package pvt.phgg.chess.server.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "chess.analysis")
public record AnalysisProperties(
        boolean workerEnabled,
        String stockfishPath,
        int depth,
        int threads,
        int hashMb,
        int niceness,
        int pvPlies,
        int maxAttempts,
        Duration pollInterval,
        Duration positionTimeout
) {}

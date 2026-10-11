package pvt.phgg.chess.server.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// enabled: whether reviews get an LLM step at all. Off, a review goes straight from the engine to DONE.
// maxComments: per game; the costliest mistakes win.
// retryInterval: how long to wait when the LLM can't be reached before trying again.
// pullTimeout: the first download of an Ollama model.
@ConfigurationProperties(prefix = "chess.commentary")
public record CommentaryProperties(
        boolean enabled,
        int maxComments,
        Duration pollInterval,
        Duration retryInterval,
        Duration pullTimeout
) {}

package pvt.phgg.chess.server.analysis.llm;

import java.time.Duration;

// outputTokens is null when the provider doesn't report it.
public record LlmReply(String text, Integer outputTokens, Duration elapsed) {
}

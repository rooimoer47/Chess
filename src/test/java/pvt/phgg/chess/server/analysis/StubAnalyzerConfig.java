package pvt.phgg.chess.server.analysis;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

// Replaces Stockfish in database tests. Import it the same way everywhere so the test classes share
// one Spring context (and one Postgres container).
@TestConfiguration
public class StubAnalyzerConfig {

    @Bean
    @Primary
    StubAnalyzer stubAnalyzer() {
        return new StubAnalyzer();
    }

    public static class StubAnalyzer implements PositionAnalyzer {
        final List<String> analyzed = Collections.synchronizedList(new ArrayList<>());
        // Set answers for specific positions (by EPD); anything else gets a made-up level eval.
        final Map<String, PositionEval> answers = new ConcurrentHashMap<>();
        volatile Predicate<String> failFor = epd -> false;
        volatile boolean available = true;

        void reset() {
            analyzed.clear();
            answers.clear();
            failFor = epd -> false;
            available = true;
        }

        @Override
        public PositionEval analyze(String fen, boolean chess960) throws AnalysisException {
            analyzed.add(fen);
            if (failFor.test(fen)) throw new AnalysisException("stub failure");
            PositionEval answer = answers.get(fen);
            return answer != null ? answer
                    : new PositionEval(Math.floorMod(fen.hashCode(), 100), null, "e2e4", "e2e4 e7e5", 16);
        }

        @Override
        public String engineName() {
            return "stub";
        }

        @Override
        public boolean isAvailable() {
            return available;
        }
    }
}

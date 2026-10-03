package pvt.phgg.chess.server.analysis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How the analyzer copes when the engine misbehaves, using a fake UCI engine (a shell script) so no
 * Stockfish is needed. The script reads `mode` on each `go`: "ok" answers, "hang" never answers,
 * "crash" exits. It logs each launch to `starts` and every command it receives to `received`.
 */
class StockfishAnalyzerFailureTest {

    private static final String FAKE_ENGINE = """
            #!/bin/sh
            DIR=$(dirname "$0")
            echo start >> "$DIR/starts"
            while IFS= read -r line; do
              echo "$line" >> "$DIR/received"
              case "$line" in
                uci) echo "id name FakeFish 1"; echo "uciok" ;;
                isready) echo "readyok" ;;
                go*)
                  case "$(cat "$DIR/mode")" in
                    hang) ;;
                    crash) exit 1 ;;
                    *) echo "info depth 16 seldepth 20 multipv 1 score cp 25 nodes 1 time 1 pv e2e4 e7e5"
                       echo "bestmove e2e4 ponder e7e5" ;;
                  esac ;;
                quit) exit 0 ;;
              esac
            done
            """;
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -";

    @TempDir Path dir;
    private StockfishAnalyzer analyzer;

    @BeforeEach
    void writeFakeEngine() throws IOException {
        Path engine = dir.resolve("fakefish");
        Files.writeString(engine, FAKE_ENGINE);
        Files.setPosixFilePermissions(engine, PosixFilePermissions.fromString("rwx------"));
        mode("ok");
        analyzer = analyzer(engine.toString(), 0);
    }

    @AfterEach
    void stop() {
        analyzer.destroy();
    }

    private static StockfishAnalyzer analyzer(String path, int niceness) {
        return new StockfishAnalyzer(new AnalysisProperties(true, path, 16, 1, 32, niceness, 6, 3,
                Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    private void mode(String mode) throws IOException {
        Files.writeString(dir.resolve("mode"), mode);
    }

    private int starts() throws IOException {
        Path starts = dir.resolve("starts");
        return Files.exists(starts) ? Files.readAllLines(starts).size() : 0;
    }

    private List<String> received() throws IOException {
        return Files.readAllLines(dir.resolve("received"));
    }

    @Test
    void answersAndReusesOneProcess() throws Exception {
        assertTrue(analyzer.isAvailable());
        assertEquals("FakeFish 1", analyzer.engineName());

        PositionEval first = analyzer.analyze(START_FEN, false);
        analyzer.analyze(START_FEN, false);

        assertEquals(25, first.evalCp());
        assertEquals("e2e4", first.bestUci());
        assertEquals(1, starts(), "one process for every position");
        assertTrue(received().containsAll(List.of("setoption name Threads value 1", "setoption name Hash value 32")));
    }

    @Test
    void switchesChess960ModeOnlyWhenTheVariantChanges() throws Exception {
        analyzer.analyze(START_FEN, false);
        analyzer.analyze(START_FEN, false);
        analyzer.analyze(START_FEN, true);
        analyzer.analyze(START_FEN, false);

        List<String> modeSwitches = received().stream().filter(l -> l.startsWith("setoption name UCI_Chess960")).toList();
        assertEquals(List.of(
                "setoption name UCI_Chess960 value false",
                "setoption name UCI_Chess960 value true",
                "setoption name UCI_Chess960 value false"), modeSwitches);
    }

    @Test
    void hangingEngineTimesOutAndIsRestarted() throws Exception {
        mode("hang");
        AnalysisException e = assertThrows(AnalysisException.class, () -> analyzer.analyze(START_FEN, false));
        assertTrue(e.getMessage().contains("did not answer"), e.getMessage());

        mode("ok");
        assertEquals(25, analyzer.analyze(START_FEN, false).evalCp());
        assertEquals(2, starts(), "the hung process was replaced");
    }

    @Test
    void crashingEngineIsRestarted() throws Exception {
        mode("crash");
        AnalysisException e = assertThrows(AnalysisException.class, () -> analyzer.analyze(START_FEN, false));
        assertTrue(e.getMessage().contains("exited"), e.getMessage());

        mode("ok");
        assertEquals(25, analyzer.analyze(START_FEN, false).evalCp());
        assertEquals(2, starts());
    }

    @Test
    void missingBinaryIsUnavailableAndFailsCleanly() {
        String missing = dir.resolve("no-such-engine").toString();

        StockfishAnalyzer direct = analyzer(missing, 0);
        assertFalse(direct.isAvailable());
        assertThrows(AnalysisException.class, () -> direct.analyze(START_FEN, false));

        // Under `nice` the process itself starts, then exits at once because the engine isn't there.
        StockfishAnalyzer niced = analyzer(missing, 10);
        assertThrows(AnalysisException.class, () -> niced.analyze(START_FEN, false));
        niced.destroy();
        direct.destroy();
    }
}

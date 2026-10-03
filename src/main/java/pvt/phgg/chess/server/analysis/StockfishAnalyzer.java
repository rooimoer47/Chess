package pvt.phgg.chess.server.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// Talks UCI to one long-lived Stockfish process. Starting it loads a ~109 MiB network, so it is
// started on first use and kept, and restarted only after an error or a timeout.
@Component
public class StockfishAnalyzer implements PositionAnalyzer, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(StockfishAnalyzer.class);
    // Pushed by the reader thread when Stockfish's output ends, so a waiting caller fails fast.
    private static final String END_OF_OUTPUT = "\u0000eof";

    private final AnalysisProperties properties;

    private Process process;
    private Writer input;
    private BlockingQueue<String> output;
    private String engineName;
    private Boolean chess960Mode;

    public StockfishAnalyzer(AnalysisProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        return Files.isExecutable(Path.of(properties.stockfishPath()));
    }

    @Override
    public synchronized String engineName() throws AnalysisException {
        ensureStarted();
        return engineName;
    }

    @Override
    public synchronized PositionEval analyze(String fen, boolean chess960) throws AnalysisException {
        ensureStarted();
        try {
            if (chess960Mode == null || chess960Mode != chess960) {
                send("setoption name UCI_Chess960 value " + chess960);
                send("ucinewgame");
                send("isready");
                readUntil("readyok");
                chess960Mode = chess960;
            }
            send("position fen " + fen);
            send("go depth " + properties.depth());
            return parse(readUntil("bestmove"), isBlackToMove(fen), properties.depth(), properties.pvPlies());
        } catch (AnalysisException | RuntimeException e) {
            stop();
            throw e;
        }
    }

    @Override
    public synchronized void destroy() {
        stop();
    }

    // Turns the output of one `go` into an evaluation from White's point of view. UCI reports scores
    // for the side to move, so they are negated when Black is to move.
    static PositionEval parse(List<String> lines, boolean blackToMove, int requestedDepth, int pvPlies)
            throws AnalysisException {
        String lastScoreLine = null;
        String bestMove = null;
        for (String line : lines) {
            if (line.startsWith("bestmove")) {
                String[] tokens = line.split("\\s+");
                bestMove = tokens.length > 1 ? tokens[1] : null;
            } else if (isExactMainLineScore(line)) {
                lastScoreLine = line;
            }
        }
        if (bestMove == null || lastScoreLine == null) {
            throw new AnalysisException("Stockfish gave no score or best move");
        }

        List<String> tokens = Arrays.asList(lastScoreLine.split("\\s+"));
        int sign = blackToMove ? -1 : 1;
        int scoreAt = tokens.indexOf("score");
        int value = Integer.parseInt(tokens.get(scoreAt + 2));
        Integer evalCp = null;
        Integer mateIn = null;
        if (tokens.get(scoreAt + 1).equals("mate")) {
            mateIn = value == 0 ? 0 : sign * value;
        } else {
            evalCp = sign * value;
        }

        // "(none)" means the side to move has no legal move. That result is exact at any depth, so
        // it is stored at the requested depth and counts as a cache hit next time.
        boolean noLegalMove = bestMove.equals("(none)");
        int depthAt = tokens.indexOf("depth");
        int depth = noLegalMove || depthAt < 0 ? requestedDepth : Integer.parseInt(tokens.get(depthAt + 1));

        int pvAt = tokens.indexOf("pv");
        String pv = null;
        if (pvAt >= 0 && pvAt + 1 < tokens.size()) {
            List<String> line = tokens.subList(pvAt + 1, Math.min(tokens.size(), pvAt + 1 + pvPlies));
            pv = String.join(" ", line);
        }
        return new PositionEval(evalCp, mateIn, noLegalMove ? null : bestMove, pv, depth);
    }

    // Skips "info string" chatter, lines without a score, other multipv lines, and bound scores
    // (an aspiration-window fail-high/low, not a final value).
    private static boolean isExactMainLineScore(String line) {
        if (!line.startsWith("info ") || !line.contains(" score ")) return false;
        if (line.contains(" lowerbound") || line.contains(" upperbound")) return false;
        return !line.contains(" multipv ") || line.contains(" multipv 1 ");
    }

    private static boolean isBlackToMove(String fen) {
        String[] fields = fen.trim().split("\\s+");
        return fields.length > 1 && fields[1].equals("b");
    }

    private void ensureStarted() throws AnalysisException {
        if (process != null && process.isAlive()) return;
        stop();
        List<String> command = new ArrayList<>();
        if (properties.niceness() > 0) {
            command.addAll(List.of("nice", "-n", String.valueOf(properties.niceness())));
        }
        command.add(properties.stockfishPath());
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            process = null;
            throw new AnalysisException("Could not start Stockfish at " + properties.stockfishPath(), e);
        }
        input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        output = new LinkedBlockingQueue<>();
        startReader(process, output);

        try {
            send("uci");
            for (String line : readUntil("uciok")) {
                if (line.startsWith("id name ")) {
                    engineName = line.substring("id name ".length()).trim();
                }
            }
            send("setoption name Threads value " + properties.threads());
            send("setoption name Hash value " + properties.hashMb());
            send("isready");
            readUntil("readyok");
        } catch (AnalysisException | RuntimeException e) {
            stop();
            throw e;
        }
        log.info("Started {} (depth {}, {} thread(s), {} MB hash)",
                engineName, properties.depth(), properties.threads(), properties.hashMb());
    }

    private static void startReader(Process process, BlockingQueue<String> output) {
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    output.add(line);
                }
            } catch (IOException e) {
                // The process was stopped; the end-of-output marker below tells any waiting caller.
            }
            output.add(END_OF_OUTPUT);
        }, "stockfish-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void send(String command) throws AnalysisException {
        try {
            input.write(command + "\n");
            input.flush();
        } catch (IOException e) {
            throw new AnalysisException("Lost the connection to Stockfish", e);
        }
    }

    // Collects output lines up to and including the first that starts with `prefix`.
    private List<String> readUntil(String prefix) throws AnalysisException {
        long deadline = System.nanoTime() + properties.positionTimeout().toNanos();
        List<String> lines = new ArrayList<>();
        while (true) {
            String line;
            try {
                line = output.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AnalysisException("Interrupted while waiting for Stockfish", e);
            }
            if (line == null) {
                throw new AnalysisException("Stockfish did not answer within " + properties.positionTimeout());
            }
            if (line.equals(END_OF_OUTPUT)) {
                throw new AnalysisException("Stockfish exited");
            }
            lines.add(line);
            if (line.startsWith(prefix)) {
                return lines;
            }
        }
    }

    private void stop() {
        if (process != null) {
            try {
                input.write("quit\n");
                input.flush();
            } catch (IOException ignored) {
                // Already gone.
            }
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        process = null;
        input = null;
        output = null;
        chess960Mode = null;
    }
}

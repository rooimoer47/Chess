package pvt.phgg.chess.server.analysis;

import com.anthropic.models.messages.Model;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import pvt.phgg.chess.GameEngine;
import pvt.phgg.chess.GameReplay;
import pvt.phgg.chess.GameReplay.ReplayedGame;
import pvt.phgg.chess.GameReplay.ReplayedPosition;
import pvt.phgg.chess.server.analysis.GameAnalysisService.LoadedGame;
import pvt.phgg.chess.server.analysis.llm.Embedder;
import pvt.phgg.chess.server.analysis.llm.LlmClient;
import pvt.phgg.chess.server.analysis.llm.LlmException;
import pvt.phgg.chess.server.analysis.llm.LlmReply;
import pvt.phgg.chess.server.analysis.llm.SpringAiEmbedder;
import pvt.phgg.chess.server.analysis.llm.SpringAiLlmClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The commentary eval set: hand-picked moves from src/test/resources/commentary/eval-set.json, each
 * with the key points a good comment should make. Runs every move through the production path
 * (Stockfish, MoveFacts, CommentaryPrompt, the model, the output check) for one or more models and
 * writes a report to target/commentary-eval/, so prompt and model changes can be compared.
 *
 * Not a pass/fail test, and slow, so it only runs when asked:
 * <pre>
 * STOCKFISH_PATH=/path/to/stockfish CHESS_LLM_EVAL=qwen3.5:4b,gemma4:e4b \
 *     mvn test -Dtest=CommentaryEvalRun
 * </pre>
 * Model names starting with "claude-" go to the Anthropic API (needs ANTHROPIC_API_KEY); the rest to
 * Ollama at OLLAMA_BASE_URL (default http://localhost:11434). The key-point score is a rough guide
 * (a keyword from each group appears); read the comments themselves.
 *
 * Opening notes are looked up as in production, with the embedding model in Ollama and a throwaway
 * Postgres container (needs Docker). CHESS_EVAL_NOTES=off leaves them out, to compare.
 */
@EnabledIfEnvironmentVariable(named = "CHESS_LLM_EVAL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "STOCKFISH_PATH", matches = ".+")
class CommentaryEvalRun {

    private static final Set<MoveClassification> COMMENTED =
            Set.of(MoveClassification.INACCURACY, MoveClassification.MISTAKE, MoveClassification.BLUNDER);

    record Case(String name, String moves, List<List<String>> expect) {
    }

    record Prepared(Case evalCase, MoveClassification classification, String prompt) {
    }

    record Result(String answer, boolean accepted, int attempts, Duration elapsed, int tokens, int keyPoints) {
    }

    @Test
    void run() throws Exception {
        List<Case> cases = loadCases();
        List<Prepared> prepared = prepare(cases);
        List<String> models = List.of(System.getenv("CHESS_LLM_EVAL").split(","));

        StringBuilder report = new StringBuilder("# Commentary eval, ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");
        Map<String, List<Result>> byModel = new HashMap<>();
        for (String model : models) {
            LlmClient llm = client(model.strip());
            if (!llm.prepare()) throw new IllegalStateException("Model " + model + " isn't reachable");
            llm.generate(CommentaryPrompt.SYSTEM, "Say ready.");  // loads the model, so timings don't include it
            List<Result> results = new ArrayList<>();
            for (Prepared p : prepared) {
                results.add(p.prompt() == null ? null : comment(llm, p));
            }
            byModel.put(model.strip(), results);
        }

        report.append("| Model | Accepted | First try | Key points | Avg s per comment | Tokens/s |\n|---|---|---|---|---|---|\n");
        for (String model : byModel.keySet().stream().sorted().toList()) {
            List<Result> results = byModel.get(model).stream().filter(r -> r != null).toList();
            int possible = 0;
            int hit = 0;
            for (int i = 0; i < prepared.size(); i++) {
                Result r = byModel.get(model).get(i);
                if (r == null) continue;
                possible += prepared.get(i).evalCase().expect().size();
                hit += r.keyPoints();
            }
            double seconds = results.stream().mapToDouble(r -> r.elapsed().toMillis() / 1000.0).sum();
            int tokens = results.stream().mapToInt(Result::tokens).sum();
            report.append(String.format(Locale.ROOT, "| %s | %d/%d | %d | %d/%d | %.1f | %.1f |%n", model,
                    results.stream().filter(Result::accepted).count(), results.size(),
                    results.stream().filter(r -> r.accepted() && r.attempts() == 1).count(),
                    hit, possible, seconds / Math.max(1, results.size()), seconds > 0 ? tokens / seconds : 0));
        }

        for (int i = 0; i < prepared.size(); i++) {
            Prepared p = prepared.get(i);
            report.append("\n## ").append(i + 1).append(". ").append(p.evalCase().name()).append("\n\n");
            if (p.prompt() == null) {
                report.append("Stockfish rates the move ").append(p.classification())
                        .append(", so it wouldn't get a comment.\n");
                continue;
            }
            report.append("Expected: ").append(p.evalCase().expect()).append("\n\n```\n")
                    .append(p.prompt()).append("\n```\n\n");
            for (String model : byModel.keySet().stream().sorted().toList()) {
                Result r = byModel.get(model).get(i);
                report.append(String.format(Locale.ROOT, "- **%s** (%s, %d/%d key points, %.1f s): %s%n", model,
                        r.accepted() ? (r.attempts() == 1 ? "accepted" : "accepted on retry") : "REJECTED",
                        r.keyPoints(), p.evalCase().expect().size(), r.elapsed().toMillis() / 1000.0, r.answer()));
            }
        }

        Path out = Path.of("target", "commentary-eval",
                "report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);
        System.out.println(report);
        System.out.println("Report written to " + out.toAbsolutePath());
    }

    private static List<Case> loadCases() throws IOException {
        try (InputStream in = CommentaryEvalRun.class.getResourceAsStream("/commentary/eval-set.json")) {
            List<Case> cases = new ArrayList<>();
            for (JsonNode node : new ObjectMapper().readTree(in)) {
                List<List<String>> expect = new ArrayList<>();
                for (JsonNode group : node.get("expect")) {
                    List<String> words = new ArrayList<>();
                    group.forEach(word -> words.add(word.asString()));
                    expect.add(words);
                }
                cases.add(new Case(node.get("name").asString(), node.get("moves").asString(), expect));
            }
            return cases;
        }
    }

    // Analyses the positions before and after each case's last move, as the worker would, then looks
    // up the opening notes, before the chat model is first used.
    private static List<Prepared> prepare(List<Case> cases) throws AnalysisException {
        List<MoveClassification> labels = new ArrayList<>();
        List<MoveContext> contexts = new ArrayList<>();
        StockfishAnalyzer stockfish = new StockfishAnalyzer(new AnalysisProperties(false, System.getenv("STOCKFISH_PATH"),
                16, 1, 32, 0, 6, 1, Duration.ofSeconds(5), Duration.ofSeconds(60)));
        try {
            for (Case c : cases) {
                ReplayedGame replay = GameReplay.replayGame(GameEngine.STANDARD_BACK_RANK, false, TestGames.moves(c.moves()));
                int index = replay.positions().size() - 2;
                ReplayedPosition before = replay.positions().get(index);
                ReplayedPosition after = replay.positions().get(index + 1);
                PositionEval evalBefore = stockfish.analyze(before.epd(), false);
                PositionEval evalAfter = stockfish.analyze(after.epd(), false);
                LoadedGame game = new LoadedGame(replay,
                        Map.of(before.epd(), evalBefore, after.epd(), evalAfter), true, true);
                MoveClassification label = MoveClassifier.classify(evalBefore, evalAfter, index % 2 == 0, before.playedUci());
                labels.add(label);
                contexts.add(COMMENTED.contains(label) ? MoveFacts.describe(game, index, label, false) : null);
            }
        } finally {
            stockfish.destroy();
        }

        List<MoveContext> commented = contexts.stream().filter(context -> context != null).toList();
        List<MoveContext> annotated = "off".equals(System.getenv("CHESS_EVAL_NOTES")) ? commented : withNotes(commented);
        List<Prepared> prepared = new ArrayList<>();
        int next = 0;
        for (int i = 0; i < cases.size(); i++) {
            String prompt = contexts.get(i) == null ? null : CommentaryPrompt.user(annotated.get(next++));
            prepared.add(new Prepared(cases.get(i), labels.get(i), prompt));
        }
        return prepared;
    }

    private static List<MoveContext> withNotes(List<MoveContext> moves) {
        String baseUrl = Optional.ofNullable(System.getenv("OLLAMA_BASE_URL")).orElse("http://localhost:11434");
        String model = Optional.ofNullable(System.getenv("CHESS_EMBEDDING_MODEL")).orElse("nomic-embed-text");
        OllamaApi ollamaApi = OllamaApi.builder().baseUrl(baseUrl).build();
        Embedder embedder = new SpringAiEmbedder(OllamaEmbeddingModel.builder().ollamaApi(ollamaApi)
                .options(OllamaEmbeddingOptions.builder().model(model).build()).build(),
                ollamaApi, model, Duration.ofMinutes(30));
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(
                DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).load().migrate();
            OpeningNotes notes = new OpeningNotes(new JdbcTemplate(dataSource), embedder);
            // One at a time: each case is its own game, and within a game notes go by ply.
            List<MoveContext> annotated = new ArrayList<>();
            for (MoveContext move : moves) {
                annotated.addAll(notes.annotate(List.of(move)));
            }
            return annotated;
        }
    }

    // The worker's loop for one move: one retry when the check fails.
    private static Result comment(LlmClient llm, Prepared p) throws LlmException {
        Duration elapsed = Duration.ZERO;
        int tokens = 0;
        String answer = "";
        Optional<String> accepted = Optional.empty();
        int attempt = 0;
        while (attempt < 2 && accepted.isEmpty()) {
            attempt++;
            LlmReply reply = llm.generate(CommentaryPrompt.SYSTEM, p.prompt());
            elapsed = elapsed.plus(reply.elapsed());
            tokens += reply.outputTokens() == null ? 0 : reply.outputTokens();
            answer = reply.text().strip().replaceAll("\\s+", " ");
            accepted = CommentaryPrompt.check(reply.text(), p.prompt());
        }
        String text = accepted.orElse(answer);
        int keyPoints = 0;
        for (List<String> group : p.evalCase().expect()) {
            if (group.stream().anyMatch(word -> text.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT)))) {
                keyPoints++;
            }
        }
        return new Result(text, accepted.isPresent(), attempt, elapsed, tokens, keyPoints);
    }

    // The same settings as application.properties.
    private static LlmClient client(String model) {
        ChatModel chatModel;
        OllamaApi ollamaApi = null;
        if (model.startsWith("claude-")) {
            chatModel = AnthropicChatModel.builder()
                    .options(AnthropicChatOptions.builder()
                            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                            .model(Model.of(model))
                            .maxTokens(4000)
                            .build())
                    .build();
        } else {
            String baseUrl = Optional.ofNullable(System.getenv("OLLAMA_BASE_URL")).orElse("http://localhost:11434");
            ollamaApi = OllamaApi.builder().baseUrl(baseUrl).build();
            chatModel = OllamaChatModel.builder()
                    .ollamaApi(ollamaApi)
                    .options(OllamaChatOptions.builder()
                            .model(model)
                            .temperature(0.2)
                            .topK(20)
                            .topP(0.95)
                            .presencePenalty(0.0)
                            .numPredict(150)
                            .numCtx(2048)
                            .disableThinking()
                            .build())
                    .build();
        }
        return new SpringAiLlmClient(chatModel, ollamaApi, Duration.ofMinutes(30));
    }
}

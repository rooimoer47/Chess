package pvt.phgg.chess.server.analysis.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;

import java.time.Duration;
import java.util.List;

// Spring AI's embedding model, which is Ollama's (spring.ai.model.embedding).
public class SpringAiEmbedder implements Embedder {

    private static final Logger log = LoggerFactory.getLogger(SpringAiEmbedder.class);

    private final EmbeddingModel embeddingModel;
    private final OllamaApi ollamaApi;
    private final String model;
    private final Duration pullTimeout;

    // embeddingModel and ollamaApi are null when embeddings are switched off.
    public SpringAiEmbedder(EmbeddingModel embeddingModel, OllamaApi ollamaApi, String model, Duration pullTimeout) {
        this.embeddingModel = embeddingModel;
        this.ollamaApi = ollamaApi;
        this.model = model;
        this.pullTimeout = pullTimeout;
    }

    @Override
    public List<float[]> embed(List<String> texts, Use use) throws LlmException {
        String prefix = prefix(use);
        try {
            return embeddingModel.embed(texts.stream().map(text -> prefix + text).toList());
        } catch (RuntimeException e) {
            throw new LlmException("Embedding call failed: " + e.getMessage(), e);
        }
    }

    // The nomic models are trained with a task prefix on every text and search worse without it.
    private String prefix(Use use) {
        if (!model.startsWith("nomic-embed-text")) return "";
        return use == Use.QUERY ? "search_query: " : "search_document: ";
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public boolean prepare() {
        if (embeddingModel == null || ollamaApi == null) return false;
        OllamaModelManager manager = new OllamaModelManager(ollamaApi,
                new ModelManagementOptions(PullModelStrategy.WHEN_MISSING, List.of(), pullTimeout, 0));
        try {
            if (!manager.isModelAvailable(model)) {
                log.info("Pulling {} into Ollama", model);
                manager.pullModel(model, PullModelStrategy.WHEN_MISSING);
                log.info("Pulled {}", model);
            }
            return true;
        } catch (RuntimeException e) {
            log.debug("Ollama not ready", e);
            return false;
        }
    }
}

package pvt.phgg.chess.server.analysis.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;

import java.time.Duration;
import java.util.List;

// Whichever chat model Spring AI is configured with (spring.ai.model.chat: ollama or anthropic).
// Sampling and thinking options are per provider, so they live in application.properties rather
// than here: the hosted models reject some of the options the local one needs.
public class SpringAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmClient.class);

    private final ChatModel chatModel;
    private final OllamaApi ollamaApi;
    private final Duration pullTimeout;

    // ollamaApi is null unless the chat model is Ollama's.
    public SpringAiLlmClient(ChatModel chatModel, OllamaApi ollamaApi, Duration pullTimeout) {
        this.chatModel = chatModel;
        this.ollamaApi = ollamaApi;
        this.pullTimeout = pullTimeout;
    }

    @Override
    public LlmReply generate(String system, String user) throws LlmException {
        long start = System.nanoTime();
        ChatResponse response;
        try {
            response = chatModel.call(new Prompt(List.of(new SystemMessage(system), new UserMessage(user))));
        } catch (RuntimeException e) {
            throw new LlmException("Chat model call failed: " + e.getMessage(), e);
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        if (response == null || response.getResult() == null) {
            throw new LlmException("Chat model returned no result", null);
        }
        String text = response.getResult().getOutput().getText();
        Integer tokens = response.getMetadata().getUsage() == null ? null
                : response.getMetadata().getUsage().getCompletionTokens();
        return new LlmReply(text == null ? "" : text, tokens, elapsed);
    }

    @Override
    public String modelName() {
        String model = chatModel.getDefaultOptions().getModel();
        return model == null ? "unknown" : model;
    }

    @Override
    public boolean prepare() {
        if (!(chatModel instanceof OllamaChatModel) || ollamaApi == null) {
            return true;
        }
        OllamaModelManager manager = new OllamaModelManager(ollamaApi,
                new ModelManagementOptions(PullModelStrategy.WHEN_MISSING, List.of(), pullTimeout, 0));
        String model = modelName();
        try {
            if (!manager.isModelAvailable(model)) {
                log.info("Pulling {} into Ollama; the first review comments wait for it", model);
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

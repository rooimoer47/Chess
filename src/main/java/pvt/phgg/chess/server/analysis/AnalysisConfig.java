package pvt.phgg.chess.server.analysis;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pvt.phgg.chess.server.analysis.llm.Embedder;
import pvt.phgg.chess.server.analysis.llm.LlmClient;
import pvt.phgg.chess.server.analysis.llm.SpringAiEmbedder;
import pvt.phgg.chess.server.analysis.llm.SpringAiLlmClient;

@Configuration
@EnableConfigurationProperties({AnalysisProperties.class, CommentaryProperties.class})
public class AnalysisConfig {

    // The chat model is whichever provider spring.ai.model.chat selects.
    @Bean
    LlmClient llmClient(ChatModel chatModel, ObjectProvider<OllamaApi> ollamaApi, CommentaryProperties properties) {
        return new SpringAiLlmClient(chatModel, ollamaApi.getIfAvailable(), properties.pullTimeout());
    }

    // There is no embedding model when spring.ai.model.embedding is none; the embedder then reports
    // itself unavailable and comments are written without retrieval.
    @Bean
    Embedder embedder(ObjectProvider<EmbeddingModel> embeddingModel, ObjectProvider<OllamaApi> ollamaApi,
                      @Value("${spring.ai.ollama.embedding.model}") String model, CommentaryProperties properties) {
        return new SpringAiEmbedder(embeddingModel.getIfAvailable(), ollamaApi.getIfAvailable(), model,
                properties.pullTimeout());
    }
}

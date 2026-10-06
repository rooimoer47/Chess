package pvt.phgg.chess.server.analysis;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pvt.phgg.chess.server.analysis.llm.LlmClient;
import pvt.phgg.chess.server.analysis.llm.SpringAiLlmClient;

@Configuration
@EnableConfigurationProperties({AnalysisProperties.class, CommentaryProperties.class})
public class AnalysisConfig {

    // The chat model is whichever provider spring.ai.model.chat selects.
    @Bean
    LlmClient llmClient(ChatModel chatModel, ObjectProvider<OllamaApi> ollamaApi, CommentaryProperties properties) {
        return new SpringAiLlmClient(chatModel, ollamaApi.getIfAvailable(), properties.pullTimeout());
    }
}

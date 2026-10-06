package pvt.phgg.chess.server.analysis.llm;

// The language model that writes review comments. Behind an interface so the provider (a local
// Ollama model or a hosted API) is a configuration choice, and tests can run without either.
public interface LlmClient {

    // One completion. Throws when the model can't be reached or fails, as opposed to answering badly.
    LlmReply generate(String system, String user) throws LlmException;

    // Stored with every comment set, e.g. "qwen3.5:4b".
    String modelName();

    // Makes sure the model can be used, e.g. by pulling it into Ollama the first time, which can take
    // minutes. False when the service can't be reached, so the caller can try again later.
    boolean prepare();
}

package pvt.phgg.chess.server.analysis.llm;

import java.util.List;

// The embedding model behind retrieval for review comments: it turns a text into a vector, and texts
// that mean similar things get vectors that lie close together.
public interface Embedder {

    // Some models embed a text differently depending on whether it is stored or searched with.
    enum Use { STORED, QUERY }

    // One vector per text, in order. Throws when the model can't be reached or fails.
    List<float[]> embed(List<String> texts, Use use) throws LlmException;

    // Stored with every vector: vectors from different models can't be compared.
    String modelName();

    // As LlmClient.prepare: pulls the model the first time. False when it can't be used right now.
    boolean prepare();
}

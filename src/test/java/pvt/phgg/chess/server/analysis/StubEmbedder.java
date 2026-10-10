package pvt.phgg.chess.server.analysis;

import pvt.phgg.chess.server.analysis.llm.Embedder;
import pvt.phgg.chess.server.analysis.llm.LlmException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Word-count vectors: texts that share words lie close together. Enough to put real vectors through
// pgvector without a model.
class StubEmbedder implements Embedder {

    static final int DIMENSIONS = 768;

    final List<List<String>> calls = new ArrayList<>();
    String model = "stub-embedder";
    boolean available = true;
    boolean failing;

    @Override
    public List<float[]> embed(List<String> texts, Use use) throws LlmException {
        if (failing) throw new LlmException("stub failure", null);
        calls.add(texts);
        return texts.stream().map(StubEmbedder::vector).toList();
    }

    static float[] vector(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (!word.isEmpty()) vector[Math.floorMod(word.hashCode(), DIMENSIONS)]++;
        }
        return vector;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public boolean prepare() {
        return available;
    }
}

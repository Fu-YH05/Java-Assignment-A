package com.cst3492.ta.embedding;

import java.util.List;

/** Provider-agnostic embedding interface; the chat provider may not offer embeddings. */
public interface EmbeddingProvider {

    /** The embedding model name; must be recorded in the index. */
    String model();

    /** Embeds a batch of texts, preserving order. */
    List<float[]> embedAll(List<String> texts);
}

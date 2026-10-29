package com.cst3492.ta.retrieval;

import com.cst3492.ta.model.KnowledgeChunk;

/** One retrieval hit: the chunk plus its similarity score. */
public record ScoredChunk(KnowledgeChunk chunk, float score) {
}

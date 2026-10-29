package com.cst3492.ta.retrieval;

import com.cst3492.ta.model.KnowledgeChunk;
import com.cst3492.ta.util.VectorMath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dense retriever: cosine top-K with a relevance threshold. Vectors are L2-normalised at write
 * time, so cosine similarity reduces to a dot product.
 */
public final class Retriever {

    private final List<KnowledgeChunk> chunks;
    private final float[][] vectors;

    public Retriever(List<KnowledgeChunk> chunks, float[][] vectors) {
        this.chunks = chunks;
        this.vectors = vectors;
    }

    public int size() {
        return chunks.size();
    }

    public List<ScoredChunk> search(float[] query, int k, double threshold) {
        VectorMath.normalize(query);
        List<ScoredChunk> hits = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (vectors[i] == null || vectors[i].length != query.length) {
                continue; // dimension mismatch must not crash retrieval
            }
            float score = VectorMath.dot(query, vectors[i]);
            hits.add(new ScoredChunk(chunks.get(i), score));
        }
        hits.sort(Comparator.comparingDouble(ScoredChunk::score).reversed()
                .thenComparing(h -> h.chunk().id));
        List<ScoredChunk> top = new ArrayList<>();
        for (ScoredChunk hit : hits) {
            if (top.size() >= k) {
                break;
            }
            if (hit.score() < threshold) {
                break;
            }
            top.add(hit);
        }
        return top;
    }
}

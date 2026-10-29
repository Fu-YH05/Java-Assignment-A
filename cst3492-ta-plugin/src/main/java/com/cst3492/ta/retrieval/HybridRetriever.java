package com.cst3492.ta.retrieval;

import com.cst3492.ta.model.KnowledgeChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid retrieval with Reciprocal Rank Fusion (Section 5.5.3 of the design). BM25 scores and
 * cosine similarities live on different scales, so only rank positions are fused:
 * rrf(d) = sum of 1 / (k + rank), with k = 60.
 */
public final class HybridRetriever {

    public static final int RRF_K = 60;
    private static final int WINDOW = 10;

    private final KeywordIndex keyword;
    private final Retriever dense;

    public HybridRetriever(KeywordIndex keyword, Retriever dense) {
        this.keyword = keyword;
        this.dense = dense;
    }

    public List<ScoredChunk> search(String query, float[] queryVector, int k, double threshold) {
        List<ScoredChunk> denseHits = dense.search(queryVector, WINDOW, threshold);
        List<ScoredChunk> keywordHits = keyword.search(query, WINDOW);

        Map<String, Double> fused = new HashMap<>();
        Map<String, KnowledgeChunk> byId = new HashMap<>();
        for (List<ScoredChunk> ranking : List.of(denseHits, keywordHits)) {
            for (int rank = 1; rank <= ranking.size(); rank++) {
                KnowledgeChunk chunk = ranking.get(rank - 1).chunk();
                fused.merge(chunk.id, 1.0 / (RRF_K + rank), Double::sum);
                byId.put(chunk.id, chunk);
            }
        }
        List<ScoredChunk> merged = new ArrayList<>();
        fused.forEach((id, score) -> merged.add(new ScoredChunk(byId.get(id), score.floatValue())));
        merged.sort(Comparator.comparingDouble(ScoredChunk::score).reversed()
                .thenComparing(h -> h.chunk().id));
        return merged.subList(0, Math.min(k, merged.size()));
    }
}

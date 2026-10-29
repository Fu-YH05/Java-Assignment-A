package com.cst3492.ta.retrieval;

import com.cst3492.ta.model.KnowledgeChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BM25 keyword index over the chunk set. It complements dense retrieval for exact identifiers
 * such as @Autowired or BeanFactory.
 */
public final class KeywordIndex {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final List<KnowledgeChunk> chunks;
    private final Map<String, Integer> docFreq = new HashMap<>();
    private final double avgLength;

    public KeywordIndex(List<KnowledgeChunk> chunks) {
        this.chunks = chunks;
        double total = 0;
        for (KnowledgeChunk chunk : chunks) {
            List<String> terms = tokenize(chunk.text + " " + chunk.title);
            total += terms.size();
            for (String term : new java.util.HashSet<>(terms)) {
                docFreq.merge(term, 1, Integer::sum);
            }
        }
        this.avgLength = chunks.isEmpty() ? 1 : total / chunks.size();
    }

    public List<ScoredChunk> search(String query, int k) {
        List<String> terms = tokenize(query);
        List<ScoredChunk> hits = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            List<String> chunkTerms = tokenize(chunk.text + " " + chunk.title);
            Map<String, Integer> tf = new HashMap<>();
            for (String term : chunkTerms) {
                tf.merge(term, 1, Integer::sum);
            }
            double score = 0;
            for (String term : terms) {
                Integer df = docFreq.get(term);
                if (df == null) {
                    continue;
                }
                double idf = Math.log(1 + (chunks.size() - df + 0.5) / (df + 0.5));
                double freq = tf.getOrDefault(term, 0);
                score += idf * freq * (K1 + 1)
                        / (freq + K1 * (1 - B + B * chunkTerms.size() / avgLength));
            }
            if (score > 0) {
                hits.add(new ScoredChunk(chunk, (float) score));
            }
        }
        hits.sort(Comparator.comparingDouble(ScoredChunk::score).reversed()
                .thenComparing(h -> h.chunk().id));
        return hits.subList(0, Math.min(k, hits.size()));
    }

    static List<String> tokenize(String text) {
        List<String> terms = new ArrayList<>();
        if (text == null) {
            return terms;
        }
        for (String token : text.toLowerCase().split("[^a-zA-Z0-9@_.]+")) {
            if (!token.isBlank()) {
                terms.add(token);
            }
        }
        return terms;
    }
}

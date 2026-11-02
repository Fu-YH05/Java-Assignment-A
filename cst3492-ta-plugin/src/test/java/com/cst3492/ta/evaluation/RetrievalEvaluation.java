package com.cst3492.ta.evaluation;

import com.cst3492.ta.index.JsonVectorStore;
import com.cst3492.ta.retrieval.*;
import com.cst3492.ta.util.JsonUtil;
import java.nio.file.*;
import java.util.*;

/** Opt-in real-corpus benchmark. No credentials, model calls, or course text in the output. */
public final class RetrievalEvaluation {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Arguments: index-folder queries.tsv output.json");
        JsonVectorStore store = new JsonVectorStore(Path.of(args[0]));
        store.load();
        KeywordIndex keyword = new KeywordIndex(store.meta().chunks);
        Retriever dense = new Retriever(store.meta().chunks, store.vectors());
        List<Map<String, Object>> rows = new ArrayList<>();
        double hitSum = 0, reciprocalSum = 0, recallSum = 0;
        int positive = 0, negative = 0, negativeEmpty = 0;
        List<String> lines = Files.readAllLines(Path.of(args[1]));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\t", -1);
            Set<Integer> gold = new HashSet<>();
            if (!fields[3].equals("-")) for (String page : fields[3].split(",")) gold.add(Integer.parseInt(page));
            keyword.search(fields[1], 5); // warm up this query
            List<Long> timings = new ArrayList<>();
            List<ScoredChunk> hits = List.of();
            for (int run = 0; run < 5; run++) {
                long start = System.nanoTime();
                hits = keyword.search(fields[1], 5);
                timings.add(System.nanoTime() - start);
            }
            Collections.sort(timings);
            int first = 0;
            Set<Integer> covered = new HashSet<>();
            List<Map<String, Object>> ranking = new ArrayList<>();
            for (int rank = 1; rank <= hits.size(); rank++) {
                var hit = hits.get(rank - 1);
                var c = hit.chunk();
                if (c.docId.equals(fields[2])) {
                    for (int page : gold) if (page >= c.pageFrom && page <= c.pageTo) {
                        covered.add(page);
                        if (first == 0) first = rank;
                    }
                }
                ranking.add(Map.of("chunk", c.id, "from", c.pageFrom, "to", c.pageTo, "score", hit.score()));
            }
            double rr = first == 0 ? 0 : 1.0 / first;
            double recall = gold.isEmpty() ? 0 : (double) covered.size() / gold.size();
            if (gold.isEmpty()) { negative++; if (hits.isEmpty()) negativeEmpty++; }
            else { positive++; hitSum += first > 0 ? 1 : 0; reciprocalSum += rr; recallSum += recall; }
            rows.add(Map.of("id", fields[0], "query", fields[1], "expectedDocument", fields[2],
                    "expectedPages", gold, "firstRelevantRank", first, "recallAt5", recall,
                    "medianMilliseconds", timings.get(2) / 1_000_000.0, "ranking", ranking));
        }
        int selfChecks = 0;
        for (float[] vector : store.vectors()) {
            var hits = dense.search(vector.clone(), 1, 0.999);
            if (!hits.isEmpty()) selfChecks++;
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("benchmark", "BM25 real-corpus seed-page relevance; no live embeddings or LLM");
        result.put("documents", store.meta().documents.size());
        result.put("chunks", store.meta().chunks.size());
        result.put("embeddingModel", store.meta().embeddingModel);
        result.put("dimension", store.meta().dimension);
        result.put("sourceHashes", store.meta().documents.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().sha256)));
        result.put("positiveQueries", positive);
        result.put("hitAt5", hitSum / positive);
        result.put("mrrAt5", reciprocalSum / positive);
        result.put("seedPageRecallAt5", recallSum / positive);
        result.put("negativeQueries", negative);
        result.put("negativeNoHitRate", (double) negativeEmpty / negative);
        result.put("denseSelfRetrievalPassed", selfChecks);
        result.put("denseSelfRetrievalTotal", store.vectors().length);
        result.put("results", rows);
        Path output = Path.of(args[2]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        JsonUtil.mapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), result);
        System.out.printf(Locale.ROOT, "BM25: Hit@5=%.4f MRR@5=%.4f seed-page Recall@5=%.4f; dense self-check=%d/%d%n",
                hitSum / positive, reciprocalSum / positive, recallSum / positive, selfChecks, store.vectors().length);
    }
}

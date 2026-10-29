package com.cst3492.ta;

import com.cst3492.ta.model.KnowledgeChunk;
import com.cst3492.ta.retrieval.HybridRetriever;
import com.cst3492.ta.retrieval.KeywordIndex;
import com.cst3492.ta.retrieval.Retriever;
import com.cst3492.ta.util.VectorMath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRetrieverTest {

    private static KnowledgeChunk chunk(String id, String text) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.id = id;
        c.docId = id.split("-")[0];
        c.docName = id + ".pdf";
        c.pageFrom = 1;
        c.pageTo = 1;
        c.title = "";
        c.text = text;
        return c;
    }

    @Test
    void keywordHitFusesIntoResults() {
        KnowledgeChunk aboutIoC = chunk("a-p1-c0", "The IoC container manages beans and @Autowired injection");
        KnowledgeChunk unrelated = chunk("b-p1-c0", "JSP pages render HTML views");
        // Dense vectors: a and b are orthogonal; query on the b direction so dense alone misses a.
        float[] va = {1, 0};
        float[] vb = {0, 1};
        VectorMath.normalize(va);
        VectorMath.normalize(vb);
        Retriever dense = new Retriever(List.of(aboutIoC, unrelated), new float[][]{va, vb});
        KeywordIndex keyword = new KeywordIndex(List.of(aboutIoC, unrelated));
        HybridRetriever hybrid = new HybridRetriever(keyword, dense);

        // Query text matches a's terms; query vector points at b (worst case for dense).
        float[] queryVector = {0, 1};
        var hits = hybrid.search("@Autowired IoC beans", queryVector, 2, 0.0);
        assertTrue(hits.stream().anyMatch(h -> h.chunk().id.equals("a-p1-c0")),
                "the keyword-only hit must survive via RRF");
    }

    @Test
    void rrfMathMatchesFormula() {
        // one hit from each ranking at rank 1: score = 1/(60+1) + 1/(60+1)
        KnowledgeChunk both = chunk("a-p1-c0", "IoC container beans @Autowired");
        VectorMath.normalize(new float[]{1});
        Retriever dense = new Retriever(List.of(both), new float[][]{{1}});
        KeywordIndex keyword = new KeywordIndex(List.of(both));
        HybridRetriever hybrid = new HybridRetriever(keyword, dense);
        var hits = hybrid.search("IoC beans", new float[]{1}, 1, 0.0);
        assertEquals(1, hits.size());
        assertEquals(2.0 / (HybridRetriever.RRF_K + 1), hits.get(0).score(), 1e-6);
    }
}

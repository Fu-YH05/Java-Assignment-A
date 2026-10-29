package com.cst3492.ta;

import com.cst3492.ta.model.KnowledgeChunk;
import com.cst3492.ta.retrieval.Retriever;
import com.cst3492.ta.retrieval.ScoredChunk;
import com.cst3492.ta.util.VectorMath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrieverTest {

    private static KnowledgeChunk chunk(String id) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.id = id;
        c.docId = id.split("-")[0];
        c.docName = id + ".pdf";
        c.pageFrom = 1;
        c.pageTo = 1;
        c.text = "text of " + id;
        return c;
    }

    @Test
    void identicalVectorsRankFirstAndThresholdFilters() {
        float[] v1 = {1, 0, 0};
        float[] v2 = {0, 1, 0};
        VectorMath.normalize(v1);
        VectorMath.normalize(v2);
        Retriever retriever = new Retriever(
                List.of(chunk("a-p1-c0"), chunk("b-p1-c0")),
                new float[][]{v1, v2});
        float[] query = {1, 0, 0};
        List<ScoredChunk> hits = retriever.search(query, 2, 0.35);
        assertEquals(1, hits.size());
        assertEquals("a-p1-c0", hits.get(0).chunk().id);
        assertEquals(1.0f, hits.get(0).score(), 1e-6);

        // a threshold above 1.0 rejects everything
        assertTrue(retriever.search(query, 2, 1.5).isEmpty());
    }

    @Test
    void topKIsRespectedAndTiesBreakById() {
        float[] v = {1, 0};
        VectorMath.normalize(v);
        Retriever retriever = new Retriever(
                List.of(chunk("b-p1-c0"), chunk("a-p1-c0"), chunk("c-p1-c0")),
                new float[][]{v, v, v});
        List<ScoredChunk> hits = retriever.search(new float[]{2, 0}, 2, 0.0);
        assertEquals(2, hits.size());
        assertEquals("a-p1-c0", hits.get(0).chunk().id);
        assertEquals("b-p1-c0", hits.get(1).chunk().id);
    }

    @Test
    void dimensionMismatchIsSkippedNotCrash() {
        Retriever retriever = new Retriever(
                List.of(chunk("a-p1-c0"), chunk("b-p1-c0")),
                new float[][]{{1, 0, 0}, {0, 1}});
        List<ScoredChunk> hits = retriever.search(new float[]{1, 0}, 2, 0.0);
        assertEquals(1, hits.size());
    }
}

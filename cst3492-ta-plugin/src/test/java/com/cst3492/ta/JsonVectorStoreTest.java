package com.cst3492.ta;

import com.cst3492.ta.index.IndexMeta;
import com.cst3492.ta.index.JsonVectorStore;
import com.cst3492.ta.model.KnowledgeChunk;
import com.cst3492.ta.util.VectorMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonVectorStoreTest {

    @TempDir
    Path dir;

    private static KnowledgeChunk chunk(String docId, String text) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.id = docId + "-p1-c0";
        c.docId = docId;
        c.docName = docId + ".pdf";
        c.pageFrom = 1;
        c.pageTo = 1;
        c.title = "";
        c.text = text;
        c.tokenCount = text.split("\\s+").length;
        c.sha256 = "deadbeef";
        return c;
    }

    @Test
    void saveAndLoadRoundTrip() throws Exception {
        JsonVectorStore store = new JsonVectorStore(dir);
        KnowledgeChunk c1 = chunk("lec01", "one two three");
        KnowledgeChunk c2 = chunk("lec02", "four five six");
        float[] v1 = {1, 0};
        float[] v2 = {0, 1};
        VectorMath.normalize(v1);
        VectorMath.normalize(v2);
        store.replaceDocument("lec01", List.of(c1), List.of(v1));
        store.replaceDocument("lec02", List.of(c2), List.of(v2));
        store.save("BAAI/bge-m3", 2);

        JsonVectorStore reloaded = new JsonVectorStore(dir);
        reloaded.load();
        assertEquals("BAAI/bge-m3", reloaded.meta().embeddingModel);
        assertEquals(2, reloaded.meta().dimension);
        assertEquals(2, reloaded.meta().chunks.size());
        assertEquals(0f, reloaded.vectors()[0][1], 1e-6);
        assertEquals(1f, reloaded.vectors()[1][1], 1e-6);
    }

    @Test
    void removeDocumentCompactsRows() throws Exception {
        JsonVectorStore store = new JsonVectorStore(dir);
        store.replaceDocument("lec01", List.of(chunk("lec01", "a b")),
                List.of(new float[]{1, 0}));
        store.replaceDocument("lec02", List.of(chunk("lec02", "c d")),
                List.of(new float[]{0, 1}));
        store.removeDocument("lec01");
        store.save("m", 2);
        JsonVectorStore reloaded = new JsonVectorStore(dir);
        reloaded.load();
        assertEquals(1, reloaded.meta().chunks.size());
        assertEquals("lec02", reloaded.meta().chunks.get(0).docId);
        assertEquals(0f, reloaded.vectors()[0][0], 1e-6);
        assertTrue(reloaded.meta().documents.containsKey("lec02"));
    }
}

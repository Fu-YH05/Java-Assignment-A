package com.cst3492.ta.index;

import com.cst3492.ta.model.KnowledgeChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Metadata side of the index: version, embedding identity, document table, chunk table. */
public class IndexMeta {
    public int version = 1;
    // Zero for older indexes whose short-page text may be missing.
    public int chunkingVersion = 0;
    public String embeddingBaseUrl = "";
    public String embeddingModel = "";
    public int dimension = 0;
    public Map<String, DocumentInfo> documents = new LinkedHashMap<>();
    public List<KnowledgeChunk> chunks = new ArrayList<>();

    public static class DocumentInfo {
        public String docName;
        public String sha256;
        public int pageCount;
        public int chunkCount;
    }
}

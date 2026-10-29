package com.cst3492.ta.model;

/** One retrieval unit: page-aware, with the metadata needed for honest citations. */
public class KnowledgeChunk {
    public String id;
    public String docId;
    public String docName;
    public int pageFrom;
    public int pageTo;
    public String title;
    public String text;
    public int tokenCount;
    public String sha256;

    public String citationLabel() {
        if (pageFrom == pageTo) {
            return docName + ", p." + pageFrom;
        }
        return docName + ", pp." + pageFrom + "-" + pageTo;
    }
}

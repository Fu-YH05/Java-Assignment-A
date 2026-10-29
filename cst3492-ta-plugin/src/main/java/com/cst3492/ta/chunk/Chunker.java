package com.cst3492.ta.chunk;

import com.cst3492.ta.model.KnowledgeChunk;
import com.cst3492.ta.parser.PageBlock;
import com.cst3492.ta.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into overlapping word windows. Short pages are accumulated with the next page,
 * retaining every word and tracking the actual source pages of each emitted window.
 */
public final class Chunker {

    public static final int WINDOW_TOKENS = 400;
    public static final int OVERLAP_TOKENS = 60;
    public static final int MAX_PAGE_TOKENS = 500;
    public static final int SHORT_PAGE_TOKENS = 60;

    private Chunker() {
    }

    public static List<KnowledgeChunk> chunkDocument(String docId, List<PageBlock> pages) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        List<Integer> pageNumbers = new ArrayList<>();
        PageBlock lastContentPage = null;
        for (PageBlock page : pages) {
            List<String> words = splitWords(page.text());
            if (words.isEmpty()) {
                continue;
            }
            pending.addAll(words);
            for (int i = 0; i < words.size(); i++) pageNumbers.add(page.pageNo());
            lastContentPage = page;
            if (words.size() >= SHORT_PAGE_TOKENS || pending.size() >= WINDOW_TOKENS) {
                emitWindows(chunks, docId, page, pending, pageNumbers);
                pending.clear();
                pageNumbers.clear();
            }
        }
        // A trailing short block still deserves a chunk.
        if (!pending.isEmpty()) {
            emitWindows(chunks, docId, lastContentPage, pending, pageNumbers);
        }
        return chunks;
    }

    private static void emitWindows(List<KnowledgeChunk> out, String docId, PageBlock page,
                                    List<String> words, List<Integer> pageNumbers) {
        int total = words.size();
        int step = WINDOW_TOKENS - OVERLAP_TOKENS;
        int windowIndex = 0;
        for (int start = 0; start < total; start += step) {
            int end = Math.min(start + WINDOW_TOKENS, total);
            String text = String.join(" ", words.subList(start, end));
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.id = docId + "-p" + page.pageNo() + "-c" + windowIndex;
            chunk.docId = docId;
            chunk.docName = page.docName();
            chunk.pageFrom = pageNumbers.get(start);
            chunk.pageTo = pageNumbers.get(end - 1);
            chunk.title = page.title() == null ? "" : page.title();
            chunk.text = text;
            chunk.tokenCount = TextUtil.estimateTokens(text);
            chunk.sha256 = TextUtil.sha256(TextUtil.normalize(text));
            out.add(chunk);
            windowIndex++;
            if (end == total) {
                break;
            }
        }
    }

    private static List<String> splitWords(String text) {
        List<String> words = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return words;
        }
        for (String w : text.trim().split("\\s+")) {
            if (!w.isBlank()) {
                words.add(w);
            }
        }
        return words;
    }
}

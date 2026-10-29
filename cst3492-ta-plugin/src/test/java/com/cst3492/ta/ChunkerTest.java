package com.cst3492.ta;

import com.cst3492.ta.chunk.Chunker;
import com.cst3492.ta.model.KnowledgeChunk;
import com.cst3492.ta.parser.PageBlock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkerTest {

    private static PageBlock page(String doc, int no, String title, int words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words; i++) {
            sb.append("w").append(i).append(' ');
        }
        return new PageBlock(doc, no, title, sb.toString());
    }

    private static int wordCount(String text) {
        return text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }

    @Test
    void shortPagesAreMergedForward() {
        List<PageBlock> pages = new ArrayList<>();
        pages.add(page("lec01", 1, "Intro", 30));
        pages.add(page("lec01", 2, "Body", 100));
        List<KnowledgeChunk> chunks = Chunker.chunkDocument("lec01", pages);
        assertEquals(1, chunks.size());
        assertEquals(1, chunks.get(0).pageFrom);
        assertEquals(2, chunks.get(0).pageTo);
        assertEquals(130, wordCount(chunks.get(0).text));
    }

    @Test
    void normalPageBecomesOneChunk() {
        List<KnowledgeChunk> chunks = Chunker.chunkDocument("lec01",
                List.of(page("lec01.pdf", 3, "IoC", 120)));
        assertEquals(1, chunks.size());
        assertEquals("lec01-p3-c0", chunks.get(0).id);
        assertEquals("lec01.pdf", chunks.get(0).docName);
        assertTrue(chunks.get(0).tokenCount > 0);
    }

    @Test
    void longPageIsSplitWithOverlap() {
        List<KnowledgeChunk> chunks = Chunker.chunkDocument("lec01",
                List.of(page("lec01.pdf", 5, "Long", 900)));
        assertEquals(3, chunks.size());
        assertTrue(wordCount(chunks.get(0).text) <= Chunker.WINDOW_TOKENS);
        // adjacent windows share exactly OVERLAP_TOKENS words
        String tail = lastWords(chunks.get(0).text, Chunker.OVERLAP_TOKENS);
        String head = firstWords(chunks.get(1).text, Chunker.OVERLAP_TOKENS);
        assertEquals(tail, head);
    }

    private static String firstWords(String text, int n) {
        String[] words = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, words.length); i++) {
            sb.append(words[i]).append(' ');
        }
        return sb.toString().trim();
    }

    @Test
    void emptyPagesAreSkipped() {
        List<KnowledgeChunk> chunks = Chunker.chunkDocument("lec01",
                List.of(new PageBlock("lec01.pdf", 2, "Blank", "   ")));
        assertTrue(chunks.isEmpty());
    }

    @Test
    void consecutiveShortPagesAndTrailingBlankPreserveAllContent() {
        var chunks = Chunker.chunkDocument("lecture", List.of(
                new PageBlock("lecture.pdf", 1, "First", "alpha unique"),
                new PageBlock("lecture.pdf", 2, "Second", "beta unique"),
                new PageBlock("lecture.pdf", 3, "Blank", " ")));
        assertEquals(1, chunks.size());
        assertEquals("alpha unique beta unique", chunks.get(0).text);
        assertEquals(1, chunks.get(0).pageFrom);
        assertEquals(2, chunks.get(0).pageTo);
    }

    @Test
    void windowsOnlyCitePagesWhoseTextTheyContain() {
        var chunks = Chunker.chunkDocument("lecture", List.of(
                new PageBlock("lecture.pdf", 1, "Short", "shortmarker"),
                new PageBlock("lecture.pdf", 2, "Long", "body ".repeat(900))));
        assertTrue(chunks.get(0).text.contains("shortmarker"));
        assertEquals(1, chunks.get(0).pageFrom);
        assertEquals(2, chunks.get(0).pageTo);
        assertEquals(2, chunks.get(1).pageFrom);
        assertEquals(901, chunks.stream().mapToInt(c -> wordCount(c.text)).sum()
                - (chunks.size() - 1) * Chunker.OVERLAP_TOKENS);
    }

    @Test
    void trailingShortPagesAreNotLostAfterNormalPage() {
        var chunks = Chunker.chunkDocument("lecture", List.of(
                page("lecture.pdf", 1, "Body", 100),
                new PageBlock("lecture.pdf", 2, "Short", "tail one"),
                new PageBlock("lecture.pdf", 3, "Short", "tail two")));
        assertEquals(2, chunks.size());
        assertEquals("tail one tail two", chunks.get(1).text);
        assertEquals(2, chunks.get(1).pageFrom);
        assertEquals(3, chunks.get(1).pageTo);
    }

    private static String lastWords(String text, int n) {
        String[] words = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, words.length - n); i < words.length; i++) {
            sb.append(words[i]).append(' ');
        }
        return sb.toString().trim();
    }
}

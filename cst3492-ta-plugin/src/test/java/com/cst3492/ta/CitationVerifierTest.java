package com.cst3492.ta;

import com.cst3492.ta.citation.CitationVerifier;
import com.cst3492.ta.model.KnowledgeChunk;
import com.cst3492.ta.prompt.PromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitationVerifierTest {

    private static KnowledgeChunk chunk(String docId, String docName, int from, int to) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.id = docId + "-p" + from + "-c0";
        c.docId = docId;
        c.docName = docName;
        c.pageFrom = from;
        c.pageTo = to;
        c.text = "excerpt";
        return c;
    }

    @Test
    void validCitationIsRewritten() {
        List<KnowledgeChunk> retrieved = List.of(chunk("lec04", "Lecture04.pdf", 12, 12));
        String answer = "The container wires beans {{cite:lec04#12}} as configured.";
        String out = CitationVerifier.verify(answer, retrieved);
        assertTrue(out.contains("[Lecture04.pdf, p.12]"));
        assertFalse(out.contains("{{cite:"));
    }

    @Test
    void wrongPageIsRemoved() {
        List<KnowledgeChunk> retrieved = List.of(chunk("lec04", "Lecture04.pdf", 12, 12));
        String answer = "Claim {{cite:lec04#99}} and fake doc {{cite:lec99#1}}.";
        String out = CitationVerifier.verify(answer, retrieved);
        assertFalse(out.contains("lec04#99"));
        assertFalse(out.contains("lec99#1"));
        assertTrue(out.contains("no verifiable course citation"));
    }

    @Test
    void pageRangeChunkAcceptsInnerPage() {
        List<KnowledgeChunk> retrieved = List.of(chunk("lec01", "Lecture01.pdf", 4, 6));
        String out = CitationVerifier.verify("X {{cite:lec01#5}}", retrieved);
        assertTrue(out.contains("[Lecture01.pdf, pp.4-6]"));
    }

    @Test
    void noRetrievalForcesDisclaimer() {
        String out = CitationVerifier.verify("Beans are managed by the container {{cite:lec04#3}}.",
                List.of());
        assertTrue(out.startsWith(PromptBuilder.GENERAL_DISCLAIMER));
        assertFalse(out.contains("{{cite:"));
    }

    @Test
    void existingDisclaimerIsNotDuplicated() {
        String input = PromptBuilder.GENERAL_DISCLAIMER + "\n\nBeans live in the container.";
        assertEquals(input, CitationVerifier.verify(input, List.of()));
    }

    @Test
    void filenamesWithSpacesParenthesesAndChineseAreVerified() {
        for (String name : List.of("Chapter_01-new (1)", "第1章 软件工程", "Lecture #1")) {
            String out = CitationVerifier.verify("Claim {{cite:" + name + "#6}}.",
                    List.of(chunk(name, name + ".pdf", 6, 6)));
            assertTrue(out.contains("[" + name + ".pdf, p.6]"));
            assertFalse(out.contains("{{cite:"));
        }
    }

    @Test
    void malformedAndOverflowingCitationsCannotLeakOrCrash() {
        String input = "Claim {{cite:missing#999999999999999}} {{cite:broken#abc}}\n"
                + "Incomplete {{cite:unclosed#1";
        String out = CitationVerifier.verify(input, List.of(chunk("lec", "Lecture.pdf", 1, 2)));
        assertFalse(out.contains("{{cite:"));
        assertTrue(out.contains("no verifiable course citation"));
        assertTrue(CitationVerifier.verify(input, List.of()).startsWith(PromptBuilder.GENERAL_DISCLAIMER));
    }
}

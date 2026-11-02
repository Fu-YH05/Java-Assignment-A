package com.cst3492.ta.prompt;

import com.cst3492.ta.model.KnowledgeChunk;

import java.util.List;

/** Assembles the Q&A prompts per Section 5.6.3 of the design. */
public final class PromptBuilder {

    public static final String GENERAL_DISCLAIMER =
            "Response is based on general knowledge; no specific course material is referenced.";

    private PromptBuilder() {
    }

    public static String systemGrounded() {
        return """
                You are the teaching assistant for CST3492 Java Enterprise Application Development.
                Answer in the same language as the student's question.
                Use only the numbered course excerpts for course-specific claims.
                After every claim taken from an excerpt, emit {{cite:docId#page}} where docId and page \
                match one of the excerpts exactly.
                If the excerpts are insufficient for the question, begin your answer with:
                "%s"
                Never invent a document, page number or API that is not present in the excerpts.
                """.stripIndent().formatted(GENERAL_DISCLAIMER);
    }

    public static String systemGeneral() {
        return """
                You are the teaching assistant for CST3492 Java Enterprise Application Development.
                No course material matched this question. Answer with general knowledge in the same \
                language as the question. Do not invent course references or citations.
                Begin your answer with:
                "%s"
                """.stripIndent().formatted(GENERAL_DISCLAIMER);
    }

    /** Single retry turn used when a grounded reply contained no verifiable citation. */
    public static String citationRetryHint() {
        return """
                Your previous answer contained no valid {{cite:docId#page}} token.
                Answer again using only the numbered excerpts above.
                Cite each course-specific claim with {{cite:docId#page}} copied from an excerpt header.
                If the excerpts are insufficient, begin the answer with:
                "%s"
                """.stripIndent().formatted(GENERAL_DISCLAIMER).strip();
    }

    /** Single retry turn used when the provider returned HTTP 200 with no visible answer text. */
    public static String emptyAnswerRetryHint() {
        return """
                Your previous reply was empty.
                Answer the question now in complete sentences.
                When course excerpts are provided, cite each course-specific claim with
                {{cite:docId#page}} copied from an excerpt header. If they are insufficient, begin with:
                "%s"
                """.stripIndent().formatted(GENERAL_DISCLAIMER).strip();
    }

    public static String userPrompt(String question, String codeContext, List<KnowledgeChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Question: ").append(question).append('\n');
        if (codeContext != null && !codeContext.isBlank()) {
            sb.append("Code context:\n```java\n").append(codeContext.trim()).append("\n```\n");
        }
        if (chunks != null && !chunks.isEmpty()) {
            sb.append("Retrieved course excerpts:\n");
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = chunks.get(i);
                sb.append('[').append(i + 1).append("] docId=").append(chunk.docId)
                        .append(", page=").append(chunk.pageFrom)
                        .append(", title=").append(chunk.title).append('\n');
                sb.append(chunk.text).append("\n\n");
            }
        }
        return sb.toString();
    }
}

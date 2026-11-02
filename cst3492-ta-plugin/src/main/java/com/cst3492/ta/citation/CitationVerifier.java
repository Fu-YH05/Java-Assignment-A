package com.cst3492.ta.citation;

import com.cst3492.ta.model.KnowledgeChunk;
import com.cst3492.ta.prompt.PromptBuilder;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whitelist verifier: the model may only cite document/page pairs that were actually retrieved,
 * and the cited page must fall inside the chunk's page range. Invalid tokens are removed; a
 * retrieval-based answer with no surviving citation gets a warning appended.
 */
public final class CitationVerifier {

    private static final Pattern CITE = Pattern.compile("\\{\\{cite:([^{}\\r\\n]*)\\}\\}");

    private CitationVerifier() {
    }

    public static String verify(String answer, List<KnowledgeChunk> retrieved) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        if (retrieved == null || retrieved.isEmpty()) {
            // No retrieval happened: strip any tokens and guarantee the disclaimer.
            String stripped = stripTokens(answer);
            return ensureDisclaimer(stripped);
        }

        Matcher matcher = CITE.matcher(answer);
        StringBuilder sb = new StringBuilder();
        boolean citationSurvived = false;
        int last = 0;
        while (matcher.find()) {
            sb.append(answer, last, matcher.end() - matcher.group(0).length());
            String label = sourceLabel(matcher.group(1), retrieved);
            if (label != null) {
                sb.append('[').append(label).append(']');
                citationSurvived = true;
            }
            last = matcher.end();
        }
        sb.append(answer.substring(last));
        String result = sb.toString();
        if (!citationSurvived) {
            result = result + "\n\n(Note: no verifiable course citation survived verification; "
                    + "inspect the Sources list.)";
        }
        return result;
    }

    /** Resolves only a document/page pair supplied in this request, never a model-invented source. */
    public static String sourceLabel(String reference, List<KnowledgeChunk> retrieved) {
        if (reference == null || retrieved == null) return null;
        int separator = reference.lastIndexOf('#');
        if (separator <= 0) return null;
        try {
            String pageText = reference.substring(separator + 1);
            if (!pageText.matches("[0-9]+")) return null;
            KnowledgeChunk match = find(retrieved, reference.substring(0, separator), Integer.parseInt(pageText));
            return match == null ? null : match.citationLabel();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static KnowledgeChunk find(List<KnowledgeChunk> retrieved, String docId, int page) {
        for (KnowledgeChunk chunk : retrieved) {
            if (chunk.docId.equals(docId) && page >= chunk.pageFrom && page <= chunk.pageTo) {
                return chunk;
            }
        }
        return null;
    }

    private static String stripTokens(String answer) {
        return CITE.matcher(answer).replaceAll("").strip();
    }

    private static String ensureDisclaimer(String answer) {
        if (answer.contains(PromptBuilder.GENERAL_DISCLAIMER)) {
            return answer;
        }
        return PromptBuilder.GENERAL_DISCLAIMER + "\n\n" + answer;
    }
}

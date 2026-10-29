package com.cst3492.ta.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses course materials page by page. Page identity is a hard requirement for citations,
 * so PDFs are extracted one page at a time instead of as one text blob.
 */
public final class MaterialParser {

    private MaterialParser() {
    }

    public static List<PageBlock> parse(Path file) throws IOException {
        String name = file.getFileName().toString();
        if (name.toLowerCase().endsWith(".pdf")) {
            return parsePdf(file, name);
        }
        throw new IOException("Unsupported material type: " + name + " (PDF only in this release)");
    }

    private static List<PageBlock> parsePdf(Path file, String name) throws IOException {
        List<PageBlock> pages = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(file.toFile())) {
            int count = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 1; i <= count; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc);
                String title = guessTitle(text);
                pages.add(new PageBlock(name, i, title, text));
            }
        }
        return pages;
    }

    /** Uses the first non-empty line as the page title when it is short enough to be one. */
    static String guessTitle(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.length() <= 80) {
                return trimmed;
            }
            if (!trimmed.isEmpty()) {
                return trimmed.substring(0, 80);
            }
        }
        return "";
    }
}

package com.cst3492.ta.service;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/** Extract one Java artifact; never guess which of several model code blocks should be written. */
public final class GeneratedTestCode {
    private GeneratedTestCode() { }

    public static String extract(String response) {
        if (response == null || response.isBlank()) throw new IllegalArgumentException("The model returned no test code.");
        List<FencedCodeBlock> blocks = new ArrayList<>();
        Parser.builder().build().parse(response).accept(new AbstractVisitor() {
            @Override public void visit(FencedCodeBlock block) { blocks.add(block); }
        });
        if (blocks.isEmpty()) return response.strip() + "\n";
        if (blocks.size() != 1 || !(blocks.getFirst().getInfo().isBlank()
                || blocks.getFirst().getInfo().strip().equalsIgnoreCase("java"))) {
            throw new IllegalArgumentException("Expected one complete Java test class; the response contains multiple or non-Java code blocks. Refine the scenario and retry.");
        }
        String code = blocks.getFirst().getLiteral().strip();
        if (code.isBlank()) throw new IllegalArgumentException("The model returned an empty Java code block.");
        return code + "\n";
    }
}
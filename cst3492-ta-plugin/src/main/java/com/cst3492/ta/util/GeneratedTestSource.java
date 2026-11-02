package com.cst3492.ta.util;

import java.util.*;
import java.util.regex.Pattern;

/** Extracts file identity without a system compiler. The IDE validates full syntax before writing. */
public record GeneratedTestSource(String code, String packageName, String className) {
    public static GeneratedTestSource parse(String response) {
        String code = response == null ? "" : response.strip();
        if (code.startsWith("```")) {
            int newline = code.indexOf('\n');
            if (newline < 0 || !code.endsWith("```")) throw new IllegalArgumentException("Incomplete code fence.");
            code = code.substring(newline + 1, code.length() - 3).strip();
        }
        String clean = maskCommentsAndLiterals(code);
        String identifier = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
        var packages = Pattern.compile("\\bpackage\\s+(" + identifier + "(?:\\s*\\.\\s*" + identifier + ")*)\\s*;").matcher(clean);
        String packageName = packages.find() ? packages.group(1).replaceAll("\\s+", "") : "";
        var declarations = Pattern.compile("\\b(public\\s+)?(?:(?:abstract|final|static|strictfp|sealed|non-sealed)\\s+)*"
                + "(class|interface|enum|record)\\s+(" + identifier + ")").matcher(clean);
        List<String> classes = new ArrayList<>(), publicClasses = new ArrayList<>();
        int publicTypes = 0;
        while (declarations.find()) {
            if (depthAt(clean, declarations.start()) != 0) continue;
            boolean isPublic = declarations.group(1) != null;
            if (isPublic) publicTypes++;
            if (declarations.group(2).equals("class")) {
                classes.add(declarations.group(3));
                if (isPublic) publicClasses.add(declarations.group(3));
            }
        }
        if (classes.isEmpty() || publicTypes > 1 || publicTypes == 1 && publicClasses.isEmpty()) {
            throw new IllegalArgumentException("Expected one Java test file with at most one public top-level class.");
        }
        return new GeneratedTestSource(code, packageName,
                publicClasses.isEmpty() ? classes.get(0) : publicClasses.get(0));
    }
    private static int depthAt(String text, int end) {
        int depth = 0;
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '{') depth++;
            else if (text.charAt(i) == '}') depth--;
        }
        return depth;
    }
    private static String maskCommentsAndLiterals(String code) {
        char[] clean = code.toCharArray();
        int depth = 0;
        for (int i = 0; i < code.length();) {
            int start = i, end = i;
            if (code.startsWith("//", i)) {
                end = code.indexOf('\n', i + 2);
                if (end < 0) end = code.length();
            } else if (code.startsWith("/*", i)) {
                end = code.indexOf("*/", i + 2);
                if (end < 0) throw new IllegalArgumentException("Unclosed comment.");
                end += 2;
            } else if (code.startsWith("\"\"\"", i)) {
                end = i + 3;
                while (true) {
                    end = code.indexOf("\"\"\"", end);
                    if (end < 0) throw new IllegalArgumentException("Unclosed text block.");
                    int slashes = 0;
                    for (int j = end - 1; j >= 0 && code.charAt(j) == '\\'; j--) slashes++;
                    if (slashes % 2 == 0) { end += 3; break; }
                    end++;
                }
            } else if (code.charAt(i) == '"' || code.charAt(i) == '\'') {
                char quote = code.charAt(i);
                end = i + 1;
                boolean closed = false;
                while (end < code.length()) {
                    char c = code.charAt(end++);
                    if (c == '\\') end++;
                    else if (c == quote) { closed = true; break; }
                }
                if (!closed) throw new IllegalArgumentException("Unclosed Java literal.");
            }
            if (end > start) {
                Arrays.fill(clean, start, Math.min(end, clean.length), ' ');
                i = end;
            } else {
                char c = code.charAt(i++);
                if (c == '{') depth++;
                else if (c == '}' && --depth < 0) throw new IllegalArgumentException("Unbalanced Java braces.");
            }
        }
        if (depth != 0) throw new IllegalArgumentException("Unbalanced Java braces.");
        return new String(clean);
    }
    public String relativePath() {
        return (packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/") + className + ".java";
    }
}

package com.cst3492.ta.ui;

/** Small inert renderer: paragraphs, fenced code, inline code and bold; raw HTML is escaped. */
public final class MarkdownView {
    private MarkdownView() {}
    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    public static String render(String markdown) {
        StringBuilder html = new StringBuilder();
        boolean code = false;
        for (String line : markdown.split("\\R", -1)) {
            if (line.stripLeading().startsWith("```")) {
                html.append(code ? "</pre>" : "<pre>");
                code = !code;
            } else if (code) {
                html.append(escape(line)).append('\n');
            } else {
                String escaped = escape(line).replaceAll("`([^`]+)`", "<code>$1</code>")
                        .replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
                html.append(escaped).append("<br>");
            }
        }
        if (code) html.append("</pre>");
        return html.toString();
    }
}

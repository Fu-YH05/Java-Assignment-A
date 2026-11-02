package com.cst3492.ta.util;

import java.util.*;
import java.util.function.*;

/** Bounded map/reduce: every staged diff character enters a leaf; every leaf enters the reduction. */
public final class DiffSummarizer {
    public static final int BUDGET = 6000;
    private DiffSummarizer() {}
    public static List<String> parts(String diff) {
        List<String> result = new ArrayList<>();
        for (String file : diff.split("(?m)(?=^diff --git )")) {
            if (file.isBlank()) continue;
            String header = file.lines().findFirst().orElse("diff");
            header = header.substring(0, Math.min(400, header.length()));
            int payload = BUDGET - header.length() - 60;
            for (int start = 0, part = 1; start < file.length(); start += payload, part++) {
                result.add(header + "\nPart " + part + ":\n" + file.substring(start, Math.min(start + payload, file.length())));
            }
        }
        return result;
    }
    public static String summarize(String diff, Function<String, String> summarize, Runnable cancelled,
                                   Consumer<String> progress) {
        List<String> parts = parts(diff);
        if (parts.isEmpty()) return "";
        List<String> summaries = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            cancelled.run();
            progress.accept("Analyzing diff part " + (i + 1) + "/" + parts.size());
            summaries.add(bound(summarize.apply(parts.get(i))));
        }
        int round = 1;
        while (String.join("\n", summaries).length() > BUDGET) {
            List<String> reduced = new ArrayList<>();
            for (int i = 0; i < summaries.size(); i += 3) {
                cancelled.run();
                progress.accept("Combining summaries (round " + round + ")");
                reduced.add(bound(summarize.apply(String.join("\n",
                        summaries.subList(i, Math.min(i + 3, summaries.size()))))));
            }
            summaries = reduced;
            round++;
        }
        return String.join("\n", summaries);
    }
    private static String bound(String summary) {
        // Fail visibly instead of silently dropping an overlong model summary.
        if (summary == null || summary.isBlank() || summary.length() > 1500) {
            throw new IllegalStateException("The model summary is empty or too long; retry with a shorter response.");
        }
        return summary;
    }
}

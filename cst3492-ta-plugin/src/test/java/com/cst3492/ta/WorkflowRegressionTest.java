package com.cst3492.ta;

import com.cst3492.ta.util.*;
import com.cst3492.ta.ui.MarkdownView;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowRegressionTest {
    @Test void largeDiffIncludesLateFilesAndEveryCharacter() {
        String first = "diff --git a/A b/A\n" + "x".repeat(20000);
        String last = "diff --git a/Z b/Z\n+LATE_CHANGE";
        var parts = DiffSummarizer.parts(first + "\n" + last);
        assertTrue(parts.get(parts.size() - 1).contains("LATE_CHANGE"));
        assertTrue(parts.stream().allMatch(p -> p.length() <= DiffSummarizer.BUDGET));
        assertEquals(first + "\n" + last, parts.stream().map(p -> p.substring(p.indexOf(":\n") + 2))
                .reduce("", String::concat));
    }
    @Test void mapReduceVisitsAllPartsWithBoundedRequests() {
        String diff = "diff --git a/A b/A\n" + "x".repeat(100000);
        List<String> requests = new ArrayList<>();
        String result = DiffSummarizer.summarize(diff, text -> {
            requests.add(text);
            return "summary ".repeat(150);
        }, () -> {}, text -> {});
        assertTrue(requests.size() > DiffSummarizer.parts(diff).size());
        assertTrue(requests.stream().allMatch(p -> p.length() <= DiffSummarizer.BUDGET));
        assertTrue(result.length() <= DiffSummarizer.BUDGET);
    }
    @Test void cancellationStopsFurtherDiffRequests() {
        assertThrows(CancellationException.class, () -> DiffSummarizer.summarize("diff --git a/A b/A\n+X",
                text -> { fail("No model request after cancellation"); return ""; },
                () -> { throw new CancellationException(); }, text -> {}));
    }
    @Test void overlongSummaryFailsInsteadOfDroppingChanges() {
        assertThrows(IllegalStateException.class, () -> DiffSummarizer.summarize("diff --git a/A b/A\n+X",
                text -> "x".repeat(1501), () -> {}, text -> {}));
    }
    @Test void taskCancellationInterruptsWorker() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        try {
            Future<String> worker = executor.submit(() -> {
                started.countDown();
                try { new CountDownLatch(1).await(); return "unexpected"; }
                catch (InterruptedException ex) { interrupted.countDown(); throw ex; }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertThrows(CancellationException.class, () -> TaskAwaiter.await(worker, () -> true));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertTrue(worker.isCancelled());
        } finally { executor.shutdownNow(); }
    }
    @Test void renderingPreservesCodeAndEscapesActiveHtml() {
        String rendered = MarkdownView.render("**Heading**\n\n```java\nif (a < b) {\n  go();\n}\n```\n<script>x</script>");
        assertTrue(rendered.contains("<b>Heading</b>"));
        assertTrue(rendered.contains("  go();\n"));
        assertTrue(rendered.contains("a &lt; b"));
        assertFalse(rendered.contains("<script>"));
        assertTrue(rendered.contains("&lt;script&gt;"));
    }
}

package com.cst3492.ta.ui;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import java.io.PrintWriter;

/** Uses the existing JUnit engine without the IDE test class loader or Gradle worker bootstrap. */
public final class ChatUiChecks {
    public static void main(String[] args) {
        var request = LauncherDiscoveryRequestBuilder.request().selectors(
                DiscoverySelectors.selectClass(ChatViewTest.class),
                DiscoverySelectors.selectClass(MarkdownContentTest.class),
                DiscoverySelectors.selectClass("com.cst3492.ta.CitationVerifierTest")).build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        var summary = listener.getSummary();
        var out = new PrintWriter(System.out, true);
        summary.printTo(out);
        summary.printFailuresTo(out);
        if (summary.getTestsFoundCount() == 0 || summary.getTestsFailedCount() > 0)
            throw new AssertionError("Offline chat UI regression checks failed");
    }
}

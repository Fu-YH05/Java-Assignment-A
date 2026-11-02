# CST3492 TA (Group B)

IntelliJ IDEA plugin implementing course-material Q&A (F0), scenario-based JUnit generation,
staged Git commit message drafts, and course-aware code review (F1).

## Configure and use

1. Install the plugin ZIP from `build/distributions` using Settings > Plugins > Install Plugin from Disk.
2. Open Settings > Tools > CST3492 TA. Configure the chat URL/model and its **Chat API key**.
3. Configure the embedding URL/model and its separate **Embedding API key**. The defaults use
   DeepSeek for chat and SiliconFlow for embeddings; supply a key belonging to each service.
   Existing keys from older versions are retained for chat only. Blank key fields keep saved keys.
4. Choose a folder containing text-based course PDF files, then apply settings.
5. Open the CST3492 TA tool window and click **Build Index**. Rebuild after upgrading from the
   old chunker or changing embedding models/providers. Unchanged PDFs are reused only when the
   chunker, model and provider match. A rebuild refreshes retrieval immediately.
6. Ask a question in the tool window. To ask about code, select it in the editor, right-click
   **Ask TA about Selected Code**, enter your question and submit. Answers appear in the TA window.

Test Connection sends small real requests to both configured services, using entered keys or
saved keys. Provider usage charges may apply. It checks actual responses rather than accepting
HTTP error statuses as successful connections.

## Chat window

The chat window separates questions, TA answers and request errors. Answers render Markdown
headings, lists and inline code; code blocks have their own horizontal scrollbar and Copy code
action. Copy answer preserves the original answer and its document/page references. Expand
Course sources to inspect the retrieved documents and pages, or click a numbered reference.

The composer accepts multiple lines: Enter sends and Shift+Enter inserts a new line. A failed
request keeps the question and selected-code context for retry; a new draft is preserved if
you already started typing one. While a request is pending, repeated submissions are disabled.
When reading an earlier answer, new responses preserve the scroll position and a button offers
to return to the latest response. The options menu opens Settings or clears the visible
conversation. Clear conversation removes the turns of the current saved chat while preserving its draft.

## Conversation navigation and history

The PR #2 (`chat-ui-refresh`, commit `31541df`) test-generation workflow is integrated with
the local turn navigator/history rather than replacing its storage format. Generate Tests from
Requirement now opens a dedicated chat bound to the source file/package and selected-code snapshot.
Enter the scenario in the composer before generating. The result offers Create Test File / Open
Test File, validates Java syntax and the bound package, resolves the source module, selects among
test roots, and warns if JUnit 5 is unavailable. An absent test root is created and registered.
Files are never overwritten. Search history matches conversation titles and questions.

Click **History** in the toolbar to show or hide the sidebar; drag its divider to adjust its width.
The **Turns** tab numbers each question, shows its status, and jumps to that question when selected.
The **History** tab opens a saved conversation. **New chat** starts a separate conversation;
**Delete chat** asks for confirmation before deleting the current one. Switching, clearing or
deleting conversations is disabled while a request or index build is running.

Questions, submitted code, answers, source document/page labels and per-conversation drafts are
saved automatically. Reopening the project restores the last chat without calling a provider.
Requests interrupted by closing the IDE are marked interrupted, rather than automatically resent.
History is stored as local JSON under the IDE system directory at
`cst3492-ta/history/<project-location-hash>.json`; each project has a separate file. Credentials,
retrieved PDF text and vectors are excluded. Stored questions and selected code are local plaintext;
they are removed from the active history file when the conversation is deleted. Conversation
history is for browsing and continuing the UI session; earlier turns are not automatically included
in the LLM prompt. A read failure leaves the original file untouched and allows temporary in-memory
chat; a visible status reports history load/save failures. Build-index notifications are not chat turns.

For offline UI checks run `gradlew.bat chatUiTest`. For an interactive view of the same Swing
component with local callbacks, run `gradlew.bat previewChatUi`. The preview's States, Theme,
Width and Local reply menus are verification controls, not part of the installed plugin.

## Build and verification

Requires JDK 21. Run `gradlew.bat test buildPlugin` (Windows) or `./gradlew test buildPlugin`
(macOS/Linux) from this folder; the wrapper selects Gradle 8.14. The target IDE
is IntelliJ IDEA Community 2024.2.6. The ZIP is written to `build/distributions`.

The tests include short-page text preservation, accurate window page ranges, immediate retrieval
after the first build, refresh after material changes, model/provider changes, and preservation
of the previous active snapshot when embedding fails during a rebuild. Index tests use local PDF
fixtures and deterministic embeddings; they do not need credentials or contact real providers.

## Revision verification

See [the verification supplement](verification/REPORT.md) for citation fixes, independent test-file
creation, cancellable tasks, complete staged-diff summarization, and a real-corpus BM25 benchmark.
The benchmark includes a failing unrelated-question case; it does not establish LLM answer quality.
Chat supports an escaped Markdown subset. Generated tests are syntax-checked and saved to a new
file after preview, rather than inserted into production code. Use the chat Cancel button or the
IDE background-task controls to cancel requests. Larger diffs require multiple model calls.

For manual acceptance, configure both real keys, build an index, ask a question immediately,
verify its source pages, then ask a custom question about selected Java code. Change a PDF and
rebuild to check that the new answer uses the updated material. Also demonstrate the three F1 actions.

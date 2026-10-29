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

## Build and verification

Requires JDK 21 and Gradle 8.14. Run `gradle test buildPlugin` from this folder. The target IDE
is IntelliJ IDEA Community 2024.2.6. The ZIP is written to `build/distributions`.

The tests include short-page text preservation, accurate window page ranges, immediate retrieval
after the first build, refresh after material changes, model/provider changes, and preservation
of the previous active snapshot when embedding fails during a rebuild. Index tests use local PDF
fixtures and deterministic embeddings; they do not need credentials or contact real providers.

For manual acceptance, configure both real keys, build an index, ask a question immediately,
verify its source pages, then ask a custom question about selected Java code. Change a PDF and
rebuild to check that the new answer uses the updated material. Also demonstrate the three F1 actions.

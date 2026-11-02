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
5. Open the CST3492 TA tool window and choose **⋯ > Build index**. Rebuild after upgrading from the
   old chunker or changing embedding models/providers. Unchanged PDFs are reused only when the
   chunker, model and provider match. A rebuild refreshes retrieval immediately.
6. Ask a question in the tool window. To ask about code, select it in the editor, right-click
   **Ask TA about Selected Code**, enter your question and submit. Answers appear in the TA window.

Test Connection sends small real requests to both configured services, using entered keys or
saved keys. Provider usage charges may apply. It checks actual responses rather than accepting
HTTP error statuses as successful connections.

## Chat window

The header keeps the conversation title on the left and compact History, ＋ and ⋯ controls on
the right. Long titles shorten to fit; hover to see the full title. Index building/rebuilding
lives under ⋯, with document and chunk counts in tooltips instead of a separate toolbar row.

The chat window separates questions, TA answers and request errors. Answers render Markdown
headings, lists and inline code; code blocks have their own horizontal scrollbar and Copy code
action. Copy answer preserves the original answer and its document/page references. Expand
Course sources to inspect the retrieved documents and pages, or click a numbered reference.

The composer accepts multiple lines: Enter sends and Shift+Enter inserts a new line. A failed
request keeps the question and selected-code context for retry; a new draft is preserved if
you already started typing one. While a request is pending, repeated submissions are disabled.
When reading an earlier answer, new responses preserve the scroll position and a button offers
to return to the latest response. **History** searches and switches this project's conversations;
**＋** (New chat) opens the pending ordinary draft, reusing it on repeated clicks. A conversation
appears in History only after the model returns content; unsent drafts, pending requests and
failed requests without model output stay out of that list. A later failure never removes an
already established conversation. Each conversation retains its own draft and pending response.
Background responses return to the conversation that started them.

Messages, source references, test artifacts, created-file paths and unsent drafts persist locally
in the project's IDEA workspace state (`.idea/workspace.xml`). They are restored when reopening
the project. A request interrupted by IDE shutdown becomes retryable and is never automatically
resent. The options menu clears the current conversation's saved messages while retaining its
draft. Saving history does not send previous turns to the model or add multi-turn model memory.

## Generate unit tests

In a Java editor, select a class or method and right-click **Generate Tests from Requirement**.
With no selection, the whole current file is used. Each invocation opens a new test conversation,
binding both the selection and containing source to a snapshot taken at invocation time.
The source snapshot is displayed inline as a read-only Java code block with highlighting,
copying and independent scrolling; it is hidden along with the chat when viewing History.
Enter a natural-language scenario, then click **Generate tests**. Test generation uses the chat
provider and does not require a course index or embedding key. Further scenarios and retries
stay in the same task; previous results remain visible.

The result displays a cleaned JUnit 5 class and its proposed path. **Create test file** writes a
separate Java file and opens it in IDEA; later visits offer **Open test file**. The plugin checks
Java syntax, one top-level class, and the source package before offering creation. It uses the
bound source module's test source root, asking when there are several. If none exists, it creates
and marks `src/test/java` under that file's content root. Existing files are never overwritten;
move or rename an existing file before creating another result with the same name.

Missing JUnit 5 is reported before creation, with an option to create anyway. Dependencies are
not modified and tests are not executed automatically. Syntax validation is not a compile/run
guarantee: review the generated assertions and run the test through IDEA. If a bound source or
created file has moved, the task reports that instead of silently targeting the current editor.

For offline UI checks run `gradlew.bat chatUiTest`. For an interactive view of the same Swing
component with local callbacks, run `gradlew.bat previewChatUi`. The preview's States, Theme,
Width and Local reply menus are verification controls, not part of the installed plugin.

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

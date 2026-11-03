
# 1. Team Particulars

CST3492 TA - Project Report

CST3492: Java Enterprise Application Development | Assignment A | AY 2026-2027, Semester 1 | Group B: F0 + F1

Report date: 3 November 2026. Implementation reviewed: the local integration of chat-ui-refresh PR #2 (31541df), together with the retained indexing, citation, cancellation and history improvements. Plugin descriptor version: 1.1.0.

## Team members

Complete the blank fields below before submission. Add or remove rows to match the actual team. No personal details or individual contributions have been inferred.

| Full name | Matriculation number | Email address | Contact number |
| --- | --- | --- | --- |
|  |  |  |  |
|  |  |  |  |
|  |  |  |  |
|  |  |  |  |


## Additional particulars

Team / tutorial section: __________________________________________

Primary contact person: _________________________________________

Repository: https://github.com/Fu-YH05/Java-Assignment-A

## Purpose and reading order

The plugin helps students ask course-specific Java questions inside IntelliJ IDEA and automate routine testing, commit-message drafting and code review. Its RAG pipeline preserves document/page provenance so a student can inspect the lecture source rather than relying only on a plausible model answer.

This report follows the required submission order: team particulars; implemented functionalities; user manual with UI samples; architecture, workflow, RAG structures, algorithms, LLM integration, design rationale and efficiency; and additional technical information. It distinguishes verified implementation from manual acceptance still required.


# 2. Implemented Functionalities

| Requirement | Implemented behavior |
| --- | --- |
| F0: course RAG Q&A | Text PDFs are parsed page by page, chunked and embedded. Query embeddings retrieve contextual excerpts for a model answer. |
| F0: source citations | Document/page tokens are checked against the retrieved set. Accepted references become readable labels and numbered source links. |
| F0: general-knowledge disclosure | Empty retrieval forces an explicit disclaimer. Prompts also request disclosure when excerpts are insufficient. |
| F0: embedded UI | A registered IDEA tool window provides Markdown answers, code copying/highlighting, source disclosure, errors and cancellation. |
| F0: editor context | An editor right-click action sends a student question and a snapshot of selected code. |
| F1: scenario-based JUnit | A dedicated test chat binds source/selection/package context and requires a nonblank scenario before generation. |
| F1: staged commit message | All staged textual changes enter a bounded diff/summarization pipeline, producing a Conventional Commit draft. |
| F1: creative action | Course-aware code review returns problems, explanations, severity, suggestions and checked source references. |


## Additional features

The Turns sidebar numbers questions and navigates to their cards. Project-local conversation history supports new/open/delete operations, title/question search, draft restoration and source/code snapshots. Interrupted requests are restored without automatically resending them. Existing transcripts remain compatible with the retained local history schema.

The chat input box accepts drag-and-drop from the file system: text files are appended with a separator, while PDFs and images show guidance dialogs directing the user to the Materials folder or clarifying the OCR limitation. Multiple-file and unsupported-type drops are rejected with an explanation.

Test artifacts can be created or reopened from the chat. The file service validates Java syntax and the bound package, resolves the source module and test root, warns when JUnit 5 is absent, and refuses to overwrite an existing file. A missing test source root can be created and registered.

## Scope boundaries

This is a Group B implementation. Group C F2 code modification with a built-in side-by-side approval viewer is outside its required scope. This release does not implement OCR, DOCX/PPTX ingestion, token streaming or multi-turn model memory. Stored transcript history is a browsing feature; earlier turns are not automatically included in later prompts.


# 3. User Manual

## 3.1 Installation and configuration

Use IntelliJ IDEA Community 2024.2.6 with Java 21 compatibility. Install the plugin ZIP through Settings > Plugins > Install Plugin from Disk, then restart if requested. To develop from source, use JDK 21 or a compatible Gradle JVM and run gradlew.bat test buildPlugin from cst3492-ta-plugin. The normal development launcher is gradlew.bat runIde.

Open Settings > Tools > CST3492 TA. Configure the chat endpoint/model and Chat API key, the separate embedding endpoint/model and Embedding API key, and a local folder of text PDFs. The defaults are DeepSeek chat (https://api.deepseek.com; deepseek-chat) and SiliconFlow embeddings (https://api.siliconflow.cn/v1; BAAI/bge-m3). Supply credentials belonging to each provider. Blank key fields preserve saved keys. Apply the settings; Test Connection sends real requests to both services.

Open CST3492 TA and click **Build index**. The index rescans the Materials folder each run, so any PDF added since the previous build is automatically parsed, chunked and embedded; unchanged documents are skipped by SHA-256 hash. Rebuild after changing materials, embedding provider/model or chunking format.

The chat input box supports drag-and-drop from the file system: text files (.txt, .md, source code, …) are appended with a separator; PDFs and images show a dialog directing the user to the Materials folder / OCR limitation; and unsupported types are rejected with an explanation.

Ask a question, inspect the answer and expand **Course sources**. Click a numbered reference to locate its source row. Enter sends; Shift+Enter inserts a newline. Copy code copies only the code; Copy answer preserves the answer and references.

![Figure 1: Settings — Tools — CST3492 TA. Configure chat/embedding endpoints and the Materials folder (PDFs).](images/F1.png)

![Figure 2: RAG answer with course-source citations (5 documents, paginated references).](images/F3.png)


## 3.2 Selected-code questions and history

Select a Java code segment, right-click Ask TA about Selected Code, and enter a question. The plugin opens the TA window and includes the selection snapshot. Check the selected-code indicator before submitting further questions. A request failure preserves the question/context for retry, while an unrelated draft is kept.

Use History to show or hide the sidebar. Turns lists numbered questions and their request state; selecting one scrolls to its question. History lists saved conversations. Search history matches titles and questions. New chat starts a separate transcript; Delete chat asks for confirmation. Session switching/deletion is blocked during a request or build. Clear conversation removes the current turns while retaining its draft. Closing and reopening the project restores its last conversation.

![Figure 3: Editor right-click — Ask TA about Selected Code — question dialog bound to the selection.](images/F5.png)

![Figure 4: Multilingual conversation with History sidebar, Turns navigator and draft restoration while a prior request is still in flight.](images/F10.png)


## 3.3 Generate JUnit tests from a requirement

In a Java file, select the target method or class and invoke Generate Tests from Requirement. The action captures the containing source, selection, package and source path, then opens a dedicated test conversation showing the bound snapshot. Type a specific scenario, for example: test the case where the input is an empty string. No generation request is sent before the scenario is submitted.

Review the generated Java artifact in the chat. Create Test File validates syntax/package and resolves the bound module. Choose a test root when several exist; confirm the JUnit warning if the dependency is absent. Existing files are refused. After creation, Open Test File reopens the result. Add required dependencies and run the generated test yourself; syntax checks do not prove that assertions or behavior are correct.

![Figure 5: Scenario-based test chat bound to HybridRetrieverTest.java — Create Test File and Copy Code actions after generation.](images/F6.png)


## 3.4 Draft a commit message and review code

Stage changes in Git, then use VCS > Generate Commit Message from Staged Changes. Review the proposed type, scope, subject and body. The plugin does not stage files or commit automatically. With large diffs, several summarization requests may run before the final draft. An empty staging area and Git/provider failures are reported separately.

Select code and invoke Review Code with Course Context. Read the severity, approximate location, problem, explanation and suggestion. Each reported source is checked against retrieved excerpts. An unknown reference is removed; when no course sources are retrieved, a general-knowledge notice is shown. This action offers advice and does not apply production-code edits.

![Figure 6: Generated Commit Message — editable Conventional Commit draft from 3 staged files.](images/F7.png)

![Figure 7: Course-Aware Code Review — severity, problem, explanation, suggestion and cited source.](images/F8.png)


## 3.5 Cancel and troubleshoot

Use Cancel in chat or the IDE task cancellation control. Cancellation interrupts the network worker and suppresses late result dialogs, but cannot undo a provider request already received or billed. For HTTP 401 check the relevant provider key; for an empty index build first; for changed embedding settings rebuild. A history error allows temporary chat without overwriting an unreadable saved file.

![Figure 8: In-flight request — indeterminate progress indicator with Cancel button; input disabled until completion or cancellation.](images/F9.png)


# 4. Architecture, RAG and LLM Integration

## 4.1 Architecture overview

The implementation separates the Swing view, IDE adapter, project orchestration and data/HTTP services. This keeps slow parsing/network work off the event dispatch thread and allows most logic and the actual conversation component to be tested offline.

| Layer / component | Responsibility |
| --- | --- |
| ChatView / ChatPanel | Render turns, history, sources and artifacts; adapt UI callbacks to cancellable project tasks. |
| TaProjectService | Load/activate the index, build incremental snapshots, validate embedding compatibility, retrieve context and call the LLM. |
| MaterialParser / Chunker | Extract text with page identity and form overlapping, provenance-aware retrieval units. |
| JsonVectorStore / IndexMeta | Persist document hashes, chunk metadata and row-aligned float vectors. |
| Retriever / KeywordIndex / HybridRetriever | Dense cosine top-K, BM25 exact-term matching and optional rank fusion. |
| PromptBuilder / CitationVerifier | Create contextual instructions; verify citation membership/ranges and supply fallback notices. |
| HttpLlmClient / HttpEmbeddingProvider | Provider-specific configuration through compatible JSON HTTP requests. |
| BackgroundWork / TaskAwaiter | Monitor cancellation/disposal and interrupt the worker. |
| ChatHistoryStore / TestFileService | Local transcript snapshots; IDE-aware, user-triggered test-file validation and creation. |


## 4.2 Index-building lane

Materials folder -> PDF pages -> page-aware chunks -> embedding batches -> normalized vectors -> candidate snapshot -> save -> activate retrievers. File hashes allow unchanged documents to reuse their chunks/vectors when provider, model and chunking version match. A failed embedding stage leaves the previous active snapshot available.

## 4.3 Answering lane

Question + optional selected code -> query embedding -> compatible-index check -> dense or hybrid search -> context budget -> grounded/general prompt -> chat completion -> at most one retry for empty or uncited output -> citation verification -> UI rendering/history save. An answer still empty after retry becomes an explicit request error.

## 4.4 IDE execution model

UI interactions and file dialogs run on the EDT; network, Git, indexing and history writes use background execution. Project service synchronization serializes retrieval/build operations. TestFileService uses read actions for PSI/module resolution and an undoable write command for file creation. Save callbacks capture detached history snapshots and execute in order.


## 4.5 Page-aware chunking and provenance

PDFBox extracts each PDF page separately. KnowledgeChunk stores id, docId, docName, pageFrom, pageTo, title, text, tokenCount and a text hash. Chunker uses a 400-word window with 60-word overlap. Short pages below 60 words accumulate with following content; remaining short content is still emitted. Per-word page numbers determine the actual page range of every window. Window sizes are word-based; tokenCount is an estimate rather than a provider tokenizer measurement.

## 4.6 Persistence and index compatibility

index.meta.json contains document SHA-256 hashes, page/chunk information, chunk records, embedding model/base URL, dimension and chunking version. vectors.bin stores corresponding rows as little-endian float32. Row order must match the chunk list. Loading checks vector file length. Candidate snapshots avoid corrupting the active retriever during a failed rebuild; temporary files are replaced individually. The two-file publication is not a transaction across both files, so a crash between replacements remains a persistence limitation.

## 4.7 Dense, keyword and hybrid retrieval

The embedding input combines document name, starting page, title and chunk text. Requests are batched at 16 inputs. Query and stored vectors are normalized; dense retrieval ranks by cosine similarity and filters using the configured threshold. Defaults are top-K = 5, threshold = 0.35 and a context budget of 4,000 estimated tokens. Retrieved chunks are added until the next chunk exceeds that budget.

BM25 complements dense retrieval for exact identifiers. Its parameters are k1 = 1.5 and b = 0.75. It uses term/document frequency and document-length normalization. Hybrid retrieval obtains up to ten hits per branch and fuses ranks using RRF(d) = sum(1 / (60 + rank(d))), then selects top-K. Rank fusion avoids adding incompatible cosine and BM25 score scales. Hybrid mode is off by default.

## 4.8 Citation verification and fallback

The model emits {{cite:docId#page}}. The final hash separates the page number, permitting hashes within document names. Verification accepts only documents retrieved for this request and pages inside their chunk ranges, then displays the document name and page/scope. Unknown, malformed, overflowing and unterminated tokens are removed. Continuous spaces are preserved so code indentation survives.

Empty retrieval enforces the explicit general-knowledge disclaimer. Nonempty retrieval without any surviving citation triggers one controlled regeneration; a second uncited response is marked Unverified. This verifies provenance membership, not semantic support. BM25 can retrieve irrelevant ordinary words, so semantic no-match detection is not guaranteed by the presence of hits.


## 4.9 HTTP integration and prompt engineering

JDK HttpClient sends JSON chat messages to /chat/completions and embedding inputs to /embeddings. Chat and embedding endpoints, models and secrets are separate. Chat requests are non-streaming, with a configurable timeout (default 60 seconds). Ordinary Q&A uses temperature 0.2 and a 1,200-token completion limit; test generation uses temperature 0.1 and up to 4,000 output tokens. Provider errors distinguish authentication, balance, rate limits and server failure where applicable.

The grounded system prompt identifies the course TA, requests the language of the student question, restricts course-specific claims to supplied excerpts and requires citation tokens. The user prompt carries the original question, optional code and numbered source excerpts. General mode prohibits invented references. Retry instructions request a nonempty response or valid citations without creating an unbounded retry loop.

The test-generation prompt binds the source package, selected target and full source snapshot to the natural-language scenario and asks for one complete JUnit 5 class without invented dependencies. GeneratedTestCode rejects ambiguous multiple/non-Java fenced artifacts. TestFileService checks syntax and the required package before allowing file creation. Source input is explicitly treated as data, not instructions; this reduces but does not eliminate prompt-injection risk.

## 4.10 Commit drafting and creative review

Git diff --cached retrieves staged changes; external diff/textconv helpers are disabled and the process has a 30-second deadline. A small diff goes directly to the model. Large diffs are split at file boundaries and into bounded pieces; every input piece enters a summary. Groups of three are reduced until the final context fits 6,000 characters. This avoids silent truncation, but semantic omissions can still occur during model summarization. Output is a Conventional Commit JSON draft.

Course-aware review combines selected code with retrieved excerpts. JSON findings carry location, severity, problem, explanation, suggestion and citation. The source field is resolved through the same document/page whitelist before display. The student remains responsible for reviewing advice and applying any change.

## 4.11 Efficiency and dependency choices

Dense scanning costs O(ND) for N chunks of D dimensions and needs approximately 4ND bytes for raw vectors, excluding metadata/runtime objects. At the recorded 111 chunks and 1,024 dimensions this is 454,656 bytes. Incremental hashing/reuse and batched embeddings reduce repeated provider calls. Local JSON/binary storage is easy to inspect, but is intended for a small course corpus rather than a large concurrent service.

PDFBox preserves page provenance; CommonMark parses Markdown; RSyntaxTextArea provides copyable code highlighting; Jackson handles JSON. LangChain4j and Apache Tika are assignment references rather than required dependencies. Direct HTTP and PDF parsing keep this implementation inspectable. Cancellation, retry and map/reduce add reliability at the cost of code complexity, latency and potentially additional provider charges.


# 5. Additional Technical Information

## 5.1 Verification and evidence

The retained local implementation passed a full Gradle test/chatUiTest/buildPlugin cycle with 71 JUnit tests and 33 overlapping UI checks. After PR #2 adaptation, source/tests were compiled with Java 21 compatibility and 73 JUnit tests passed with zero failures or skips on the target JBR. These are sequential snapshots, not additive totals. The PR #2 runtime package was assembled from current compiled production classes and the previously built plugin resources/libraries; a fresh full Gradle build for that final integration is not claimed here.

Coverage includes chunk text/page ranges, index lifecycle, vector persistence, ranking, citation rejection and indentation, diff partitioning, worker interruption, Markdown safety, history persistence/restoration, turn navigation, artifact extraction and scenario-first test conversations. The D-drive IDEA runtime was observed responding with CST3492 TA loaded. These checks do not establish real-provider quality or successful interactive PSI file creation in every module layout.

| Recorded retrieval experiment | Result |
| --- | --- |
| Corpus / development labels | 9 PDFs; 111 chunks; 9 positive questions; 1 unrelated question |
| BM25 Hit@5 / MRR@5 | 1.0000 / 0.9444 |
| Mean seed-page Recall@5 | 1.0000 |
| BM25 negative no-hit rate | 0.0000: unrelated cooking question still matched |
| Stored-vector self-retrieval | 111 / 111; persistence/cosine sanity check only |


The benchmark uses small manually seeded page labels, not an independent exhaustive relevance set. It excludes live query embeddings, HTTP/LLM latency and answer entailment. The negative failure is retained openly; neither the positive scores nor self-retrieval establish reliable out-of-domain behavior.

## 5.2 Storage, security and privacy

Nonsecret settings use the IDEA persistent state component; keys use PasswordSafe. Saved-key lookup and model calls stay off the EDT. Rendered raw HTML is escaped, unsafe URLs are sanitized and image nodes are recursively removed so model images do not cause hidden fetches. No generated tests are executed automatically.

History stores questions, submitted code, answers, source labels, drafts and test-artifact metadata as local plaintext JSON in the IDE system directory, partitioned by project. It excludes credentials, vectors and retrieved PDF text. Atomic file replacement and detached snapshots protect normal saves; unreadable files are preserved and reported. The configured D-drive runtime places system/config/plugins/log/temp data under D:/CST3492-TA-runtime. Course indexes use the runtime APPDATA/cst3492-ta/index folder.


## 5.3 Remaining limitations and acceptance

Before submission, demonstrate a real course question with manually checked source pages; an unrelated question and its disclosure; a selected-code question; scenario-based test generation followed by creation and execution; multiple staged files including a large late diff; and course-aware review with invalid-reference rejection. Check theme changes, source navigation, history reload and cancellation. A valid citation does not guarantee a correct interpretation, and a generated test may compile but assert the wrong behavior.

Known limits include text PDFs only, small-corpus linear search, no multi-turn model context, optional hybrid false positives and a two-file index publication window. Test-root registration in IDEA does not automatically edit Maven/Gradle dependencies. Cancellation cannot revoke a received provider request. Large diffs require extra calls. Future work should prioritize held-out multilingual/negative retrieval tests, live answer-quality/latency evaluation and stronger persistence over unmeasured feature claims.

## 5.4 Collaboration and reproducibility

The repository includes a team workflow: create short feature branches, agree on ownership, run checks, request review and merge reviewed changes. PR #2 supplied source-bound test chat and file-creation improvements; local integration retained citation/index fixes, cancellation and the existing history schema. This report does not assign individual authorship. Team members should fill in actual responsibilities and examples of review/conflict resolution for the presentation.

Responsibility allocation / review evidence: __________________________________________

____________________________________________________________________________

## 5.5 Submission checklist

Complete the blank team particulars and actual contribution information. Run a fresh final build and execute the manual acceptance flow. Keep the complete source code and documentation in different folders within one submission ZIP. Include this English project report PDF and separate English presentation slides; the first slide must list actual full names and matriculation numbers. Exclude local secrets, course PDFs where redistribution is not permitted, caches and runtime history. The installable plugin ZIP is distinct from the assignment submission ZIP.

## 5.6 Source materials and references

[1] AY202627S1-CST3492-Assignment-A.pdf, supplied assignment brief, especially Submission (page 3) and F0/F1 (pages 1-2).

[2] Local source tree: cst3492-ta-plugin/src/main; tests in src/test; verification/evaluation-results.json and evaluation/queries.tsv; PR2_INTEGRATION.md and recorded test logs. Repository: https://github.com/Fu-YH05/Java-Assignment-A

[3] Assignment-provided development references: https://plugins.jetbrains.com/docs/intellij/developing-plugins.html ; https://platform.deepseek.com ; https://github.com/langchain4j/langchain4j ; https://tika.apache.org ; https://aws.amazon.com/what-is/retrieval-augmented-generation . These are reference links, not a claim of newly verified online content.

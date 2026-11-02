# Verification and design supplement

Date: 2 November 2026. Scope: Group B (F0 + F1). This supplements the earlier design draft;
it is not a replacement for the required team particulars, UI screenshots or presentation slides.

## Implemented changes

### Citation boundary

Both chat answers and code-review findings resolve citations against the exact document IDs
and page ranges retrieved for that request. Document IDs may contain dots, spaces, parentheses,
Unicode and hash characters; the final hash separates the page number. Unknown documents,
out-of-range pages, malformed values and overflowing numbers are rejected. Code review displays
an explicit unverified-reference notice instead of the model's raw citation. Empty retrieval
adds the general-knowledge disclaimer. When excerpts were retrieved but the reply contains no
verifiable citation, the service performs one controlled retry with an explicit citation
instruction; if the retry also produces no verified citation, the answer carries an
"Unverified" notice. A citation check establishes source membership, not that
the source actually supports every sentence; semantic correctness still requires review.

### Test file creation

The preview offers Copy and Create Test File. File identity extraction masks comments and Java
literals; IntelliJ's own PSI Java parser checks syntax before any file is written. This avoids
requiring a system compiler in the IDE's runtime. It never executes or compiles generated tests.
Code fences are removed; the package and public class (or first package-private class) determine
the relative filename. The user reviews the proposed test source root; the default is an existing
test source root in the selected module, otherwise project/src/test/java. A write command creates
the package folders and a new file and opens it in the editor. Existing files are never overwritten.
Syntax validity does not establish that imports, dependencies, assertions or behavior are correct;
the user must run the generated test. Custom module layouts may require choosing a different root
or registering it as a test root in the IDE/build configuration.

### Large staged changes

Git diff runs in a background worker with a 30-second process deadline; cancellation destroys
the running process. Git errors are distinguished from an empty staging area. External diff
and textconv helpers are disabled. Small diffs go directly to the model. Larger diffs are split
at file boundaries and then into bounded pieces, preserving all input characters. Every piece
is summarized; summaries are reduced in groups of three until the final request fits 6,000
characters. The model is required to preserve filenames, changes and intent. Empty/overlong
summaries fail visibly rather than being silently truncated. Binary changes are represented
by Git's textual binary-change notice, not by decoding binary contents.

This trades additional requests and latency for coverage of every staged change. A model may
still omit details during summarization; users review the resulting commit message. This feature
never stages files or commits automatically.

### Task lifecycle and chat display

All three smart actions, indexing and chat use cancellable IDE background tasks backed by the
application executor. The task monitor polls for cancellation/project disposal and interrupts the
network worker. Indexing checks cancellation between documents and immediately before saving.
The previous active snapshot remains available if embedding fails before publication. Cancelled
tasks suppress late success/error dialogs. Chat serializes its own tasks, disables conflicting
controls, and exposes a Cancel button. Smart actions use the IDE task cancellation controls.
Service-level synchronization still serializes retrieval/build work for the same project; this
is not a concurrent read/write index implementation.

Chat identifies selected-code questions and displays the submitted code snapshot. Its inert HTML
renderer supports paragraphs/line breaks, inline code and bold. Raw HTML is escaped; links/scripts
are not executed. Fenced and indented code blocks are promoted to a syntax-highlighted, copyable
component, including blocks nested inside list items and blockquotes; the surrounding list or
quote text stays in the inert HTML renderer. This remains a deliberately small Markdown subset,
not a full Markdown engine.

## Design choices

The assignment lists LangChain4j and Apache Tika as references, not mandatory dependencies.
Native JDK HttpClient covers the required HTTP calls, and PDFBox preserves PDF page provenance.
Fewer dependencies keep packaging and error handling inspectable. No measured SDK conflict is
claimed. This release supports text PDFs, not OCR, DOCX or PPTX.

BM25 and cosine scores use different scales, so optional hybrid search uses Reciprocal Rank
Fusion rather than adding raw scores. The dense threshold of 0.35 remains an initial configuration
value, not an experimentally optimized value. The existing descending-score threshold loop is
correct and was not reordered. Vcs.MainMenu exists in the target SDK and was retained.

## Real-corpus retrieval experiment

Artifacts: `evaluation-results.json` and `src/test/resources/evaluation/queries.tsv`.
The JSON records document SHA-256 values, ranked chunk IDs/page ranges, scores and measured times;
it contains no credentials or PDF text. The locally supplied course corpus contains nine PDFs,
111 indexed chunks and 1,024-dimensional BAAI/bge-m3 vectors. Materials and index files remain local.

Nine English questions were manually paired with seed relevant PDF page sets after reading the
slides. PDF page numbers, not printed slide numbering, are used. The tenth question is an
out-of-course cooking question. These are development seed labels, not an independent exhaustive
judgment set; other unlabelled pages can also be relevant. Chunk page ranges covering a labelled
page count as retrieving that page, so merged chunks make this measure less strict than verifying
that every expected statement is present. No test parameters were tuned on these questions.

| Measurement | Observed result |
|---|---:|
| Positive questions | 9 |
| BM25 Hit@5 | 1.0000 |
| BM25 MRR@5 | 0.9444 |
| Mean seed-page Recall@5 | 1.0000 |
| Negative questions | 1 |
| BM25 negative no-hit rate | 0.0000 (failed) |
| Dense stored-vector self-retrieval sanity check | 111 / 111 |

Hit@5 is the fraction of positive questions with at least one matching labelled page in the top
five chunks. MRR@5 averages the reciprocal rank of the first matching chunk, with zero for a miss.
Seed-page Recall@5 averages retrieved labelled pages divided by labelled pages for each positive
question. Negatives are excluded from those means and evaluated separately. Search times are
medians of five in-memory runs after one warm-up per query, excluding parsing, index loading,
HTTP and LLM latency. They are descriptive local timings, not production latency guarantees.

Q7's first labelled hit was rank 2; the other eight positive questions hit at rank 1. Importantly,
the negative cooking question still matched ordinary words in the course material: the current
BM25 branch has no stop-word or semantic rejection policy. Therefore an empty-retrieval disclaimer
cannot be guaranteed for arbitrary unrelated questions, especially with hybrid retrieval enabled.
This remains an explicit evaluation limitation, not evidence of successful out-of-domain handling.
The next evaluation should add English/Chinese paraphrases, harder negatives, multiple raters,
and a held-out set before adjusting thresholds or keyword relevance policies.

The dense sanity check queries the index with each stored vector and verifies a near-identical
top match. It checks vector persistence and cosine search integrity only; it is NOT evaluation of
natural-language query embeddings. No live query-embedding, RRF quality, citation entailment,
LLM answer quality or provider latency result is claimed by this offline benchmark.

### Reproduce

With JDK 21, run from the plugin folder:

```powershell
.\gradlew.bat test buildPlugin
.\gradlew.bat evaluateRetrieval "-PevaluationIndex=$env:APPDATA\cst3492-ta\index"
```

On another OS pass the absolute path to the local index. Build that index from the same PDFs
using the plugin first; compare source hashes before comparing results. Output is generated in
`build/reports/evaluation/results.json`. The evaluation does not require an API key and does not
call either provider. Rerunning on a different corpus requires reviewing the gold labels.

## Automated verification

On 3 November 2026 the current working tree passed Gradle `test chatUiTest buildPlugin`:
71 JUnit tests and 33 offline chat UI checks, with zero failures/errors/skips. The JUnit suite runs
under the IntelliJ test bootstrap; `chatUiTest` executes four of the same classes (ChatViewTest,
ChatHistoryViewTest, MarkdownContentTest and CitationVerifierTest) directly without it. The 33
checks repeat part of the 71 JUnit cases; the counts must not be added as distinct tests.
The JUnit suite covers project-isolated history storage, snapshot independence, invalid files,
interrupted-request recovery, conversation drafts/sources, same-turn retries and scroll navigation,
citation identity/ranges and preserved indentation, the one-shot
retry decision including an empty reply, an empty reply reported as a request error, and explicit
unverified marking, file identity extraction, full diff partitioning and reduction, cancellation
interrupting the worker, safe rendering (including nested image filtering and nested code
promotion), and the earlier indexing/retrieval regressions. Neither suite performs the interactive
PSI/file writing workflow or contacts providers. The final plugin ZIP was checked for the 1.1.0
descriptor and new implementation classes.

## Manual acceptance checklist

The following interactive checks remain required after installing this build; automated tests
do not substitute for them:

1. Ask about Exceptions and verify citations for a dotted filename; attempt a fabricated review citation.
2. Generate a test from a selected method, check the full class context, save under the correct module
   and package, verify an existing filename is refused, then compile/run the generated test.
3. Stage changes in multiple files including a late file beyond 8,000 characters; verify their intent
   appears in the draft. Cancel during Git reading, a model call and summary reduction.
4. Cancel an index rebuild and verify the old index remains usable. Close the project during a request
   and confirm that no late result dialog appears.
5. Ask about selected code and inspect the context marker, paragraphs, fenced code and Sources section.
6. Verify general-knowledge disclosure on genuinely empty retrieval; separately record false positives.

The network worker can be interrupted while HttpClient is waiting, but cancellation cannot undo
a request already received/billed by a provider. A small file-save publication window may finish
before cancellation is observed. Complete live API/UI acceptance and add screenshots to the final
English PDF report; do not claim those manual checks are already passed.

# Assignment A: functional correspondence

Review date: 3 November 2026. Target: Group B, F0 + F1. Source: the original four-page
AY202627S1-CST3492-Assignment-A.pdf supplied locally. This is a source/automated-check review,
not a claim that live provider and interactive IDE acceptance have been completed.

| Assignment requirement | Implementation evidence | Remaining acceptance |
|---|---|---|
| F0 course-material preprocessing, chunks and index | MaterialParser extracts PDF pages; Chunker records overlapping windows and page ranges; JsonVectorStore persists metadata/vectors; TaProjectService handles rebuilds and reuse | Use the actual lecture PDFs; text PDFs only, no OCR |
| F0 relevant retrieval and contextual LLM answer | Query embeddings, cosine search, optional BM25/RRF, bounded context and PromptBuilder | Verify live query embeddings and answers; hybrid keyword false positives remain a known limitation |
| F0 document and page/scope citations | CitationVerifier whitelists this request's documents/page ranges; readable source labels; one retry when citations are absent | Verify actual cited pages support the answer; a valid token alone does not prove support |
| F0 explicit general-knowledge disclosure when no relevant material is found | Empty retrieval forces GENERAL_DISCLAIMER; prompts instruct disclosure for insufficient excerpts | Unrelated keywords can still produce hits, especially in hybrid mode; semantic rejection is not guaranteed |
| F0 friendly embedded IDE interface | Registered Tool Window; Markdown, copy, sources, theme styling, error/cancel states; new turn navigator and project history | Check actual IDEA layout/theme and real callbacks |
| F0 editor right-click action for selected code | AskSelectedCodeAction captures selected code and user question and opens the TA window | Demonstrate selection and answer in IDEA |
| F1 scenario-first JUnit generation for selected class/method | Required scenario dialog, full-file/selection context, JUnit 5 prompt, preview and independent file creation with PSI syntax checks | Create and run a generated test in a real module; syntax checks do not validate assertions/dependencies |
| F1 commit messages for all staged files | git diff --cached; bounded complete diff partitioning and reduction; Conventional Commit preview | Stage several files including a large late diff; check represented intent and Git-root behavior |
| F1 creative smart action | ReviewCodeAction performs course-aware review with source whitelisting | Demonstrate meaningful findings and verified sources |

Every required Group B feature has an implementation. Full compliance cannot be inferred from
unit tests alone: relevance/disclosure quality and live IDE/provider behavior remain to be checked.
F2 side-by-side code-change approval is for Group C and is not a missing Group B feature.
LangChain4j and Apache Tika are references, not mandatory dependencies. Multi-turn model memory,
streaming, OCR and opening a source PDF directly are not stated minimum requirements.

## Additional requested features

The Turns sidebar jumps to a numbered question. Local project-specific history supports new,
open and delete operations, saved code/citations/drafts, interrupted-request recovery, and atomic
background writes. Restoring history never invokes an LLM. History does not add earlier turns to
the current LLM prompt. No credentials or PDF excerpts are included in history JSON.

## Submission and presentation evidence

The required English report PDF must contain, in order: full team particulars (including names,
matriculation numbers and contacts), implemented features, a user manual with UI samples,
architecture/workflow/RAG storage/algorithms/LLM prompts/design rationales/efficiency, and optional
additional technical details. A verification supplement alone does not meet this report requirement.
English slides must list actual member names and matriculation numbers on the first slide.
Put complete source and documents in different folders within one submission ZIP. The installable
plugin ZIP is not that submission ZIP. Demonstrate all features and discuss team collaboration,
technical decisions, implementation problems and solutions within the 20-minute presentation.
Generated PDF/slides need synchronization with this history feature and the current 71/33 checks;
placeholders or old test counts must be replaced before submission.

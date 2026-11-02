# Java Assignment A — CST3492 TA

Group B project: an IntelliJ IDEA teaching assistant plugin for CST3492 Java Enterprise
Application Development. The implementation covers F0 and F1: course PDF retrieval and cited
answers, questions about selected code, scenario-based JUnit generation, staged Git commit
message drafts, and course-aware code review.

## Repository layout

- `cst3492-ta-plugin/src/main`: plugin implementation and action registrations.
- `cst3492-ta-plugin/src/test`: automated tests, including index lifecycle regression tests.
- `cst3492-ta-plugin/README.md`: plugin setup and manual acceptance steps.
- `CONTRIBUTING.md`: team development workflow.

The GradeBook lecture examples, course slides, local API credentials, IDE state and generated
artifacts are not part of this source repository.

## Build and run

Install JDK 21 and set `JAVA_HOME` to that JDK. From `cst3492-ta-plugin`:

```powershell
.\gradlew.bat test buildPlugin
.\gradlew.bat runIde
```

On macOS/Linux use `./gradlew test buildPlugin` and `./gradlew runIde` instead.
The first build downloads Gradle and project dependencies. `runIde` opens IntelliJ IDEA
Community 2024.2.6 with the plugin loaded. Open a Java project inside that window.
The installable ZIP is generated in `cst3492-ta-plugin/build/distributions/`.

## Local configuration

In the test IDE, open Settings > Tools > CST3492 TA:

1. Chat: `https://api.deepseek.com`, model `deepseek-chat`, and your own chat API key.
2. Embeddings: `https://api.siliconflow.cn/v1`, model `BAAI/bge-m3`, and your own embedding API key.
3. Select your local folder of text-based course PDFs, apply settings, and click **Build Index**.

Each teammate supplies their own credentials and PDF path. Keys are stored in IntelliJ
PasswordSafe; blank key inputs preserve saved keys. Never commit keys, password stores or
generated index files. Endpoint/model changes require rebuilding the index.

## Verification

The regression suite covers indexing, citations, test-source parsing, complete diff partitioning,
cancellation and inert chat rendering. Tests use generated PDFs and deterministic embeddings
and need no live credentials. Real provider availability and answer quality require manual checks.
See [the verification supplement](cst3492-ta-plugin/verification/REPORT.md) for the real-corpus
BM25 benchmark, reproducible inputs and its explicitly recorded limitations.

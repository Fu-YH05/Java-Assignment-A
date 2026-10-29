# Team development

## Start work

Clone this repository, install JDK 21, then work on a branch based on the latest `main`:

```bash
git switch main
git pull --ff-only
git switch -c feature/your-change
```

Use short branches such as `feature/selected-code-chat` or `fix/index-refresh` and agree on
file ownership before editing shared service, settings and UI classes.

## Before opening a pull request

1. Run `gradlew.bat test buildPlugin` (or `./gradlew test buildPlugin`) in `cst3492-ta-plugin`.
2. For UI changes, run `runIde` and verify the relevant action in the test IDE.
3. Check `git diff --cached` for unexpected files or credentials.
4. Commit with a descriptive message, push your branch, and open a pull request to `main`.

Describe the problem, resulting behavior, and checks performed. Ask a teammate to review the
change before merging. Branch protection and collaborator access are managed separately in
the GitHub repository settings.

## Source conventions

- Keep UI work on the event dispatch thread; run model calls and PDF processing in background tasks.
- Preserve document/page provenance when changing chunking or retrieval.
- Add regression tests for fixes affecting indexing, retrieval, or citation behavior.
- Do not weaken tests to make a build pass.
- Keep local paths, API keys, course PDFs, caches and build products out of commits.
- Classroom GradeBook examples are references, not the plugin implementation.

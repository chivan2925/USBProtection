# APPLY — USBShield Reliability Phase 2

**Exact base:** `vinhkhang` at `d0c07f1a14f5a9d08af20835e8ddee198f6808c3`.

1. On Windows PowerShell at repo root, run `git switch vinhkhang`, `git status --short`, `git rev-parse HEAD`. The worktree should be clean and HEAD equal to the exact base above.
2. Extract this ZIP at **repo root** with directory structure preserved, and overwrite existing files. Do not replace the repository wholesale. Files are limited to code, unit tests, config and one new operational document.
3. Run `mvn -f admin-server/pom.xml clean test` and `mvn -f client-agent/pom.xml clean test`, then `git diff --check` and inspect `git status --short`.
4. If any failure occurs, do **not** push: provide the complete Maven log and current source ZIP so it can be fixed against the applied state.
5. If all Windows tests PASS, commit/push to **vinhkhang** only. No PR or merge to `main` until the owner authorizes it.

**Expected additional test classes:** `OutboxReliabilityRegressionTest`, `UsbGuardWatchReconnectReliabilityTest`. Existing `OutboxSpoolServiceTest` expectations change only for quarantine retention.

**Note:** This package was checked with Java 21 isolated class-level compilation and stubbed-dependency smoke tests. Maven is unavailable in the package-creation environment; full Maven/JUnit is NOT EXECUTED here.

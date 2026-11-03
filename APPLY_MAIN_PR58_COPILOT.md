# USBShield — PR #58 Copilot follow-up patch

## Baseline and scope

- Repository: `chivan2925/USBProtection`.
- **Exact upstream base:** `main` at `8d263257439620601fc3889a44db5d0862b6ee9e` (merged PR #58).
- Apply to a clean working branch from that commit. Do not reapply any older Phase 1/2/3 ZIP or the earlier PR #57 package.
- Patch contains only modified/new Java source, regression tests and documentation. No changes to Frontend, enrollment, USBGuard policy, or remote GitHub branches.

## Findings addressed

1. **Java-blank legacy USB hashes (MEDIUM; PR #58 discussion r4451147083).**
   The pre-Hibernate H2 preflight reads existing hashes using JDBC, applies exactly `String.isBlank()`, changes only matching rows to SQL `NULL`, checks duplicates of the **remaining** non-NULL hashes, then commits in one transaction. Duplicate nonblank hashes abort startup and roll back cleanup; existing USB and event/whitelist rows are never deleted or merged.
2. **H2 verification guide consistency (additional moderate review comment).**
   `PATCH_APPLY_WINDOWS.md` no longer uses `TRIM()` as an authoritative check. It documents the Java `isBlank` predicate and a supplementary H2 `REGEXP_LIKE(... '\p{javaWhitespace}' ...)` check, with a JDBC fallback where the regex is unsupported.
3. **Quarantine directory sync failure (additional moderate review comment).**
   The Agent now syncs the parent after creating the quarantine directory, then both parent and destination directories after an evidence move. If sync fails *after* the move, it tries to move the evidence back into the active FIFO and sync both directories again. Either way, this drain stops; if the rollback cannot be verified as durable, later drains remain blocked until the directories can be re-synced. On a fresh process, startup re-syncs existing outbox/quarantine directories on Linux before accepting writes.

These are narrow remedies, **not** a guarantee against corruption from kernel, filesystem or power-loss failures when fsync itself is failing. If a rollback cannot be durably confirmed, preserve disk evidence, stop the Agent and investigate the filesystem before restarting.

## Manual application on Windows

1. Verify `git rev-parse HEAD` is exactly the base SHA above and `git status --short` is empty. Create a new local fix branch.
2. Stop running Admin Server and Client Agent. Back up the H2 database and the Agent's spool/quarantine directories **before** applying files. Do not include DB files, credentials or audit logs in Git.
3. Extract the patch ZIP at the repository root, preserving relative paths and overwriting included files only.
4. Run the following commands at the repository root:

```powershell
mvn -f admin-server/pom.xml clean test
mvn -f client-agent/pom.xml clean test
cd frontend
npm test
npm run lint
npm run build
cd ..
git diff --check
git status --short
```

5. Specifically inspect `UsbCatalogUpgradeTest`, `UsbCatalogUpgradeStartupIntegrationTest`, `OutboxReliabilityRegressionTest`. Verify startup and backup/restore on the **actual legacy H2 database** in a safe copy; synthetic in-memory H2 tests alone cannot prove the production upgrade.
6. If Hibernate refuses to start because of duplicate nonblank hashes, leave the DB unchanged and perform a deliberate FK-aware reconciliation; do not delete/migrate rows automatically.
7. Only after all required checks pass should the owner commit/push the fix branch and continue PR review.

## Test evidence and limits when package was built

- The production Java classes compiled with Java 21 using minimal local stand-ins for framework dependencies; this is **not** a Maven/JUnit result.
- A standalone Java 21 harness with dependency stand-ins passed the one-shot and persistent quarantine fsync failure/recovery scenarios.
- Java whitespace predicate and Java regular-expression property agreed on the specified test characters.
- **Full Maven/JUnit, frontend tests, and Ubuntu hardware E2E were NOT executed here.**
- S01 HTTPS and S03 native Ubuntu E2E remain outside this patch.

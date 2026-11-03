# USBProtection — Copilot PR #57 remediation (manual apply)

Baseline: `main` HEAD **f919ebcff44ac8d09cb09b92c2a1177b04066c62**
Merged change: `fix(all): improve correctness, reliability, and scalability`.
The ZIP contains **only new and modified files**, NOT the full repo.

## Before applying

- Start from a clean Git working tree at this exact baseline; create a new fix
  branch from `main` or otherwise explicitly review conflicts.
- Stop running Admin Server and Agent instances, back up H2 database and Agent
  spool, and ensure private credentials/events remain outside Git.
- Extract this ZIP onto repository root without deleting unrelated files.

## What changed

- HIGH (H2 migration): `UsbCatalogUpgrade` executes before Hibernate JPA
  schema update and converts legacy blank/whitespace-only hashes to NULL;
  nonblank duplicates stop startup without losing IDs/history.
- HIGH (outbox): synchronous ordinary `sendPayload()` now throws if the server
  did not accept the audit and `spool()` rejected it. Existing callers must not
  interpret that exception as a successful durable delivery.
- LOW (guide): the top-level guide describes all merged phases instead of
  Phase 1 alone.
- Targeted moderate findings: delayed DISCONNECTED retries cannot mutate a
  newer lifecycle's dedup key; Linux outbox directory entries get fsynced.

## Required validation

```powershell
mvn -f admin-server/pom.xml clean test
mvn -f client-agent/pom.xml clean test
cd frontend
npm ci
npm test
npm run lint
npm run build
cd ..
git diff --check
git status --short
```

For existing H2 databases, verify no nonblank duplicate hash is present before
first startup (see `PATCH_APPLY_WINDOWS.md`). Do not delete duplicate USB rows;
any repair must account for historical events and per-endpoint whitelist FKs.
If a test/build fails, do not merge: retain the exact output and repair first.

## Verification done while preparing the ZIP

- Exact baseline source hashes checked against GitHub `main` for touched
  production files before edits.
- JDK 21 standalone compilation: `UsbCatalogUpgrade` and
  `UsbGuardEventListener` PASS with framework stubs.
- Linux directory fsync smoke check PASS.
- Full Maven/JUnit, frontend test/build, and Ubuntu E2E NOT EXECUTED in the
  package preparation environment. The package is a review candidate.

## Remaining review suggestions

Not included here: outbox O(backlog) scans, global catalog writer bottleneck,
frontend redundant/duplicate reads, and strengthened browser production-path
coverage. These are separate performance/test improvements, not part of the
three open blockers fixed by this package. HTTPS (S01) and native Ubuntu E2E
(S03) remain intentionally deferred.

## PR #58 Copilot follow-up (main `8d263257439620601fc3889a44db5d0862b6ee9e`)

The original preflight/verification `TRIM(device_hash)` check was not equivalent
to Java `String.isBlank()`. The follow-up migration reads each legacy hash
through JDBC, normalizes Java-blank values before adding uniqueness, then
checks nonblank duplicates in the same transaction. It also handles a failed
quarantine directory fsync by trying to restore the original source file and
pausing the drain; uncertain durability blocks subsequent drain until re-sync.
See `PATCH_APPLY_WINDOWS.md` for the corrected verification procedure.

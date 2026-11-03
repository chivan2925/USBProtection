# USBShield PR #57 — unified Windows validation guide

**This is the current, consolidated guide for the changes merged into `main` by PR #57.**
The previous text in this file described *only the historical Phase 1 ZIP* and
its former `vinhkhang` baseline; it must **not** be used to scope, reapply, or
validate the complete merged PR. Do not reapply any old Phase 1/2/3 ZIP to `main`.

## Merged scope

| Phase | Findings | Components |
|---|---|---|
| 1: Correctness | R01–R05, S02 | Admin Server, Client Agent, tests |
| 2: Reliability | R06–R08, R12–R13 | Client Agent, tests |
| 3: Scalability | R09–R11 | Admin Server, Frontend, tests |

Companion historical guides: `APPLY_WINDOWS_PHASE2.md` and
`APPLY_WINDOWS_PHASE3.md`. Their old baseline/branch instructions apply to
those original ZIPs only, not to a current PR #57 remediation.

## Post-merge Copilot corrections (November 3, 2026)

Base: `main` commit `f919ebcff44ac8d09cb09b92c2a1177b04066c62`
(Merge PR #57). The new corrective package addresses the three open review
issues and two targeted reliability comments:

- Pre-Hibernate H2 preflight normalizes legacy empty/whitespace-only
  `usb_devices.device_hash` to `NULL`. Nonblank duplicate hashes are NOT
  merged/deleted; startup fails with a remediation message to preserve FKs.
- Client Agent propagates `spool(...) == false` as a durability failure in its
  ordinary USB event path, while USBGuard policy enforcement stays independent.
- The former Phase 1-only guide is replaced by this consolidated scope guide.
- A delayed DISCONNECTED audit retry does not alter deduplication keys for a
  newer USB insertion/removal lifecycle.
- The Linux outbox fsyncs its spool directory after publishing a file and
  after quarantine moves. Windows has different filesystem durability semantics.

**Do not operate on the H2 file without a backup.** The preflight changes old
rows containing only blank hashes; it never removes USB rows or audit history.
When the server detects duplicate *nonblank* hashes, stop and investigate USB
catalog/event/whitelist references instead of deleting rows blindly.

### Verification SQL (existing H2 file)

**Only after a successful startup migration**, H2 can check the remaining
non-NULL hashes. The migration uses Java `String.isBlank()` as its authoritative
predicate (tabs, newlines and Java-defined Unicode whitespace included);
ordinary SQL `TRIM()` is **not equivalent** and must not be used as a pass gate.

```sql
-- H2 uses Java regex; javaWhitespace mirrors Character.isWhitespace,
-- the predicate underlying String.isBlank(). Expected: no rows after upgrade.
SELECT id, device_hash
FROM usb_devices
WHERE device_hash IS NOT NULL
  AND REGEXP_LIKE(device_hash, '^\p{javaWhitespace}*$');

-- All remaining non-NULL hashes should be distinct after a successful start.
SELECT device_hash, COUNT(*) AS duplicate_count
FROM usb_devices
WHERE device_hash IS NOT NULL
GROUP BY device_hash
HAVING COUNT(*) > 1;
```

The migration unit/integration tests (not this query alone) establish the
`String.isBlank()` contract; the first query is a supplementary H2 check.
If an H2 version does not support `javaWhitespace` in `REGEXP_LIKE`, instead
read all remaining `device_hash` values through JDBC and check each value
with `hash.isBlank()`; **do not replace the check with `TRIM()`**.

A fresh DB without `usb_devices` does not require preflight data changes.
If the duplicate query has results, application startup must refuse to
continue. Back up and investigate references before any intentional repair.

### Manual application and tests

1. On a clean working tree, verify the current `main` commit above. Create an
   ordinary working branch from that commit; **do not commit straight to `main`**.
2. Stop Admin Server / Client Agent and **back up** the configured H2 database
   and Agent spool/credential directory. Never stage secrets, H2 files, or spool.
3. Extract the **current Copilot remediation ZIP** into the repository root,
   preserving paths and overwriting only included files.
4. From root, run:

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

`UsbCatalogUpgradeStartupIntegrationTest` verifies the ordering of the
preflight before Hibernate `ddl-auto:update` on an existing H2 file.
`UsbCatalogUpgradeTest` covers blank hashes, duplicate nonblank rejection,
restart idempotence and a fresh database. Client Agent tests cover spool
rejection and a delayed removal retry. Full Maven and Ubuntu hardware tests
must still be executed in the owner's environment.

### Explicitly separate follow-up concerns

Copilot also made nonblocking review comments about O(backlog) spool quota
scanning, a global catalog coordinator lock, frontend duplicate fetches, and
production-path browser coverage. These require additional design/performance
review and are **not claimed resolved by this targeted corrective package**.
S01 (HTTPS) and S03 (Ubuntu hardware E2E) remain deferred by owner decision.

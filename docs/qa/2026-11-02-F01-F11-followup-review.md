# F01–F11 follow-up: source review and targeted test repair (2026-11-02)

## Input and scope

Input: owner-supplied `USBProtection(20261102-133409).zip` and the accompanying Maven log (`Pasted markdown(20261102-133333).md`). This package is a **delta** against that exact input, not a complete source tree or a claim of native Ubuntu acceptance.

The Maven log shows:

- `client-agent`: 202 tests, **2 failed** (`UsbGuardWeek4WatchStressTest.replayedInsertImmediatelyAfterReconnectIsDeduplicated`, `replayedWatchOutputAcrossTwoProcessRestartsIsSuppressed`). Both were caused by replayed/orphan `PolicyApplied` generating an additional queued event after the watch state had been reset.
- `admin-server`: 32 tests, **1 error** (`Week2ServerIntegrationTest.concurrentWhitelistChangesDoNotLoseDesiredVersion`). Test fixtures attempted to allow two catalog-only devices with no prior event on the endpoint, violating F10's intentional per-endpoint observation check.

### Changes in this delta

1. `UsbGuardEventListener`: ignore orphan `PolicyApplied` unless its runtime ID and fingerprint match a previously observed device. Preserve already observed, confirmed device state and popup deduplication across `usbguard watch` reconnect; discard unfinished pending insertion and partial watch sections on reset; clean all state on shutdown. Do not synthesize events from unknown device IDs, or duplicate a `CONNECTED` when a previously observed device replays `Insert` with a different provisional target.
2. `UbuntuFinalDecisionIntegrationRegressionTest`: add tests for orphan PolicyApplied, partial replay, an Allow transition after watch reconnect while a device remains inserted, and replayed Insert with a changed provisional target.
3. `Week2ServerIntegrationTest`: seed valid, endpoint-specific `UsbEvent` records for both devices before running concurrent Allow calls. This keeps F10 security validation enabled and preserves the concurrency test's actual purpose.

## Review of F01–F11

| ID | Source-level status after this patch | Residual acceptance condition |
| --- | --- | --- |
| F01 | **IMPLEMENTED / NATIVE PENDING** — first/periodic reconciliation when versions match; foreign Allow triggers failure and prevents new verification/ACK | A foreign Allow is *detected but not automatically removed*. This may remain an actual bypass until an authorized owner resolves it. Existing server ACK is not a real-time protection-health report. Test on disposable Ubuntu VM. |
| F02 | **IMPLEMENTED / TEST PENDING** — `CONNECTED` emitted once for insertion, `POLICY_APPLIED` separately records changed effective decisions | Verify backend event history and dashboard totals with real Insert BLOCK → ALLOW event sequence. |
| F03 | **IMPLEMENTED / TEST PENDING** — subsequent policy transitions on an observed, still-attached device are captured; observation retained across watch reconnect | A device already attached *before Agent starts* may have no insertion snapshot; current listener conservatively ignores uncorrelated PolicyApplied. Native restart/attached-device scenario needs acceptance decision/test. |
| F04 | **IMPLEMENTED BEST-EFFORT / NATIVE PENDING** — stale pending Insert rechecks daemon by runtime ID and fingerprint; no guessed BLOCK popup | No guarantee of a popup if watch and daemon inspection both remain unavailable. Missing-event recovery needs Ubuntu fault-injection. |
| F05 | **IMPLEMENTED / TEST PENDING** — endpoint credential writes through private temp file, synced and replaced atomically when supported | Check ownership/modes and interrupted-update recovery on Ubuntu. |
| F06 | **IMPLEMENTED / TEST PENDING** — stable event ID, endpoint-scoped unique key, duplicate retry response, conflicting decision rejected | Verify server schema migration and concurrent duplicate POST behavior; legacy events without eventId remain accepted. |
| F07 | **IMPLEMENTED / TEST PENDING** — watch queues durably before async HTTP drain; independent spool/drain locks | Verify backpressure, disk-full and server-outage behavior, and that network latency cannot block watch IPC. |
| F08 | **IMPLEMENTED / NATIVE PENDING** — source IPv4 selected by OS route to Admin URL, fallback avoids loopback | Verify actual VMnet8 IP on Ubuntu A/B and multi-interface hosts. |
| F09 | **IMPLEMENTED / TEST PENDING** — original user snapshot kept through final decision and subsequent attached-device policy updates | User attribution is unavailable for devices inserted before Agent began observing; do not fabricate it. |
| F10 | **IMPLEMENTED / TEST PENDING** — Allow requires event proving this endpoint observed that USB ID; concurrency test now uses valid fixture | Confirm intended policy disallows preauthorization of a device from another endpoint. |
| F11 | **COMPLETE FOR THIS PACKAGE** — delta ZIP includes only modified/new files, paths start from repository root | Check archive contents before copying into the supplied baseline. |

These are **source-code assessments**, not unconditional `READY_FOR_COMMIT` or Ubuntu E2E PASS. Even if all Windows tests pass, F01/F03/F04 require native decisions/verification before hardware acceptance.

## Checks actually run by assistant

- JDK 21 compiler, isolated stubs for production `UsbGuardEventListener` and exact related watch parsers: **PASS**.
- Four-path event smoke (`BLOCK → ALLOW`, attached revoke, dedup, verified missing `PolicyApplied` recovery): **PASS**.
- Three additional replay smokes (`complete watch replay`, `partial watch replay`, and `changed provisional target` across reconnect): **PASS**.
- Package delta comparison against uploaded ZIP: must contain only files modified/added by this follow-up.
- **Full Maven/JUnit not run here** (Maven/dependency cache unavailable). Full frontend suite was not rerun because frontend was unchanged by this delta.

## Owner verification (Windows, repository root)

```powershell
mvn -B -f client-agent/pom.xml clean test
mvn -B -f admin-server/pom.xml clean test
cd frontend
npm test
npm run lint
npm run build
```

Do not treat an `Exception` stack trace printed by a *negative scenario test* as a failure if the corresponding Surefire test is reported PASS; rely on Surefire totals, process exit code, and test assertions. Conversely, a `BUILD SUCCESS` alone does not establish native USBGuard, H2-upgrade, or GNOME popup correctness.

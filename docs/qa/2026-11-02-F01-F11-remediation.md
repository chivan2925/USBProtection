# Ubuntu E2E — F01–F11 remediation candidate

Basis: `USBProtection(20261102-130021).zip` supplied by owner (02 Nov 2026). This package is a *delta* against that exact ZIP, **not** a replacement for the entire repository and **not** a confirmed hardware E2E PASS.

## Behavior changes and regression coverage

| Finding | Code / contract | Validation |
| --- | --- | --- |
| F01 HIGH | Heartbeat reconciles on first successful connection and periodically even when versions match; policy sync rejects regressing/malformed versions. Synchronizer rejects any foreign Allow and retains foreign rules unchanged for owner review. A pre-existing foreign Allow remains dangerous until explicitly resolved. | `HeartbeatCoordinatorTest`, USBGuard synchronizer tests, actual `usbguard list-rules` still required |
| F02 MEDIUM | One insertion emits `CONNECTED`. A later effective decision change emits separate `POLICY_APPLIED`, never a second `CONNECTED`. Frontend maps and filters it. Counts use latest observed device decision, while historical events remain immutable. | `UbuntuAllowTransitionRegressionTest`, `Week2ServerIntegrationTest`, `m4_admin_api.test.mjs` |
| F03 MEDIUM | PolicyApplied following an already finalized insertion (Allow/Revoke while attached) produces a policy event without inventing an insertion. | `UbuntuFinalDecisionIntegrationRegressionTest` |
| F04 MEDIUM | When `PolicyApplied` is missing, a bounded periodic diagnostic reads the *live* USBGuard device state (with matching runtime ID and fingerprint); normal event flow has no settle timer. No popup is claimed when inspection is unavailable. | `UbuntuFinalDecisionIntegrationRegressionTest`; native USBGuard test needed |
| F05 MEDIUM | `endpoint.json` writes to a restricted temp file, fsync, atomic replacement where supported, directory fsync where supported. Does not delete existing registration on failure. | `AtomicCredentialPersistenceRegressionTest` |
| F06 MEDIUM | Stable `eventId` on each event and legacy spool migration; `(endpointId,eventId)` unique server key with duplicate response reuse. Mismatched reused ID rejected. | `Week2ServerIntegrationTest` (legacy null `eventId` remains optional) |
| F07 MEDIUM | USBGuard watch writes to durable spool and wakes a bounded background HTTP-drain worker; network waits are not performed inside the watch reader. Scheduled heartbeat also drains the spool. | `DurableAsyncDispatchRegressionTest`, `OutboxConcurrentDrainRegressionTest` |
| F08 LOW | Agent chooses source IPv4 selected by OS routing to Admin URL, falling back to a non-loopback IPv4. | `SystemInfoProviderRoutingTest` |
| F09 LOW | Insertion-time GUI user snapshot is retained for subsequent effective-policy changes from the same device runtime ID. | `UbuntuFinalDecisionIntegrationRegressionTest` |
| F10 LOW | Allow now requires server evidence that this endpoint previously observed the device (`usbDeviceId`). This intentionally disallows pre-authorization from the global catalog. | `Week2ServerIntegrationTest`, `UbuntuE2EMetadataRegressionTest`, updated frontend test fixture |
| F11 INFO | ZIP contains only paths newly created or modified relative to supplied archive, retaining repository-relative paths. No generated artifacts, dependencies, or test reports. | ZIP content manifest review |

## Important integration conditions

* **Database migration:** Hibernate `ddl-auto=update` should add nullable `usb_events.event_id` and the composite unique constraint on `(endpoint_id,event_id)`. Take a backup of the Windows H2 lab database before first launch. Do not copy/overwrite production H2 DB.
* **Event contract:** `POST /api/agent/events` now accepts optional UUID-format `eventId` and `eventType=POLICY_APPLIED`; legacy null IDs are still accepted. Policy-applied is a decision transition, not a physical insertion. Compare actual backend/frontend builds together: mixed old UI + new server is not a supported test matrix.
* **Policy safety:** An unmanaged Allow cannot be quietly removed or declared safe. After a detection alert, examine the foreign rule under an authorized owner procedure. USBGuard's existing enforcement is not made fail-closed merely by refusing reconciliation.
* **Notification recovery:** Recovery uses live USBGuard read-only inspection if an expected PolicyApplied event does not arrive. Failure to read the daemon means the popup remains unverified; do not generate a guessed BLOCK/ALLOW notification.
* **Frontend statistics:** `blockedToday` now means devices whose latest observed decision is BLOCKED today, not every transient raw `CONNECTED/BLOCKED` audit record. Audit history retains the raw record.
* **ACK health:** A pending ACK is not resent until a fresh/retained successful daemon verification passes; stale policy versions cannot be re-ACKed.
* **Durable spool:** A filesystem write failure is surfaced as a critical event transport failure and does not claim successful delivery; USBGuard policy enforcement is unaffected.

## Verify on Windows (repository root, after backing up working files)

```powershell
mvn -B -f client-agent/pom.xml clean test
mvn -B -f admin-server/pom.xml clean test
cd frontend
npm ci
npm test
npm run lint
npm run build
```

**Review execution status:** The assistant ran JDK 21 listener compilation/smoke with isolated stubs and it passed BLOCK → ALLOW, attached revoke, dedup and native-state fallback. Frontend `tsc -b` passed and 32 standalone Node tests passed in the review container. Full Java Maven/JUnit and full browser/native frontend suite were **not executed** here: Maven and Linux native Rolldown dependencies were unavailable. Owner must rerun both Maven modules and frontend gates on Windows and report exact results; do not commit/deploy as accepted until then.

## Remaining Ubuntu verification (non-substitutable)

Check fresh install/upgrade preserves `/var/lib/usbshield-agent/endpoint.json` with least-privilege permissions; examine foreign ALLOW handling on a disposable VM; unknown Kingston must be BLOCKED with a real popup; A whitelist must ALLOW without a false popup; policy applied while USB remains attached must update Web; Allow A must not permit the same USB on B; reconnect, reboot, watch interruption, server outage and event replay must preserve correct state without duplicates. Do not reset machine-id or delete endpoint credentials just to recover a test.

# M5 Week 4 — integrated Agent/packaging, 2026-10-31

Source: `0b726304a29a78ac36aefbbc71e40fbd7758ae2e` (S2). Timezone: Asia/Bangkok. This run independently checks the merged M2 policy/session/watch changes and M3 HTTP-factory, Surefire and staging changes. M1 has not selected this SHA as the final candidate.

## Execution

| Capture | Command from repository root | Actual result |
|---|---|---|
| [01-agent-package](01-agent-package.txt) / [metadata](01-agent-package.json) | `mvn -o -B -f client-agent/pom.xml clean package` | **156/156 PASS**, 0 failure/error/skip; policy **12**, active-session **8**, watch **9**; executable JAR built |
| [02-staging-prepare](02-staging-prepare.txt) / [metadata](02-staging-prepare.json) | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File docs/qa/scripts/prepare-week4-staging.ps1` | **PASS**, safety-checked generated target, unmodified M3 preparation script, all 9 required files present |
| [03-packaging-week4](03-packaging-week4.txt) / [metadata](03-packaging-week4.json) | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week4-windows.ps1` | **PASS**, full verifier, neither skip flag used |
| [04-staging-integrity](04-staging-integrity.txt) / [metadata](04-staging-integrity.json) | Profiled evidence validator with `--stage-only` | **PASS**, exact 9-file set; every staged file byte-equal to current source or newly built JAR |
| [05-http-transport](05-http-transport.txt) / [metadata](05-http-transport.json) | `python docs/qa/scripts/run-agent-http-smoke.py --run-dir docs/qa/week4-evidence/2026-10-31/integrated-agent-packaging` | **FAIL retained**, caused by the QA harness assuming ISO strings for `Instant` JSON |
| [06-http-transport](06-http-transport.txt) / [metadata](06-http-transport.json) | Same HTTP runner with `--summary-name http-transport-summary-02.json` | **PASS**, 15 scenarios, 14 actual HTTP requests, **255 assertions**; exact semantic `Instant` comparison |

All captures record exact executable/argv/cwd, timestamps, source before/after, log hash and exit code. Tested application source-tree SHA-256: `3e312fbc65aa9bc71bb99f7dc4a9b1d0747fd1a19bf181430b54fa5e514bc59a`. QA helper/document edits are identified separately from application source.

Built Agent JAR: `client-agent/target/usbshield-agent.jar`, **20,177,551 bytes**, SHA-256 **`06e96129d308fd1bfb059d0885c8f2f378772cfeba76e24d649d8d3f43430a30`**. The [staging manifest](staging-manifest.json) compares full bytes and SHA-256 for all 9 source/destination pairs. Windows staging does not establish POSIX ownership/modes, a built Debian archive or installation behavior. `.deb`: **NOT BUILT IN THIS RUN**.

The [guard](../../../scripts/prepare-week4-staging.ps1) validates the resolved workspace target and refuses reparse points or unexpected files before invoking M3's cleanup/preparation script. Staging was absent before this execution. The full M3 verifier checks file presence and prints hashes; the independent byte comparison supplies the additional content-integrity proof.

## HTTP transport scope

The [QA runner](../../../scripts/run-agent-http-smoke.py) extracts classes and dependencies from the built Agent JAR into a fresh ignored `client-agent/target/qa-http-*` directory, then launches the [Java harness](../../../scripts/AgentHttpTransportSmoke.java). It calls the production `AdminServerClient(AgentProperties)` constructor over real loopback HTTP to a controlled JDK `HttpServer`. Random synthetic credentials remain in memory; no raw request bodies or tokens are backed up.

Executed assertions cover enrollment, heartbeat, policy, ACK and event methods/routes/headers/payloads; controlled 401/403/409, 503 then manual recovery and connection refusal. **All 15 scenarios / 255 assertions PASS** in the [successful summary](http-transport-summary-02.json). These are transport/client assertions. Backend authentication, scheduled retry, durable outbox, actual USBGuard enforcement and Ubuntu are separate scopes.

The [first-attempt summary](http-transport-summary.json) retains its actual failure after enroll/heartbeat/policy. The initial QA harness compared ACK time to an ISO string; this JAR serializes both `ack.appliedAt` and `event.occurredAt` as JSON numbers. The corrected harness decodes the value with Jackson's Java Time module and compares the exact `Instant`, retaining fractional-second precision. Retry 06 passed against the same application source and JAR; no application code was changed. This was a corrected test assumption, not a reproduced client failure. The successful summary records both wire types as `NUMBER` without retaining request bodies or credentials.

## Handoffs and historical evidence

[M5 technical acknowledgment](../../../evidence/M5_WEEK4_M2_M3_ACK_20261031.md) records receipt of M2 Part 2 and M3 guide/manifest/checklist/scripts. M3's old raw logs report 127 tests/JAR `219D…`, while its final closeout reports 156/JAR `A926…`; the owner needs to reconcile that provenance. The fresh M5 build/test/staging identities above are current independent evidence. Historical owner logs remain untouched.

Frontend and Admin Server source match [28/10](../../2026-10-28/windows-regression/README.md), including M4's reviewed frontend tree. Their browser/server/frontend results remain historical and source-applicable. No fresh browser run, screenshots, recording, dependency audit or narrated walkthrough is claimed. Packaging changed, so earlier static results alone do not establish its current state. [30/10](../../2026-10-30/post-pull-regression/README.md) remains a separate historical Agent/watch run.

## Evidence finalization

**Final evidence validation PASS.** The [reviewed run specification](run-spec.json) declares the expected suites, changed paths, five successful captures, retained failed capture, HTTP summary and staging map. The [validator](../../../scripts/validate-week4-post-pull.py) verifies actual log/source/JUnit/JAR hashes, both current HTTP helper hashes, HTTP revision/result, all nine staged byte comparisons, source equivalence, historical evidence preservation and documentation links. Outputs: [validation](validation.json), [JUnit summary](junit-summary.json), [build manifest](build-manifest.json), [scope equivalence](scope-equivalence.json), [environment](environment.json), [QA document/tool snapshot](qa-documents.json) and [checksums](checksums.json).

The original 30/10 default behavior remains available; this run uses its explicit profile. After documentation edits, refresh the QA manifest/checksums with:

```powershell
python docs/qa/scripts/validate-week4-post-pull.py --run-dir docs/qa/week4-evidence/2026-10-31/integrated-agent-packaging --baseline 0b726304a29a78ac36aefbbc71e40fbd7758ae2e --profile docs/qa/week4-evidence/2026-10-31/integrated-agent-packaging/run-spec.json
```

The run-specific `.gitattributes` preserves captured bytes on Windows checkouts. Earlier Week 1–3 and dated Week 4 evidence must remain unchanged. Generated JARs, H2 databases, browser profiles and credentials are not copied into this backup.

[Release report and remaining members](../../../WEEK4_RELEASE_VALIDATION.md), [matrix](../../../WEEK4_TEST_INVENTORY.md), [Event History](../../../WEEK4_EVENT_HISTORY_REVIEW.md), [demo](../../../../DEMO_SCRIPT.md), [Ubuntu acceptance](../../../WEEK4_UBUNTU_ACCEPTANCE.md).

Remaining group work: M1 API freeze/security/manifest/candidate decision; M2 Part 3 notification/docs/Q&A/IPC and policy/session/watch contract clarification; M3 historical provenance alignment and Debian build on Linux/CI when available. Ubuntu 24.04, physical USB, USBGuard daemon/IPC, actual session/native GUI, systemd/reboot and `.deb` lifecycle remain **NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4**.

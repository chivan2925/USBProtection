# M5 Week 4 — M2 Part 3 regression, 2026-11-01

Source **S3 = `3efa50b50031dac9cec5738e286ec0a7730529ae`**. Timezone: Asia/Bangkok. M2 Part 3 (`942527c`) is merged; this run independently validates its policy-review and notification changes. M1 has not selected this SHA as the final release candidate. Earlier M5 S2 QA was committed as `48a13b3` and merged through `176e547`; these new QA changes are separate working-tree deliverables.

## Actual execution

| Capture | Command from repository root | Result |
|---|---|---|
| [01-agent-package](01-agent-package.txt) / [metadata](01-agent-package.json) | `mvn -o -B -f client-agent/pom.xml clean package` | **174/174 PASS**, 0 failures/errors/skips, executable JAR built |
| [02-staging-prepare](02-staging-prepare.txt) / [metadata](02-staging-prepare.json) | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File docs/qa/scripts/prepare-week4-staging.ps1` | **PASS**, guarded generated target, current JAR copied by M3's unmodified script |
| [03-packaging-week4](03-packaging-week4.txt) / [metadata](03-packaging-week4.json) | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week4-windows.ps1` | **PASS**, full verifier with neither skip flag |
| [04-staging-integrity](04-staging-integrity.txt) / [metadata](04-staging-integrity.json) | Profiled evidence validator with `--stage-only` | **PASS**, exact nine-file set; all source/destination bytes equal |
| [05-http-transport](05-http-transport.txt) / [metadata](05-http-transport.json) | `python docs/qa/scripts/run-agent-http-smoke.py --run-dir docs/qa/week4-evidence/2026-11-01/m2-part3-regression` | **PASS**, production constructor, 15 scenarios / 14 loopback requests / **255 assertions** |

All five captures retain the same source revision and source-tree SHA-256 **`438fc983ed9e2b656884f5dceb7802f25de227d945a260b3a35c6b29630f8722`** before/after each command. Exact executable/arguments, cwd, log hash, timestamps and exits are in the adjacent JSON. Maven ran **14:37:58–14:38:37 +07:00**, on 1 November 2026. Application source was not edited by M5.

Current Agent JAR: `client-agent/target/usbshield-agent.jar`, **20,186,115 bytes**, SHA-256 **`63255be52082dd8883b102633d8ec962b2d3e864a4204861b6b69cd8f532dd24`**. It is a new artifact; earlier JAR/staging checksums remain tied to their earlier artifacts. [Staging manifest](staging-manifest.json) records all nine source/destination hashes and full-byte comparisons. These checks do not verify POSIX ownership/modes, a Debian archive or an installation. `.deb`: **NOT BUILT IN THIS RUN**.

## New M2 coverage and review

| New suite | Executed tests | Verification boundary |
|---|---|---|
| `UsbGuardWeek4CopilotReviewRegressionTest` | **9/9 PASS** | Re-resolve changing rule IDs on removals/replacement/rollback; reject malformed/unknown rule targets and trailing tokens; preserve recognized foreign attributes; refuse unsupported conditions before mutation |
| `Week4NotificationCorrectnessTest` | **7/7 PASS** | Presence target, exclusions, Remove/switch dedup, bounded cooldown state, clock rollback, publisher failure and disabled notification |
| `Week4DesktopNotifierPeerSafetyTest` | **1/1 PASS** | Username authorization predicate only; does not execute Linux peer credentials or socket ACL |
| `UsbGuardWeek4NotificationEventTargetTest` | **1/1 PASS** | Effective watch target drives notification while the event preserves BLOCKED semantics despite stale device-rule state |

These **18** tests are included in **174**, alongside existing policy **12**, active-session **8** and watch **9** tests. M2's owner report of 43 isolated checks used unshipped stubs and is not substituted for this genuine Maven/JUnit run.

[M5 Part 3 ACK](../../../evidence/M5_WEEK4_M2_PART3_ACK_20261101.md) records independent local source review plus executed regression for the submitted two HIGH/one MEDIUM policy fixes. Windows unit/mock verification is **PASS**; external PR discussion resolution, owner approval and actual USBGuard grammar/runtime are not inferred. The rule verifier intentionally supports a conservative subset and rejects unsupported conditions; arbitrary concurrent administrator changes remain outside its locking boundary.

The [HTTP summary](http-transport-summary.json) identifies the current JAR, runner/harness hashes, Java version and exact source-launch command. It calls `AdminServerClient(AgentProperties)` from built classes against a controlled JDK `HttpServer`. Assertions cover methods/routes/headers/DTOs, 401/403/409 handling, 503 then manual recovery and connection refusal. Both Instant fields are JSON numbers decoded to the exact intended time. Random synthetic credentials remain in memory. This does not execute real Spring backend authorization, scheduled retry, durable outbox or native enforcement.

## Historical applicability

Frontend and Admin Server source are unchanged from [S0 / 28 October](../../2026-10-28/windows-regression/README.md); their original frontend/server/HTTP/browser results and M4 shared-frontend ACK remain dated historical evidence. Compared with [S2 / 31 October](../../2026-10-31/integrated-agent-packaging/README.md), packaging and Agent `sync/` source are also unchanged. The new Agent JAR still required fresh staging and transport verification. No fresh frontend/server/browser suite, audit, screenshots, recording or narrated walkthrough is claimed here. [S1 / 30 October](../../2026-10-30/post-pull-regression/README.md) remains a separate historical watch run.

The existing S2 failed HTTP harness attempt is preserved in S2; **all five commands in this S3 run passed**. Week 1–3 and all earlier dated Week 4 evidence remain untouched. M3's older raw/report provenance discrepancy remains an owner follow-up; this independently tested JAR does not retroactively certify either earlier artifact.

## Evidence finalization

**Final evidence validation PASS.** The [run specification](run-spec.json) requires 174 tests, all seven Week 4 suites, the exact reviewed application delta, five successful captures, source/JAR/HTTP identity and nine staged byte comparisons. The [validator](../../../scripts/validate-week4-post-pull.py) checks those artifacts, documentation links, preserved historical evidence and explicit source comparisons with S0 and S2. Outputs: [validation](validation.json), [JUnit summary](junit-summary.json), [build manifest](build-manifest.json), [scope equivalence](scope-equivalence.json), [environment](environment.json), [QA document/tool snapshot](qa-documents.json) and [checksums](checksums.json). Refresh the final QA manifests after document updates:

```powershell
python docs/qa/scripts/validate-week4-post-pull.py --run-dir docs/qa/week4-evidence/2026-11-01/m2-part3-regression --baseline 3efa50b50031dac9cec5738e286ec0a7730529ae --profile docs/qa/week4-evidence/2026-11-01/m2-part3-regression/run-spec.json
```

The run-specific `.gitattributes` preserves captured bytes across Windows Git checkouts. Generated binaries, credentials, H2 databases and runtime profiles are not copied into this evidence backup.

## Remaining dependencies

- M1: API/security freeze, documentation/artifact candidate and group release decision.
- M2/M3: native peer/IPC signoff and watch contract follow-up. The peer-name unit test does not establish `SO_PEERCRED`, per-UID ACL, socket-path collision safety or a rendered popup. Current root-owned directory `1777` and GUI-owned socket `0660` require M3's secure provisioning/reachability review.
- M3: reconcile historical raw/report provenance and supply a real Debian package when Linux/CI build is available. Current Windows staging does not complete that runtime gate.
- Ubuntu 24.04: USBGuard/USB, actual attribution/native GUI, permissions, install/upgrade/remove, systemd/reboot and enforcement remain **NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4**.

[Release report](../../../WEEK4_RELEASE_VALIDATION.md), [matrix](../../../WEEK4_TEST_INVENTORY.md), [Event History review](../../../WEEK4_EVENT_HISTORY_REVIEW.md), [Demo Script](../../../../DEMO_SCRIPT.md), [Ubuntu acceptance](../../../WEEK4_UBUNTU_ACCEPTANCE.md).

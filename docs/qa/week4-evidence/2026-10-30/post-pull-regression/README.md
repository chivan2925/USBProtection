# M5 Week 4 — post-pull Windows regression, 2026-10-30

Source: `751387bf166c528326ae91c6a16a820a6d8fc24a`. Timezone: Asia/Bangkok. Scope: affected Agent/M2 changes and received M4 handoff after the [28/10 regression](../../2026-10-28/windows-regression/README.md). This source is not an owner-selected final release candidate.

## Fresh execution

| Capture | Command from repository root | Result |
|---|---|---|
| [01-agent-package](01-agent-package.txt) / [metadata](01-agent-package.json) | `mvn -o -B -f client-agent/pom.xml clean package` | **136/136 PASS**, 0 failures/errors/skips; new `UsbGuardWeek4WatchStressTest` **9/9 PASS**; executable JAR built |
| [02-packaging-static](02-packaging-static.txt) / [metadata](02-packaging-static.json) | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week3-windows.ps1 -CheckBuiltJar` | **PASS** against the newly built Agent JAR; no installation or native service execution |

Both commands used stable source-tree SHA-256 `21318e741b1571ab31b887a969d05e10e462490063f686403b33098f79173715`. Agent JAR SHA-256 at capture: `26f8d74b450c399a798a9cbc7a0c0fc34d7a80cb34960e34a21c176c74d6e5d9`. JUnit counts, artifact path/size/write-time and checksum are recorded in actual command metadata, not inferred from an earlier run. Failed prior setup or historical runs are not relabeled as successful execution here.

The [capture runner](../../../scripts/capture-week4.mjs) now accepts `--run-dir` and `--expected-revision`, requires a complete SHA for a new directory and refuses an existing capture ID. This run's `.gitattributes` preserves artifact bytes through Windows checkouts. New evidence never overwrites the 28/10 captures.

## Source equivalence and handoff

The complete tracked frontend, Admin Server and packaging trees are unchanged from `2013ce7`; neither revision has a tracked `.github` entry. Only Agent listener and its new test change in the application delta. [Source-equivalence manifest](scope-equivalence.json) records actual Git tree IDs/path comparisons and absent scopes explicitly. The 28/10 frontend/server/browser/staging/shell results remain **historical executions applicable to unchanged source**; no fresh frontend tests, browser screenshots, audit, narrated demonstration or recording is claimed for 30/10. Historical artifact hashes remain tied to the old artifacts.

[M4 handoff](../../../evidence/M4_WEEK4_HANDOFF.md) has been received. [M5 technical acknowledgment](../../../evidence/M5_WEEK4_POST_PULL_ACK_20261030.md) compares the full frontend tree at M4's reviewed SHA `6127766`, M5's tested SHA `2013ce7` and current source. It closes the missing M4 review input for the unchanged source, without selecting a final candidate or signing for other members.

M2's Part 1 report still records tests as unexecuted in its package-preparation environment; **the independent M5 Windows execution here is now PASS**. M2 owns remaining policy/session/notification/Q&A/IPC handoffs. The short-delay reconnect fixtures do not establish duplicate suppression after the production 5-second retry when dedup is 1.5 seconds; this is an M2 contract/coverage question, not a reproduced P0 or Ubuntu result.

## Evidence verification

**Post-pull evidence validation PASS.** [Validator](../../../scripts/validate-week4-post-pull.py) checks command/log/source hashes, actual JUnit reports, new suite, captured Agent JAR hash/content, three unchanged tracked source trees, historical evidence preservation and documentation links. Outputs: [validation](validation.json), [JUnit summary](junit-summary.json), [build manifest](build-manifest.json), [scope equivalence](scope-equivalence.json), [environment](environment.json), [QA document/tool snapshot](qa-documents.json), [evidence checksums](checksums.json). The validator's initial assumption that `.github` was tracked and its correction are retained in [validation attempts](validation-attempts.json); no application test failed from that utility issue.

Run validation after the two captures and final document updates:

```powershell
python docs/qa/scripts/validate-week4-post-pull.py --run-dir docs/qa/week4-evidence/2026-10-30/post-pull-regression --baseline 751387bf166c528326ae91c6a16a820a6d8fc24a
```

[Release report and remaining members](../../../WEEK4_RELEASE_VALIDATION.md), [matrix](../../../WEEK4_TEST_INVENTORY.md), [Event review](../../../WEEK4_EVENT_HISTORY_REVIEW.md), [demo](../../../../DEMO_SCRIPT.md), [Ubuntu acceptance](../../../WEEK4_UBUNTU_ACCEPTANCE.md).

Ubuntu 24.04, physical USB, USBGuard daemon/IPC/session/GUI, systemd, reboot, `.deb` installation and actual Agent enforcement remain **NOT EXECUTED / DEFERRED until after Week 4**. No raw tokens, H2 data or browser profiles are backed up here. Final group RC acceptance still needs M1's frozen contract/security/source decision, remaining M2 handoff and M3 package/guide disposition.

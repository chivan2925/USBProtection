# M5 Week 4 — post-pull technical acknowledgment

Date: **2026-10-30, Asia/Bangkok (+07:00)**. Reviewed HEAD: **`751387bf166c528326ae91c6a16a820a6d8fc24a`**. Scope: M5 review of the received M4 handoff and the M2 Part 1 delta after M5 commit `63ea070`.

**M4 shared-frontend handoff: RECEIVED / M5 TECHNICALLY ACKNOWLEDGED for the unchanged frontend at this HEAD.** This record documents technical review prepared by Codex for the user. It does not create a human member signature, select the final release candidate, or approve a group release.

## Exact frontend equivalence

Independent read-only Git checks on 2026-10-30 returned:

| Check | Result |
|---|---|
| `git rev-parse HEAD` | `751387bf166c528326ae91c6a16a820a6d8fc24a` |
| `git diff --name-only 6127766a1d3b982efa0ad0f9414ff1abc368cbb5..HEAD -- frontend` | No changed paths |
| `git diff --name-only 2013ce726ef8cc960b30720435be23e8ccef1903..HEAD -- frontend` | No changed paths |
| `git status --short -- frontend` | No working-tree changes reported |
| `git rev-parse 6127766a1d3b982efa0ad0f9414ff1abc368cbb5:frontend` | `85938291c72c25de0fc7716261440cfe136128e7` |
| `git rev-parse 2013ce726ef8cc960b30720435be23e8ccef1903:frontend` | `85938291c72c25de0fc7716261440cfe136128e7` |
| `git rev-parse HEAD:frontend` | `85938291c72c25de0fc7716261440cfe136128e7` |

The same Git tree identifies the complete tracked frontend, including source, shared adapters/auth, Event mapping/filter/types, browser harness, package manifest/lockfile and configuration. Thus M4's reviewed frontend at `6127766` and M5's tested frontend at `2013ce7` are byte-identical to the tracked frontend at this HEAD; no frontend implementation delta requires a new UI test run solely because M2's Agent code was merged. Ignored build/runtime artifacts are outside this source-equivalence statement.

## M4 input and M5 acceptance

[M4_WEEK4_HANDOFF.md](M4_WEEK4_HANDOFF.md) records M4's Week 4 review of `api.ts`, `http.ts`, Event mapping, browser harness, demo boundary and production error states on `6127766`. M4's [Admin Guide](../../ADMIN_GUIDE.md) is now marked final for Week 4. The received handoff explicitly leaves acknowledgment to M5 and excludes real Agent/USBGuard/physical USB acceptance.

M5 accepts that shared-frontend technical handoff for the exact unchanged source identified above. The former missing-input dependency is therefore closed for this reviewed frontend. M5 does not reattribute M4's command statements as new independent M5 execution and does not replace the handoff's stated reviewed SHA.

[28/10 M5 evidence](../week4-evidence/2026-10-28/windows-regression/README.md) retains its original source SHA, commands, counts, screenshots and recording: 96 frontend tests, 26 server tests and the fixture/management/Events/packaged-JAR browser results are **28/10 results**. The 207-frame replay remains a recording from that run. Source equivalence supports carrying the frontend evidence forward with its provenance; none of those checks or the recording was rerun by this acknowledgment task.

Final candidate selection remains with M1/the owners. If frontend, backend contract, harness, configuration or delivery artifacts subsequently change, compare the affected source/build manifests and repeat the affected review/checks. This acceptance is not an advance approval of later changes.

## M2 Part 1 and affected M5 follow-up

The delta since `63ea070` changes production [UsbGuardEventListener.java](../../../client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardEventListener.java), adds [UsbGuardWeek4WatchStressTest.java](../../../client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardWeek4WatchStressTest.java) and M2 documentation; the other changes are M4's handoff and Admin Guide. Frontend and server application source are unchanged in that delta.

M2's [Part 1 report](../../usbguard/WEEK4_PART1_WATCH_RECOVERY.md) still describes Maven execution as pending in its package-creation environment. **The independent M5 QA execution on 30/10 is now available:** Agent `clean package` **136/136 PASS**, including `UsbGuardWeek4WatchStressTest` **9/9 PASS**, zero failures/errors/skips; current-JAR packaging/static verifier **PASS**, both exit 0 at HEAD `751387b`. These results are from the main QA execution, not this read-only acknowledgment task. [New run index](../week4-evidence/2026-10-30/post-pull-regression/README.md), [Agent command metadata](../week4-evidence/2026-10-30/post-pull-regression/01-agent-package.json) and [static command metadata](../week4-evidence/2026-10-30/post-pull-regression/02-packaging-static.json) record their exact source, commands and results.

Both captures report stable source-tree SHA-256 **`21318e741b1571ab31b887a969d05e10e462490063f686403b33098f79173715`**. The newly built Agent JAR is **`26f8d74b450c399a798a9cbc7a0c0fc34d7a80cb34960e34a21c176c74d6e5d9`** (20,175,259 bytes). The [post-pull validation result](../week4-evidence/2026-10-30/post-pull-regression/validation.json) was observed **PASS** for the two executed checks, actual JUnit/JAR/source hashes, source-equivalence and preserved historical evidence. The main QA collector refreshes document/tool hashes after final document updates. No new frontend/browser execution, screenshots, recording or Ubuntu runtime is inferred from this Agent run.

**Coverage/contract question for M2, not a proven P0 or reproduced user bug:** production watch retry is 5,000 ms (`UsbGuardEventListener.java:70–71,136–137`), while dedup remains 1,500 ms (`:36,227`). The process-restart test injects 10 ms (`UsbGuardWeek4WatchStressTest.java:184`). It therefore does not establish suppression of an identical replay after the production backoff. Actual USBGuard replay behavior has not been demonstrated here. M2 should confirm the intended contract and appropriate production-delay scenario; M5 must not describe the short-delay fixture as that runtime evidence.

M2 retains ownership of policy/session Part 2 and notification/documentation/Q&A/IPC Part 3, which its [three-part plan](../../usbguard/WEEK4_M2_THREE_PART_PLAN.md) records as NOT STARTED. Those are pending owner deliverables, not newly demonstrated application defects. M5 should incorporate their eventual handoffs and affected checks before final candidate sign-off. The Part 1 report's “package contains tests only” wording also needs M2 clarification because production listener source changed.

## M5 disposition

| Work item | Post-pull disposition |
|---|---|
| W4-M5-02 | Frontend equivalence verified; affected Agent package **136/136**, new watch suite **9/9** and current-JAR static validation **PASS** on 30/10. The old 127-test result retains its 28/10 provenance. Final RC regression still follows the selected source/artifacts. |
| W4-M5-03 / W4-H04 | M4 input received; M5 technical shared-frontend acknowledgment complete for `751387b` with the identical frontend tree. [Event History review](../WEEK4_EVENT_HISTORY_REVIEW.md) updated accordingly. |
| W4-M5-04 | Preserve the 28/10 automatic rehearsal/replay provenance. Record current source and M2 limitations in the demo follow-up; actual Ubuntu demo and narrated walkthrough are not inferred from the old recording. |
| W4-M5-05 | Fresh post-pull command/JUnit/source/Agent-artifact evidence is stored separately; validator result observed PASS. Preserve earlier evidence and refresh final QA document/tool hashes after edits. |
| W4-M5-06 | Received M4 acknowledgment and fresh Agent results are available to the release report. M2/M1/M3/final-candidate decisions remain separate; this acknowledgment does not itself declare group GO. |

Ubuntu 24.04, physical USB, USBGuard daemon/session/GUI/IPC, systemd/reboot and package lifecycle acceptance remain **NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4**. No application/test/shared-frontend code was changed by this review.

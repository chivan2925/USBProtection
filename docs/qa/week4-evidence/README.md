# Week 4 — QA evidence index

Owner: M5. Updated: 2026-10-31 (Asia/Bangkok).

Current capture: [Integrated Agent/packaging checks, 31/10](2026-10-31/integrated-agent-packaging/README.md), source `0b726304a29a78ac36aefbbc71e40fbd7758ae2e`: **156/156 Agent tests PASS**, including policy **12**, active-session **8** and watch **9**; guarded staging preparation, full Week 4 verifier and independent **9/9 staged-file byte equality PASS**. Production-constructor loopback HTTP smoke **PASS: 15 scenarios / 255 assertions**; [final evidence validation PASS](2026-10-31/integrated-agent-packaging/validation.json). [M5 M2/M3 acknowledgment](../evidence/M5_WEEK4_M2_M3_ACK_20261031.md) records the received handoffs and limits. The first failed HTTP attempt is retained with its corrected QA timestamp assumption.

Historical post-pull capture: [Agent/static regression and received M4 handoff, 30/10](2026-10-30/post-pull-regression/README.md), source `751387bf166c528326ae91c6a16a820a6d8fc24a`: **136/136 Agent tests** and JAR static verification **PASS**. [M5 technical acknowledgment](../evidence/M5_WEEK4_POST_PULL_ACK_20261030.md) records the received M4 input and exact unchanged frontend tree.

Historical comprehensive capture: [Windows regression, 28/10](2026-10-28/windows-regression/README.md), source `2013ce726ef8cc960b30720435be23e8ccef1903`. Frontend/server/browser evidence remains historical, with source applicability verified separately; no rerun, new screenshots or new recording is claimed on 30/10 or 31/10. Agent and packaging changes have fresh checks in their dated runs. Earlier audit, artifact identities and runtime evidence retain their original dates and scope.

- [Release validation and member handoffs](../WEEK4_RELEASE_VALIDATION.md).
- [Test matrix / Blueprint / DoD inventory](../WEEK4_TEST_INVENTORY.md).
- [Event History review](../WEEK4_EVENT_HISTORY_REVIEW.md).
- [Demo Script](../../DEMO_SCRIPT.md) and [Ubuntu acceptance sheets](../WEEK4_UBUNTU_ACCEPTANCE.md).

Each executed command has a `.txt` log and same-stem `.json` metadata with exact executable/argv/cwd, scope, times, source revision/tree hash, exit status and fresh screenshot records. Failed attempts retain their actual result. Build artifacts are identified by checksums and package content comparison; generated JARs, runtime databases, tokens and browser profiles are not copied into this evidence directory.

Screenshots and the browser recording use synthetic QA data. Fixture evidence, real Spring Boot/H2 with HTTP-seeded events, and actual Ubuntu behavior are separate levels. Native GUI, physical USB/USBGuard, systemd/reboot, clean install and real Agent enforcement remain **NOT EXECUTED / DEFERRED until after Week 4**.

Week 1–3 evidence is preserved. [Week 3 Windows acceptance](../WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md) remains closed; this index does not reopen its gate or claim a Week 4 release tag/owner approval.

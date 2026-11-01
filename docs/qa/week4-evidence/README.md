# Week 4 — QA evidence index

Owner: M5. Updated: 2026-11-01 (Asia/Bangkok).

Current capture: [M2 Part 3 regression, 01/11](2026-11-01/m2-part3-regression/README.md), source `3efa50b50031dac9cec5738e286ec0a7730529ae`: **174/174 Agent tests PASS**, including all **18 new tests** (policy-review 9, notification 7, peer-name predicate 1, effective event target 1). Guarded staging preparation, full verifier, **9/9 staged-file byte equality** and production-constructor HTTP smoke (**15 scenarios / 255 assertions**) **PASS**. [Final evidence validation PASS](2026-11-01/m2-part3-regression/validation.json). [M5 Part 3 acknowledgment](../evidence/M5_WEEK4_M2_PART3_ACK_20261101.md) records local review and test boundaries; native peer credentials/ACL/GUI and external PR acceptance remain separate.

Historical capture: [Integrated Agent/packaging checks, 31/10](2026-10-31/integrated-agent-packaging/README.md), source `0b726304a29a78ac36aefbbc71e40fbd7758ae2e`: **156/156 Agent tests**, full verifier, **9/9 staged-file byte equality** and production HTTP smoke **255 assertions PASS**; [validation PASS](2026-10-31/integrated-agent-packaging/validation.json). [M5 M2/M3 acknowledgment](../evidence/M5_WEEK4_M2_M3_ACK_20261031.md) retains that source's handoff and limits. Its failed HTTP harness attempt remains preserved.

Historical post-pull capture: [Agent/static regression and received M4 handoff, 30/10](2026-10-30/post-pull-regression/README.md), source `751387bf166c528326ae91c6a16a820a6d8fc24a`: **136/136 Agent tests** and JAR static verification **PASS**. [M5 technical acknowledgment](../evidence/M5_WEEK4_POST_PULL_ACK_20261030.md) records the received M4 input and exact unchanged frontend tree.

Historical comprehensive capture: [Windows regression, 28/10](2026-10-28/windows-regression/README.md), source `2013ce726ef8cc960b30720435be23e8ccef1903`. Frontend/server/browser evidence remains historical, with source applicability verified separately; later post-pull runs do not claim new browser screenshots or recordings. Agent and packaging/artifact checks have their own dated results. Earlier audit, artifact identities and runtime evidence retain their original dates and scope.

- [Release validation and member handoffs](../WEEK4_RELEASE_VALIDATION.md).
- [Test matrix / Blueprint / DoD inventory](../WEEK4_TEST_INVENTORY.md).
- [Event History review](../WEEK4_EVENT_HISTORY_REVIEW.md).
- [Demo Script](../../DEMO_SCRIPT.md) and [Ubuntu acceptance sheets](../WEEK4_UBUNTU_ACCEPTANCE.md).

Each executed command has a `.txt` log and same-stem `.json` metadata with exact executable/argv/cwd, scope, times, source revision/tree hash, exit status and fresh screenshot records. Failed attempts retain their actual result. Build artifacts are identified by checksums and package content comparison; generated JARs, runtime databases, tokens and browser profiles are not copied into this evidence directory.

Screenshots and the browser recording use synthetic QA data. Fixture evidence, real Spring Boot/H2 with HTTP-seeded events, and actual Ubuntu behavior are separate levels. Native GUI, physical USB/USBGuard, systemd/reboot, clean install and real Agent enforcement remain **NOT EXECUTED / DEFERRED until after Week 4**.

Week 1–3 evidence is preserved. [Week 3 Windows acceptance](../WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md) remains closed; this index does not reopen its gate or claim a Week 4 release tag/owner approval.

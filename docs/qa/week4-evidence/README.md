# Week 4 — QA evidence index

Owner: M5. Updated: 2026-11-01 (Asia/Bangkok).

Current capture: [M1 integration regression, 01/11](2026-11-01/m1-integration-regression/README.md), source `c6cc1df21283588d70a792a37d42870bdea49497`: frontend build, **28/28 backend tests**, **1/1 policy HTTP test**, management/Events/packaged-JAR browser suites (**25 screenshots**) and actual production Agent client → Spring Server (**215 assertions / 64 scenarios**) **PASS**. [Final evidence validation PASS](2026-11-01/m1-integration-regression/validation.json), including **5 React assets byte-equal in the JAR**. [M5 M1 acknowledgment](../evidence/M5_WEEK4_M1_ACK_20261101.md) separates M1 inputs/current technical proof from final group acceptance. First Agent–Server attempt remains FAIL with its corrected QA/environment assumptions.

Historical capture: [M2 Part 3 regression, 01/11](2026-11-01/m2-part3-regression/README.md), source `3efa50b50031dac9cec5738e286ec0a7730529ae`: **174/174 Agent tests**, staging **9/9 byte equality**, full verifier and controlled-server HTTP smoke **255 assertions PASS**; [validation PASS](2026-11-01/m2-part3-regression/validation.json). [Part 3 ACK](../evidence/M5_WEEK4_M2_PART3_ACK_20261101.md) retains unit/native boundaries. Agent/frontend/packaging source remains identical in S4; backend integration is newly executed above.

Historical capture: [Integrated Agent/packaging checks, 31/10](2026-10-31/integrated-agent-packaging/README.md), source `0b726304a29a78ac36aefbbc71e40fbd7758ae2e`: **156/156 Agent tests**, full verifier, **9/9 staged-file byte equality** and production HTTP smoke **255 assertions PASS**; [validation PASS](2026-10-31/integrated-agent-packaging/validation.json). [M5 M2/M3 acknowledgment](../evidence/M5_WEEK4_M2_M3_ACK_20261031.md) retains that source's handoff and limits. Its failed HTTP harness attempt remains preserved.

Historical post-pull capture: [Agent/static regression and received M4 handoff, 30/10](2026-10-30/post-pull-regression/README.md), source `751387bf166c528326ae91c6a16a820a6d8fc24a`: **136/136 Agent tests** and JAR static verification **PASS**. [M5 technical acknowledgment](../evidence/M5_WEEK4_POST_PULL_ACK_20261030.md) records the received M4 input and exact unchanged frontend tree.

Historical comprehensive capture: [Windows regression, 28/10](2026-10-28/windows-regression/README.md), source `2013ce726ef8cc960b30720435be23e8ccef1903`. Its executions, **39 screenshots / 207 CDP frames**, audit and artifact identities retain that date/scope. Later runs record their own affected checks; S4 adds 25 screenshots and no new recording. Source equivalence never changes an old run's execution date.

- [Release validation and member handoffs](../WEEK4_RELEASE_VALIDATION.md).
- [Test matrix / Blueprint / DoD inventory](../WEEK4_TEST_INVENTORY.md).
- [Event History review](../WEEK4_EVENT_HISTORY_REVIEW.md).
- [Demo Script](../../DEMO_SCRIPT.md) and [Ubuntu acceptance sheets](../WEEK4_UBUNTU_ACCEPTANCE.md).

Each executed command has a `.txt` log and same-stem `.json` metadata with exact executable/argv/cwd, scope, times, source revision/tree hash, exit status and fresh screenshot records. Failed attempts retain their actual result. Build artifacts are identified by checksums and package content comparison; generated JARs, runtime databases, tokens and browser profiles are not copied into this evidence directory.

Screenshots and the browser recording use synthetic QA data. Fixture evidence, real Spring Boot/H2 with HTTP-seeded events, and actual Ubuntu behavior are separate levels. Native GUI, physical USB/USBGuard, systemd/reboot, clean install and real Agent enforcement remain **NOT EXECUTED / DEFERRED until after Week 4**.

Week 1–3 evidence is preserved. [Week 3 Windows acceptance](../WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md) remains closed; this index does not reopen its gate or claim a Week 4 release tag/owner approval.

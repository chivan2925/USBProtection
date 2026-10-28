# Week 4 — QA evidence index

Owner: M5. Date: 2026-10-28 (Asia/Bangkok).

Fresh capture: [Windows regression, 28/10](2026-10-28/windows-regression/README.md), source `2013ce726ef8cc960b30720435be23e8ccef1903`.

- [Release validation and member handoffs](../WEEK4_RELEASE_VALIDATION.md).
- [Test matrix / Blueprint / DoD inventory](../WEEK4_TEST_INVENTORY.md).
- [Event History review](../WEEK4_EVENT_HISTORY_REVIEW.md).
- [Demo Script](../../DEMO_SCRIPT.md) and [Ubuntu acceptance sheets](../WEEK4_UBUNTU_ACCEPTANCE.md).

Each executed command has a `.txt` log and same-stem `.json` metadata with exact executable/argv/cwd, scope, times, source revision/tree hash, exit status and fresh screenshot records. Failed attempts retain their actual result. Build artifacts are identified by checksums and package content comparison; generated JARs, runtime databases, tokens and browser profiles are not copied into this evidence directory.

Screenshots and the browser recording use synthetic QA data. Fixture evidence, real Spring Boot/H2 with HTTP-seeded events, and actual Ubuntu behavior are separate levels. Native GUI, physical USB/USBGuard, systemd/reboot, clean install and real Agent enforcement remain **NOT EXECUTED / DEFERRED until after Week 4**.

Week 1–3 evidence is preserved. [Week 3 Windows acceptance](../WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md) remains closed; this index does not reopen its gate or claim a Week 4 release tag/owner approval.

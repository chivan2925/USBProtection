# QA evidence validation — 2026-10-28

Reviewed revision: `39561907ef92f97234f449140bc394ddfe4f30e8`; M3 implementation commit: `0c9bb6d2b626abe1f22eed3559d89f19e503138b`.

- Fresh Agent package result: **26 suites, 127 tests, 0 failures, 0 errors, 0 skips**; executable JAR built.
- Week 3 static verifier with the fresh JAR: **PASS**.
- The M3 source/test review confirms rollback on policy-state persistence failure, ACK suppression before durable state, and pending-ACK retention after a failed clear.
- QA-document diff check: no whitespace errors in the M5 QA changes.
- Markdown-link validation: **230 checked, 0 broken**. Ubuntu, `.deb`, installer, service, USBGuard, GUI, and reboot results are not inferred.

# USBShield — M2 Week 4: Three-Part Execution Plan

**Repository:** `chivan2925/USBProtection`  
**Baseline:** `main` at `1614f9b4e325e8cb42da87946601810d3c5e53de` (2026-10-30)  
**Owner:** Member 2 (USBGuard, active sessions, desktop notification)  
**Week 3 gate:** `PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`.

Week 4 is stabilization, documentation, and release-candidate preparation. No new product feature, global policy, or API contract change is authorized. Ubuntu 24.04 hardware/daemon/GUI/systemd/reboot/installer E2E remains deferred until **after Week 4**.

| Part | Work IDs from the Week 4 plan | Focus | Intended output | Status |
| --- | --- | --- | --- | --- |
| **Part 1 — USBGuard watch and recovery resilience** | **W4-M2-01** | Bound watch input, reconnect after daemon unavailability, dedup across restart, malformed/oversized frames, Insert/Remove and event/notification independence | `UsbGuardEventListener.java`, new regression tests, Part 1 report | **PATCH PREPARED — Maven owner validation pending** |
| **Part 2 — Policy, identity and session correctness** | **W4-M2-02**, **W4-M2-03** | Rule order/class-08, hash/no-serial identity, fail-closed foreign/base policy, local/remote/ambiguous sessions and `UNKNOWN` | Focused tests/fixtures and policy/session review | NOT STARTED |
| **Part 3 — Notification, documentation and handoff** | **W4-M2-04**, **W4-M2-05**, **W4-M2-06**, **W4-M2-07 (P1)** | Correct-UID notification and failure isolation, core explanations, technical Q&A, IPC handoff to M3, deferred Ubuntu test checklist for M5 | Notification regression, `docs/usbguard/` final technical docs and demo Q&A | NOT STARTED |

## Boundaries and sequence

1. Each part is applied to an identified clean repository commit and validated with `mvn -B -f client-agent/pom.xml clean test` and `mvn -B -f client-agent/pom.xml -DskipTests package` before the owner commits/pushes.
2. M2 changes only `client-agent/.../{usbguard,session,notification}/**`, M2 event enrichment, corresponding tests, `packaging/usbguard/**`, and `docs/usbguard/**`. M3 retains Agent sync, endpoint, outbox, packaging and runtime installer ownership.
3. Part 2 should be based on the committed Part 1 result; Part 3 should be based on the committed Part 2 result. Independent review must compare against the correct HEAD.
4. The owner and M5 record actual Maven results (including test counts, exit status, SHA, and platform). A patch prepared by this document is **not** an execution PASS.
5. Native Ubuntu watch/USBGuard IPC, real device removal/reinsert, real graphical notifications, and reboot **remain NOT EXECUTED** under the agreed schedule.

## Integration handoff

M3: no modification to Agent API contracts, lifecycle scripts, or service permissions in Part 1. The watch process still calls `usbguard watch` using argument-array `ProcessBuilder`, with the established 5-second reconnect delay in production.  
M5: use the new Week 4 test class for Windows evidence; do not relabel it as native USBGuard validation.

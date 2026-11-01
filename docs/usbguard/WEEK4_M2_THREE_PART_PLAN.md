# USBShield — M2 Week 4: Three-Part Execution Plan

**Repository:** `chivan2925/USBProtection`  
**Part 2 base:** `main` at `ab346931d8d0994b5c4e015feb823b4cf8df5a2f` (2026-10-30 UTC)  
**Part 3 base:** `main` at `176e547f5bf093ef8c9aebdfabd29cab7177d73e` (2026-10-31 15:57 UTC)  
**Owner:** Member 2 — USBGuard, active-user attribution and desktop notification  
**Week 3 gate:** `PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`.

Week 4 is limited to stabilization, documentation, tests and release-candidate preparation. No new product feature or Agent–Server API change is included. Actual Ubuntu 24.04 USBGuard/USB hardware, GUI and systemd testing remains deferred until after Week 4.

| Part | Work IDs | Focus | Status |
|---|---|---|---|
| **Part 1 — Watch/recovery resilience** | `W4-M2-01` | Reconnect, bounded input, duplicate suppression, daemon unavailable | **MERGED on `main`** in PR #37, `2b963a1`; owner reported Maven test/package BUILD SUCCESS |
| **Part 2 — USBGuard policy and active-user attribution** | `W4-M2-02`, `W4-M2-03` | Canonical base rule, class 08, hash/serialless, managed rule integrity/ordering, foreign-rule guard, active/remote/ambiguous sessions | **MERGED in PR #40**; owner reported Part 2 Maven test/package BUILD SUCCESS; Copilot's 2 HIGH/1 MEDIUM follow-up addressed by Part 3 patch (review pending) |
| **Part 3 — Notification/docs/handoff** | `W4-M2-04`–`W4-M2-07` | Native notification correctness, core guide, Q&A, Ubuntu checklist, IPC review with M3 | **IMPLEMENTATION PREPARED — owner Maven/independent review pending; native Ubuntu and M3 IPC ACL signoff deferred/open** |

## Owner boundary

M2 changes only `client-agent/.../{usbguard,session,notification}/**`, M2-specific tests and `docs/usbguard/**`, plus `packaging/usbguard/**` where needed. This Part 2 package does **not** modify M3-owned `sync/`, `endpoint/`, `outbox/`, `system/`, `packaging/agent-deb/`, or shared API contracts.

## Gates

- Part 2 needs a successful Windows `client-agent` Maven test run and executable-JAR build **on the applied, reviewed source**, with SHA, test counts and log evidence. Neither source inspection nor a minimal Java compile substitutes for Maven/JUnit.
- Test and review must show that policy reconciliation cannot ACK an unverified or overtaken managed Allow rule; missing or unsafe base rules must not cause silent success. Matching unmanaged device-specific Allow rules are preserved rather than silently deleted; their presence is a separately disclosed policy-audit caveat.
- Session attribution must preserve genuine local `username`/UID/session when uniquely known, while avoiding false attribution for remote-only, missing, duplicated, malformed or ambiguous sessions. Lack of a GUI does not erase a known local tty identity; GUI notification delivery is a separate Part 3 check.
- The full Ubuntu E2E release gate **remains DEFERRED**, not PASS.

## Handoff

M3: no Agent sync or `.deb` changes in Part 2; coordinate any previously existing unmanaged USBGuard rules in installed policy.  
M5: record Part 2 Windows unit coverage and unresolved Ubuntu runtime checks under the correct base/final SHA.  
M1: no REST/API/DTO changes.


## Part 3 completion and review follow-up (2026-11-01)

Part 3 delivers `WEEK4_M2_PART3_FINAL_CLOSEOUT.md`, `WEEK4_M2_CORE_OPERATIONS_GUIDE.md`, `WEEK4_M2_TECHNICAL_QA.md` and `WEEK4_M2_M3_M5_UBUNTU_HANDOFF.md`; associated Java regression covers notification filtering, UID-peer authentication, effective event target and rule-reconciliation review findings from PR #40.

Three GitHub Copilot findings in PR #40 (two HIGH, one MEDIUM) were independently read and code-fix candidates are included. They are **not yet marked independently resolved** until the owner applies, runs full Maven tests and obtains review feedback. The rule-line grammar validation intentionally fails on unsupported foreign conditions rather than claiming arbitrary USBGuard syntax is safe. M3's live socket ACL/reachability and native Ubuntu notification remain explicit follow-ups; do not mark them PASS from Windows tests.

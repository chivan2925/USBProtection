# Week 1 — Test Inventory

> **Owner:** Member 5
> **Project:** USBShield Ubuntu

Updated: 2026-10-12 (Asia/Saigon). IDs and statuses mirror the canonical
[Test Plan](../TEST_PLAN.md), T01–T38. This synchronization reviews existing evidence;
it does not rerun tests. M2 PASS is reported hardware evidence, frontend PASS is
mock/fixture verification, and M1/M3 PASS is Windows HTTP PoC.

## Executed / partially verified Week 1

| ID | Test Case | Owner | Status | Notes |
|---|---|---|---|---|
| T01 | Unknown USB Flash Disk → BLOCK | M2 | **PASS (M2 reported)** | Kingston DataTraveler 2.0, interface 08:06:50 |
| T02 | Physical USB Mouse → ALLOW | M2 | **PASS (M2 reported)** | USB Optical Mouse, class 03:01:02 |
| T03 | Mass Storage class 08 detected | M2 | **PASS (M2 reported)** | `with-interface 08:06:50` confirmed |
| T04 | Specific Allow (permanent whitelist) | M2 | **PASS (M2 reported)** | `usbguard allow-device --permanent` |
| T05 | Reconnect blocked device stays BLOCK | M2 | **PASS (M2 reported)** | 3 reconnect attempts |
| T06 | Reboot persistence of permanent allow | M2 | **PASS (M2 reported)** | Rule survives reboot |
| T07 | USBGuard event stream (watch) | M2 | **PASS (M2 reported)** | Remove→Insert→PolicyChanged→PolicyApplied |
| T08 | Active local Ubuntu user resolved | M2 | **PASS (M2 reported)** | `usbdev`, UID 1000, wayland session |
| T09 | Second user attribution | M2 | **PASS (M2 reported)** | `student01`, UID 1001, session 19 |
| T10 | Resolver ambiguity → UNKNOWN | M2 | **PASS (M2 reported, SIMULATED)** | Two equally valid candidates → UNKNOWN |
| T11 | Java USBGuard parser — massStorage=true | M2 | **PASS (M2 reported)** | DataTraveler parsed correctly |
| T12 | Sample Agent event contract complete | M2 | **PASS (M2 reported)** | endpoint+user+USB+decision in JSON |
| T13 | Admin Server skeleton boots | M1 | **PASS (Windows PoC)** | [Server verification](../member1/WEEK1_SERVER_VERIFICATION.md): standalone JAR boot + H2 File Mode |
| T14 | Agent heartbeat POST reaches Admin Server | M3+M1 | **PASS (Windows PoC)** | Actual Agent JAR enrolls and sends heartbeat; server runtime log |
| T15 | Agent event POST deserialized by server | M3+M1 | **PASS (Windows PoC, simulated event)** | Unchanged M2 sample HTTP accepted; actual Agent simulated event persisted; 4 server tests pass |
| T16 | Admin Web boots | M4 | **PASS** | React/Vite app starts |
| T17 | Login wireframe renders | M4 | **PASS** | username/password/button present |
| T19 | Endpoints list renders rows | M4 | **PASS** | hostname, status, policy version |
| T20 | Endpoint Detail renders per-endpoint info | M4 | **PASS** | |
| T21 | Per-endpoint whitelist — "Allow on this endpoint" | M4 | **PASS** | Label verified in test |
| T22 | Per-endpoint whitelist — "Revoke on this endpoint" | M4 | **PASS** | Label verified in test |
| T23 | Event History table renders | M5 | **PASS** | Mock data in Week 1 |
| T24 | Event History columns: Timestamp, Endpoint, Linux User, USB, Event, Decision | M5 | **PASS** | All columns present |
| T25 | Event History filter by decision | M5 | **PASS (MOCK)** | UI filter works on mock |
| T37 | Event field filters, local date and reset | M5 | PASS (MOCK) | eventFilters.test.mjs and browser smoke |
| T38 | Whitelist/detail fixture isolation | M4+M5 | PASS (frontend) | components.test.mjs and m4_endpoints.test.mjs |
| T28 | Admin Server H2 schema correct | M1 | **PARTIAL (Week 1)** | H2 connection/JPA context and persisted endpoint/event/device verified; full schema/constraint review remains Week 2 |

## Not executed / planned

| ID | Test Case | Owner | Status | Notes |
|---|---|---|---|---|
| T18 | Dashboard renders stat cards | M4 | NOT EXECUTED (dedicated assertion) | Browser checks route/heading; no dedicated stat-card assertion in the recorded suite |
| T26 | Multiple USB Mass Storage independent handling | M2 | **NOT EXECUTED** | Only one device available |
| T27 | Real multi-session ambiguity (live) | M2 | **NOT EXECUTED** | Simulated only |
| T35 | Physical USB Keyboard ALLOW | M2 | NOT EXECUTED | Mock keyboard UI is not hardware evidence |
| T36 | Revoke per-endpoint permission and enforce BLOCK | M1+M2+M3 | NOT EXECUTED / PLANNED WEEK 2 | Functional enforcement; distinct from T22 button-label test |
| T29 | Agent → Server live heartbeat E2E | M1+M3 | PLANNED WEEK 2 (Ubuntu) | Windows real HTTP transport covered by T14; Ubuntu deployment remains pending |
| T30 | Agent → Server live event E2E | M1+M3 | PLANNED WEEK 2 (physical USB/Ubuntu) | T15 proves HTTP with sample/simulation, not physical USB attribution |
| T31 | Per-endpoint policy sync | M1+M2+M3 | PLANNED WEEK 2 | |
| T32 | Admin Web live API integration | M1+M4 | PLANNED WEEK 2 | |
| T33 | USBGuard block on real hardware (no whitelist) | M2 | PLANNED WEEK 2 | Production integration |
| T34 | .deb package install/uninstall | M3 | PLANNED WEEK 3 | |
## Evidence and limits

- [M1/M3 verification](../member1/WEEK1_SERVER_VERIFICATION.md): 4 server tests pass;
  standalone server connects to file H2; actual Agent JAR enrolls, sends heartbeat
  and a simulated event over HTTP. Unchanged M2 sample is accepted and persisted.
- [M2 evidence review](WEEK1_M2_POC_VERIFICATION.md): no independent Ubuntu rerun
  in this synchronization. Physical keyboard (T35) remains NOT EXECUTED.
- [M4 follow-up](evidence/M4_FRONTEND_VERIFICATION.md) and
  [M5 frontend verification](evidence/M5_FRONTEND_VERIFICATION.md): UI evidence
  does not establish backend integration or physical policy enforcement.
- T28 is PARTIAL: H2/JPA connection and persistence verified, full schema/constraint
  review pending. T29/T30 retain Ubuntu/physical E2E scope beyond T14/T15 Windows PoC.
- T34 remains planned Week 3 for .deb install/uninstall. Related deployment scenarios
  include systemd auto-start, endpoint isolation and Admin login with real live data;
  these are not additional executed Week 1 tests.

The former inventory used conflicting IDs for planned Week 2/3 scenarios. This
version uses Test Plan IDs consistently; it does not convert those scenarios to PASS.

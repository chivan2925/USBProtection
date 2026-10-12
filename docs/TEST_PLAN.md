# TEST PLAN v0.1

> **Owner:** Member 5  
> **Project:** USBShield Ubuntu  
> **Scope:** Week 1 — Prove Core + Freeze Client/Server Contract  
> **Version:** v0.1

---

Updated: 2026-10-12 (Asia/Saigon).

M2 PASS means M2-reported evidence, not an independent Ubuntu runtime rerun. See [M2 verification](qa/WEEK1_M2_POC_VERIFICATION.md). Frontend PASS applies to mock/fixture checks in [frontend verification](qa/evidence/M5_FRONTEND_VERIFICATION.md). Missing execution evidence stays NOT EXECUTED. T01–T38 are the canonical Test Plan IDs used by [the inventory](qa/WEEK1_TEST_INVENTORY.md).

## 1. Week 1 Test Inventory

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
| T13 | Admin Server skeleton boots | M1 | **PASS (Windows PoC)** | [Server verification](member1/WEEK1_SERVER_VERIFICATION.md): standalone JAR boot + H2 File Mode |
| T14 | Agent heartbeat POST reaches Admin Server | M3+M1 | **PASS (Windows PoC)** | Actual Agent JAR enrolls and sends heartbeat; server runtime log |
| T15 | Agent event POST deserialized by server | M3+M1 | **PASS (Windows PoC, simulated event)** | Unchanged M2 sample HTTP accepted; actual Agent simulated event persisted; 4 server tests pass |
| T16 | Admin Web boots | M4 | **PASS** | React/Vite app starts |
| T17 | Login wireframe renders | M4 | **PASS** | username/password/button present |
| T18 | Dashboard renders stat cards | M4 | NOT EXECUTED (dedicated assertion) | Browser checks route/heading; no dedicated stat-card assertion in the recorded suite |
| T19 | Endpoints list renders rows | M4 | **PASS** | hostname, status, policy version |
| T20 | Endpoint Detail renders per-endpoint info | M4 | **PASS** | |
| T21 | Per-endpoint whitelist — "Allow on this endpoint" | M4 | **PASS** | Label verified in test |
| T22 | Per-endpoint whitelist — "Revoke on this endpoint" | M4 | **PASS** | Label verified in test |
| T23 | Event History table renders | M5 | **PASS** | Mock data in Week 1 |
| T24 | Event History columns: Timestamp, Endpoint, Linux User, USB, Event, Decision | M5 | **PASS** | All columns present |
| T25 | Event History filter by decision | M5 | **PASS (MOCK)** | UI filter works on mock |
| T26 | Multiple USB Mass Storage independent handling | M2 | **NOT EXECUTED** | Only one device available |
| T27 | Real multi-session ambiguity (live) | M2 | **NOT EXECUTED** | Simulated only |
| T35 | Physical USB Keyboard ALLOW | M2 | NOT EXECUTED | Mock keyboard UI is not hardware evidence |
| T36 | Revoke per-endpoint permission and enforce BLOCK | M1+M2+M3 | NOT EXECUTED / PLANNED WEEK 2 | Functional enforcement; distinct from T22 button-label test |
| T37 | Event field filters, local date and reset | M5 | PASS (MOCK) | eventFilters.test.mjs and browser smoke |
| T38 | Whitelist/detail fixture isolation | M4+M5 | PASS (frontend) | components.test.mjs and m4_endpoints.test.mjs |
| T28 | Admin Server H2 schema correct | M1 | **PARTIAL (Week 1)** | H2 connection/JPA context and persisted endpoint/event/device verified; full schema/constraint review remains Week 2 |
| T29 | Agent → Server live heartbeat E2E | M1+M3 | PLANNED WEEK 2 (Ubuntu) | Windows real HTTP transport covered by T14; Ubuntu deployment remains pending |
| T30 | Agent → Server live event E2E | M1+M3 | PLANNED WEEK 2 (physical USB/Ubuntu) | T15 proves HTTP with sample/simulation, not physical USB attribution |
| T31 | Per-endpoint policy sync | M1+M2+M3 | PLANNED WEEK 2 | |
| T32 | Admin Web live API integration | M1+M4 | PLANNED WEEK 2 | |
| T33 | USBGuard block on real hardware (no whitelist) | M2 | PLANNED WEEK 2 | Production integration |
| T34 | .deb package install/uninstall | M3 | PLANNED WEEK 3 | |

---

## 1.1. Allow/Revoke functional acceptance

Precondition: use a storage device with stable identity, registered endpoints A/B and an active user. Record initial policy versions and device targets on both endpoints.

| Test | Action | Expected result | Execution status |
|---|---|---|---|
| T04 local permanent allow | Allow the specific device permanently in USBGuard; reconnect | Device becomes ALLOW; the specific rule persists. This local PoC does not establish central per-endpoint sync. | PASS (M2 reported); runtime rerun pending |
| T31 central per-endpoint allow | Add the device to A's whitelist and apply its policy | Device becomes ALLOWED on A with intended identity and policy version; B's whitelist and permission remain unchanged. | PLANNED WEEK 2 |
| T36 central per-endpoint revoke | Remove the same entry from A and apply the new policy; reconnect | Device becomes BLOCKED on A; removed permission stays absent after reconnect. B remains unchanged. Record policy versions, USBGuard targets and endpoint/user/device/decision event. | NOT EXECUTED / PLANNED WEEK 2 |

T21/T22 verify UI wording only; they do not verify API calls, policy synchronization or device enforcement.

## 2. Week 1 Gate Criteria

| Gate | Owner | Result |
|---|---|---|
| Unknown Flash BLOCK | M2 | PASS (M2 reported; evidence reviewed) |
| Physical mouse ALLOW | M2 | PASS (M2 reported; evidence reviewed) |
| Keyboard ALLOW | M2 | NOT EXECUTED; physical keyboard evidence missing |
| Active Ubuntu username/session | M2 | PASS (M2 reported; evidence reviewed) |
| Sample endpoint+user+USB+decision | M2 | PASS (M2 reported; evidence reviewed) |
| Admin Server skeleton | M1 | PASS (Windows PoC; boot/H2 evidence) |
| Contract freeze v0.1 | M1 | PASS (sample/DTO/docs v0.1); production interoperability remains Week 2 |
| Agent shell starts | M3 | PASS (Windows standalone JAR) |
| Heartbeat/event POST PoC | M3+M1 | PASS (real HTTP; simulated USB event) |
| Admin Web skeleton | M4 | ✅ PASS |
| Event History mock | M5 | ✅ PASS |
| Test Plan v0.1 | M5 | ✅ This document |

---

## 3. Test Evidence Sources

| Component | Evidence |
|---|---|
| USBGuard PoC | `docs/usbguard/USBGuard_POC.md` |
| USBGuard evidence | `docs/usbguard/evidence/` |
| Sample event contract | `docs/agent-contract/sample-usb-event.json` |
| M1/M3 server and transport PoC | [Server verification](member1/WEEK1_SERVER_VERIFICATION.md), [4-test summary](member1/evidence/test-summary.txt), [server log](member1/evidence/server-runtime.log), [Agent log](member1/evidence/agent-runtime.log) |
| Admin Web tests | `frontend/tests/m4_endpoints.test.mjs` |
| Event History tests | `frontend/tests/m5_events.test.mjs` |
| M2 PoC verification | `docs/qa/WEEK1_M2_POC_VERIFICATION.md` |

---

## 4. Out of Scope — Week 1

```text
Production Agent → Admin Server live event delivery
Per-endpoint policy sync (USBGuard rules pulled from server)
Real multi-session Ubuntu ambiguity test
.deb packaging
Full Admin authentication
```

---

## 5. Planned — Week 2

```text
Full H2 schema/constraint review (Week 1 connection and persistence already verified)
Ubuntu Agent heartbeat and physical USB event reach server (Windows PoC already verified)
Admin Web reads real API data
Per-endpoint whitelist CRUD via API
Production interoperability and authenticated endpoint contract beyond v0.1 PoC
```

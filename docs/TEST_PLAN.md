# TEST PLAN v0.1

> **Owner:** Member 5  
> **Project:** USBShield Ubuntu  
> **Scope:** Week 1 — Prove Core + Freeze Client/Server Contract  
> **Version:** v0.1

---

## 1. Week 1 Test Inventory

| ID | Test Case | Owner | Status | Notes |
|---|---|---|---|---|
| T01 | Unknown USB Flash Disk → BLOCK | M2 | **PASS** | Kingston DataTraveler 2.0, interface 08:06:50 |
| T02 | USB Mouse/Keyboard → ALLOW | M2 | **PASS** | USB Optical Mouse, class 03:01:02 |
| T03 | Mass Storage class 08 detected | M2 | **PASS** | `with-interface 08:06:50` confirmed |
| T04 | Specific Allow (permanent whitelist) | M2 | **PASS** | `usbguard allow-device --permanent` |
| T05 | Reconnect blocked device stays BLOCK | M2 | **PASS** | 3 reconnect attempts |
| T06 | Reboot persistence of permanent allow | M2 | **PASS** | Rule survives reboot |
| T07 | USBGuard event stream (watch) | M2 | **PASS** | Remove→Insert→PolicyChanged→PolicyApplied |
| T08 | Active local Ubuntu user resolved | M2 | **PASS** | `usbdev`, UID 1000, wayland session |
| T09 | Second user attribution | M2 | **PASS** | `student01`, UID 1001, session 19 |
| T10 | Resolver ambiguity → UNKNOWN | M2 | **PASS (SIMULATED)** | Two equally valid candidates → UNKNOWN |
| T11 | Java USBGuard parser — massStorage=true | M2 | **PASS** | DataTraveler parsed correctly |
| T12 | Sample Agent event contract complete | M2 | **PASS** | endpoint+user+USB+decision in JSON |
| T13 | Admin Server skeleton boots | M1 | NOT EXECUTED — Week 2 | |
| T14 | Agent heartbeat POST reaches Admin Server | M3+M1 | NOT EXECUTED — Week 2 | |
| T15 | Agent event POST deserialized by server | M3+M1 | NOT EXECUTED — Week 2 | |
| T16 | Admin Web boots | M4 | **PASS** | React/Vite app starts |
| T17 | Login wireframe renders | M4 | **PASS** | username/password/button present |
| T18 | Dashboard renders stat cards | M4 | **PASS** | Mock data in Week 1 |
| T19 | Endpoints list renders rows | M4 | **PASS** | hostname, status, policy version |
| T20 | Endpoint Detail renders per-endpoint info | M4 | **PASS** | |
| T21 | Per-endpoint whitelist — "Allow on this endpoint" | M4 | **PASS** | Label verified in test |
| T22 | Per-endpoint whitelist — "Revoke on this endpoint" | M4 | **PASS** | Label verified in test |
| T23 | Event History table renders | M5 | **PASS** | Mock data in Week 1 |
| T24 | Event History columns: Timestamp, Endpoint, Linux User, USB, Event, Decision | M5 | **PASS** | All columns present |
| T25 | Event History filter by decision | M5 | **PASS (MOCK)** | UI filter works on mock |
| T26 | Multiple USB Mass Storage independent handling | M2 | **NOT EXECUTED** | Only one device available |
| T27 | Real multi-session ambiguity (live) | M2 | **NOT EXECUTED** | Simulated only |
| T28 | Admin Server H2 schema correct | M1 | PLANNED WEEK 2 | |
| T29 | Agent → Server live heartbeat E2E | M1+M3 | PLANNED WEEK 2 | |
| T30 | Agent → Server live event E2E | M1+M3 | PLANNED WEEK 2 | |
| T31 | Per-endpoint policy sync | M1+M2+M3 | PLANNED WEEK 2 | |
| T32 | Admin Web live API integration | M1+M4 | PLANNED WEEK 2 | |
| T33 | USBGuard block on real hardware (no whitelist) | M2 | PLANNED WEEK 2 | Production integration |
| T34 | .deb package install/uninstall | M3 | PLANNED WEEK 3 | |

---

## 2. Week 1 Gate Criteria

| Gate | Owner | Result |
|---|---|---|
| Unknown Flash BLOCK | M2 | ✅ PASS |
| Mouse/keyboard ALLOW | M2 | ✅ PASS |
| Active Ubuntu username/session | M2 | ✅ PASS |
| Sample endpoint+user+USB+decision | M2 | ✅ PASS |
| Admin Server skeleton | M1 | ⚠️ Not verified this week |
| Contract freeze v0.1 | M1 | ⚠️ Not verified this week |
| Agent shell starts | M3 | ⚠️ Not verified this week |
| Heartbeat/event POST PoC | M3+M1 | ⚠️ Not verified this week |
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
Admin Server H2 database verified
Agent heartbeat/event reach live server
Admin Web reads real API data
Per-endpoint whitelist CRUD via API
Contract v0.1 frozen across M1/M2/M3
```

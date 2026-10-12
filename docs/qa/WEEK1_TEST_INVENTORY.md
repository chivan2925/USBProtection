# Week 1 — Test Inventory

> **Owner:** Member 5  
> **Project:** USBShield Ubuntu

---

## Executed Week 1

| ID | Test | Owner | Result |
|---|---|---|---|
| T01 | Unknown Flash BLOCK (Kingston DataTraveler 2.0) | M2 | ✅ PASS |
| T02 | USB Mouse/Keyboard ALLOW (Optical Mouse class 03) | M2 | ✅ PASS |
| T03 | Mass Storage class `08` detected in interface string | M2 | ✅ PASS |
| T04 | Specific permanent allow (USBGuard `allow-device --permanent`) | M2 | ✅ PASS |
| T05 | Reconnect blocked device — 3 attempts all BLOCK | M2 | ✅ PASS |
| T06 | Reboot persistence — permanent allow rule survives | M2 | ✅ PASS |
| T07 | USBGuard watch event stream: Remove→Insert→PolicyChanged→PolicyApplied | M2 | ✅ PASS |
| T08 | Active local Ubuntu user resolved (usbdev, UID 1000) | M2 | ✅ PASS |
| T09 | Second user attribution (student01, UID 1001, not hard-coded) | M2 | ✅ PASS |
| T10 | Resolver ambiguity → UNKNOWN (simulated logic test) | M2 | ✅ PASS (SIMULATED) |
| T11 | Java USBGuard parser — massStorage=true for class 08 device | M2 | ✅ PASS |
| T12 | Sample Agent event JSON contract — all required fields present | M2 | ✅ PASS |
| T16 | Admin Web boots (Vite/React app starts) | M4 | ✅ PASS |
| T17 | Login wireframe renders (username/password/button) | M4 | ✅ PASS |
| T18 | Dashboard renders with stat cards | M4 | ✅ PASS |
| T19 | Endpoints list renders rows (hostname, status, policy) | M4 | ✅ PASS |
| T20 | Endpoint Detail renders per-endpoint info | M4 | ✅ PASS |
| T21 | EndpointWhitelistPanel: "Allow on this endpoint" label correct | M4 | ✅ PASS |
| T22 | EndpointWhitelistPanel: "Revoke on this endpoint" label correct | M4 | ✅ PASS |
| T23 | Event History table renders with mock data | M5 | ✅ PASS |
| T24 | Event History columns: Timestamp/Endpoint/Linux User/USB/Event/Decision | M5 | ✅ PASS |
| T25 | Event filter by decision (ALLOWED/BLOCKED/UNKNOWN) | M5 | ✅ PASS (MOCK) |

---

## Not Executed Week 1

| ID | Test | Reason | Owner |
|---|---|---|---|
| T26 | Multiple USB Mass Storage simultaneous handling | Only one device available | M2 |
| T27 | Real live multi-session ambiguity | Single-seat VM, simulated only | M2 |
| T13 | Admin Server skeleton boots | M1 backend not yet in this repo branch | M1 |
| T14 | Agent heartbeat POST reaches Admin Server | Integration not live | M1+M3 |
| T15 | Agent event POST deserialized by server | Integration not live | M1+M3 |

---

## Planned Week 2

| ID | Test | Owner |
|---|---|---|
| T29 | Admin Server H2 schema and context loads | M1 |
| T30 | Agent heartbeat/event live POST to Admin Server | M1+M3 |
| T31 | Contract v0.1 fields match between M1 DTO and M3 transport DTO | M1+M3 |
| T32 | Per-endpoint policy sync: server → Agent | M1+M2+M3 |
| T33 | Admin Web reads real API data (endpoints, events) | M1+M4 |
| T34 | Per-endpoint whitelist Allow/Revoke via real API | M1+M4 |
| T35 | USBGuard block on real hardware — Agent reports to server | M2+M3+M1 |

---

## Planned Week 3

| ID | Test | Owner |
|---|---|---|
| T40 | .deb package install on Ubuntu 24.04 | M3 |
| T41 | .deb package uninstall (prerm/postrm) | M3 |
| T42 | systemd unit enables on boot | M3 |
| T43 | Multi-endpoint scenario: two machines, different policies | All |
| T44 | Admin logs in with real credentials and views live events | M1+M4 |

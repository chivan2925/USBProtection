# Week 1 — M2 PoC Verification Report

> **Owner:** Member 5 (QA)  
> **Source evidence:** `docs/usbguard/` and `docs/agent-contract/`  
> **Rule:** M5 reads and verifies. M5 does NOT modify M2 source evidence.

---

## Verification Checklist

| # | Item | Evidence file | Result |
|---|---|---|---|
| 1 | Mass Storage class `08` detected | `evidence/04-list-devices-after-unknown-flash.txt` | ✅ PASS |
| 2 | Unknown Flash Disk BLOCK evidence exists | `evidence/05-blocked-devices.txt` | ✅ PASS |
| 3 | Physical USB mouse class `03` ALLOW | `evidence/12-hid-mouse-test.txt` | ✅ PASS |
| 4 | Reboot persistence — permanent allow survives | `evidence/10-rules-after-reboot.txt` | ✅ PASS |
| 5 | `usbguard watch` event stream captured | `evidence/13-usbguard-watch-full.txt` | ✅ PASS |
| 6 | Active user `usbdev` resolved via loginctl | `evidence/18-active-user-resolver-poc.txt` | ✅ PASS |
| 7 | Second user `student01` resolved (not hard-coded) | `evidence/19-active-user-student01.txt` | ✅ PASS |
| 8 | Ambiguity — simulated test returns `UNKNOWN` | `evidence/resolver-ambiguity.txt` | ✅ PASS (SIMULATED LOGIC TEST) |
| 9 | Sample Agent event includes endpoint+user+USB+decision | `agent-contract/sample-usb-event.json` | ✅ PASS |

---

## Detailed Findings

### 1. Mass Storage class 08 detected — PASS

From `USBGuard_POC.md` section 5:
```text
Mass Storage interface | contains class 08 | 08:06:50 | PASS
```
Device `DataTraveler 2.0` has `with-interface 08:06:50` — class `08` confirmed.

### 2. Unknown Flash Disk BLOCK — PASS

From `evidence/05-blocked-devices.txt` and POC section 6:
```text
10: block id 0951:1665 serial "C81F660E8BE8FFA14601FEF6" name "DataTraveler 2.0"
    ... with-interface 08:06:50
```
State = `block`. No manual intervention needed — falls through `ImplicitPolicyTarget=block`.

### 3. USB Mouse class 03 ALLOW — PASS

From `evidence/12-hid-mouse-test.txt` and POC section 9:
```text
10: allow id 093a:2510 ... name "USB Optical Mouse" ... with-interface 03:01:02
```
Class `03` (HID) — matches `allow with-interface none-of { 08:*:* }`.

### 4. Reboot persistence — PASS

From `evidence/10-rules-after-reboot.txt` and POC section 8:
```text
2: allow id 0951:1665 serial "C81F660E8BE8FFA14601FEF6" name "DataTraveler 2.0"
   ... with-interface 08:06:50 ...
```
Permanent rule survives reboot. Note: rule numeric ID may change but content is persistent.

### 5. USBGuard watch event stream — PASS

From `evidence/13-usbguard-watch-full.txt` and POC section 10:
```text
PresenceChanged / Remove
PresenceChanged / Insert
PolicyChanged  → target_new=block
PolicyApplied  → target_new=block
```
Sequence confirmed: remove → insert → policy evaluation → block enforced.

### 6. Active user `usbdev` resolved — PASS

From `evidence/18-active-user-resolver-poc.txt` and POC section 11:
```text
status=RESOLVED
username=usbdev
uid=1000
sessionId=2
sessionType=wayland
seat=seat0
```
Active local Wayland session resolved correctly.

### 7. Second user `student01` — PASS

From `evidence/19-active-user-student01.txt`:
```text
status=RESOLVED
username=student01
uid=1001
sessionId=19
```
Resolver is not hard-coded to `usbdev`.

### 8. Ambiguity → UNKNOWN — PASS (SIMULATED LOGIC TEST)

From `evidence/resolver-ambiguity.txt`:
```text
status=UNKNOWN
reason=ambiguous-active-local-sessions
candidate=10|student01|1001|wayland|seat1
candidate=11|student02|1002|wayland|seat2
```
Two equally valid candidates → `UNKNOWN`. Fail-safe logic verified.

> **Note:** This is a simulated logic test, not a real simultaneous multi-session integration test.
> A real two-user graphical session on a single-seat Ubuntu VM is difficult to reproduce.

### 9. Sample Agent event contract — PASS

From `docs/agent-contract/sample-usb-event.json`:

Required fields present:
- `endpointId` ✅
- `hostname` ✅
- `linuxUsername` ✅
- `linuxUid` ✅
- `sessionId` ✅
- `sessionType` ✅
- `seat` ✅
- `device` (vendorId, productId, serial, name, hash, interface) ✅
- `eventType` ✅
- `decision` ✅
- `occurredAt` ✅

---

## Summary

All 9 checklist items verified from M2 evidence files.

No M2 source file was modified during this verification.

**Week 1 M2 PoC: PASS** (with explicitly documented NOT EXECUTED and SIMULATED cases)

NOT EXECUTED (documented explicitly, not silently treated as PASS):
- Multiple USB Mass Storage simultaneous handling — only one device available
- Real live multi-session ambiguity test — simulated logic test only

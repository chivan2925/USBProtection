<<<<<<< HEAD
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
=======
﻿# Week 1 — Member 2 PoC Verification

## 1. Phạm vi xác minh

Ngày review: 2026-10-05 (Asia/Bangkok).

Phương pháp: đọc và đối chiếu evidence trong repository trên Windows. Đây là **evidence review**, không chạy lại USBGuard, reboot, USB thật hoặc session resolver trên Ubuntu. PASS dưới đây chỉ có nghĩa evidence phù hợp với expected result trong phạm vi đã ghi; không chứng minh tính xác thực của log hoặc kết quả E2E.

Không sửa nguồn evidence trong `docs/usbguard/`, `packaging/usbguard/` hoặc sample JSON. Review thực hiện bởi Codex theo yêu cầu người dùng; chưa phải xác nhận thực thi độc lập của Member 5.

## 2. Verification Checklist

| ID | Verification Item | Expected Result | Evidence review | Evidence | Note / giới hạn |
|---|---|---|---|---|---|
| M2-01 | Mass Storage class detection | Detect class 08 | PASS | [Blocked device](../usbguard/evidence/05-blocked-devices.txt) | Interface 08:06:50; review log, không chạy parser lại |
| M2-02 | Unknown flash | Flash chưa whitelist BLOCK | PASS | [Blocked device](../usbguard/evidence/05-blocked-devices.txt), [base policy](../../packaging/usbguard/rules-v0.1.conf), [PoC](../usbguard/USBGuard_POC.md) | DataTraveler 0951:1665 có target block; trạng thái chưa whitelist dựa vào mô tả/setup PoC |
| M2-03 | Physical mouse | Mouse class 03 ALLOW | PASS | [Mouse log](../usbguard/evidence/12-hid-mouse-test.txt) | Optical Mouse 093a:2510, interface 03:01:02, target allow; không suy ra keyboard đã test |
| M2-04 | Reboot persistence | Permanent allow còn sau reboot | PASS | [Post-reboot rules](../usbguard/evidence/10-rules-after-reboot.txt), [PoC](../usbguard/USBGuard_POC.md) | Rule DataTraveler còn và ghi nhận ALLOW; thao tác reboot do M2 báo cáo, không review được boot identity từ log này |
| M2-05 | USBGuard watch | Có event stream cắm/rút/policy | PASS | [Watch log](../usbguard/evidence/13-usbguard-watch-full.txt) | Có Remove, Insert, PolicyChanged, PolicyApplied với target block |
| M2-06 | Active user | Resolve đúng active local user | PASS | [Session list](../usbguard/evidence/16-loginctl-list-sessions.txt), [detail](../usbguard/evidence/17-active-session-detail.txt), [resolver](../usbguard/evidence/18-active-user-resolver-poc.txt) | Khớp usbdev, UID 1000, session 2, seat0; Active=yes, Remote=no, wayland |
| M2-07 | Second user | Resolve user thứ hai | PASS | [student01](../usbguard/evidence/19-active-user-student01.txt), [PoC](../usbguard/USBGuard_POC.md) | Output student01, UID 1001, session 19; chưa có loginctl detail riêng để đối chiếu độc lập |
| M2-08 | Ambiguous session | UNKNOWN, không đoán user | PASS | [Ambiguity](../usbguard/evidence/resolver-ambiguity.txt) | SIMULATED LOGIC TEST: hai candidate, reason=ambiguous-active-local-sessions; không phải hai session thật |
| M2-09 | Sample Agent Event | Endpoint + user + USB + decision + timestamp | PASS | [Sample JSON](../agent-contract/sample-usb-event.json), [input](../usbguard/evidence/20-combined-event-input.txt), [contract](../agent-contract/README.md), [mouse](../usbguard/evidence/12-hid-mouse-test.txt) | JSON parse được; user/session/host/time và USB khớp evidence; endpoint placeholder, sample tạo thủ công; không chứng minh POST/deserialize Server hoặc contract freeze |

## 3. Các kiểm tra đã thực hiện

- Đọc log target/interface của flash và mouse, đối chiếu base policy và báo cáo PoC.
- Đọc post-reboot rule và watch event sequence.
- Đối chiếu loginctl list/detail với resolver output cho user usbdev.
- Review output second user và ambiguity; giữ riêng phạm vi simulated logic.
- Parse sample JSON, kiểm tra trường bắt buộc, giá trị user/device/decision/time và đối chiếu combined input.
- Kiểm tra các đường dẫn Markdown trong tài liệu tồn tại.

## 4. Quy tắc kết quả

- PASS: evidence đã review hỗ trợ expected result trong phạm vi được nêu.
- FAIL: evidence đã review trái với expected result.
- NOT EXECUTED: chưa thực hiện evidence review.
- Kết quả chạy lại Ubuntu và QA VERIFIED là trạng thái riêng; không chuyển các kết quả này thành runtime PASS.

## 5. Tổng kết và việc còn lại

Evidence review: **9/9 PASS, 0/9 FAIL, 0/9 NOT EXECUTED**.

Runtime rerun trong lần review này: **0/9**. Không kết luận hệ thống E2E PASS.

Hạn chế: evidence reboot và second user chủ yếu là output/ghi nhận của M2; cần môi trường Ubuntu để xác minh độc lập. Keyboard và nhiều flash không thuộc chín mục trên, vẫn chưa có kết quả đủ để đánh dấu PASS.

Hành động tiếp theo:

1. Member 5 xác nhận evidence review và ghi tên, ngày, commit khi review hoặc chạy lại.
2. Nếu cần mức VERIFIED runtime, chạy lại trên Ubuntu, bổ sung boot identity trước/sau reboot và loginctl detail cho second user.
3. Xác minh event pipeline thật và Server POST riêng trong integration Week 2.

[Test Inventory](WEEK1_TEST_INVENTORY.md) và [QA Summary](WEEK1_QA_SUMMARY.md) vẫn phân biệt kết quả M2 reported với QA runtime verification; báo cáo này chỉ bổ sung lớp evidence review.
>>>>>>> origin/huy

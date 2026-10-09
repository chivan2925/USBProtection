<<<<<<< HEAD
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
=======
﻿# USBShield — Week 1 Test Inventory

## 1. Phạm vi và cách đọc

Inventory theo dõi test Week 1 và kế hoạch Week 2–3. ID TC-* tham chiếu [Test Plan](../TEST_PLAN.md).

Bảng dưới phản ánh evidence trong repository, không phải lần chạy lại QA. PASS (M2 reported) nghĩa là M2 đã báo cáo PASS tại [USBGuard PoC](../usbguard/USBGuard_POC.md); các kết quả M2 vẫn chờ QA runtime; chín mục đã được evidence review tại [M2 verification](WEEK1_M2_POC_VERIFICATION.md). NOT EXECUTED nghĩa là chưa có bản ghi thực thi trong inventory, không khẳng định chưa từng có ai chạy.

## 2. Week 1 Test Cases

| ID | Test Plan ID | Test | Owner | Result / phạm vi | Evidence | Verification |
|---|---|---|---|---|---|---|
| W1-01 | TC-USB-01 | Unknown flash BLOCK | M2 / M5 | PASS (M2 reported) | [Blocked device](../usbguard/evidence/05-blocked-devices.txt) | PENDING QA |
| W1-02 | TC-USB-02 | Mouse ALLOW | M2 / M5 | PASS (M2 reported, physical mouse) | [Mouse](../usbguard/evidence/12-hid-mouse-test.txt) | PENDING QA |
| W1-03 | TC-USB-03 | Keyboard ALLOW | M2 / M5 | NOT EXECUTED | Chưa có evidence bàn phím | PENDING QA |
| W1-04 | TC-USB-04 | Mass Storage class 08 | M2 / M5 | PASS (M2 reported) | [Blocked device: 08:06:50](../usbguard/evidence/05-blocked-devices.txt) | PENDING QA |
| W1-05 | TC-USER-01 | Active local user | M2 / M5 | PASS (M2 reported, resolver PoC) | [Resolver](../usbguard/evidence/18-active-user-resolver-poc.txt) | PENDING QA |
| W1-06 | TC-USER-02 | Second user | M2 / M5 | PASS (M2 reported, resolver PoC) | [student01](../usbguard/evidence/19-active-user-student01.txt) | PENDING QA |
| W1-07 | TC-USER-03 | Ambiguous session | M2 / M5 | PASS (M2 reported, SIMULATED LOGIC TEST) | [Ambiguity](../usbguard/evidence/resolver-ambiguity.txt) | PENDING QA |
| W1-08 | TC-EVENT-01 | USBGuard event stream | M2 / M5 | PASS (M2 reported) | [Watch log](../usbguard/evidence/13-usbguard-watch-full.txt) | PENDING QA |
| W1-09 | TC-EVENT-02 | Sample payload complete | M2 / M5 | PASS (M2 reported, manual PoC) | [Sample JSON](../agent-contract/sample-usb-event.json), [contract](../agent-contract/README.md) | PENDING QA |
| W1-10 | TC-USB-05 | Permanent allow | M2 / M5 | PASS (M2 reported) | [Rules](../usbguard/evidence/09-rules-after-permanent-allow.txt) | PENDING QA |
| W1-11 | TC-USB-07 | Reconnect blocked flash | M2 / M5 | PASS (M2 reported) | [PoC results and steps](../usbguard/USBGuard_POC.md) | PENDING QA |
| W1-12 | TC-USB-08 | Reboot persistence | M2 / M5 | PASS (M2 reported, local USBGuard policy) | [Rules after reboot](../usbguard/evidence/10-rules-after-reboot.txt), [PoC](../usbguard/USBGuard_POC.md) | PENDING QA |
| W1-13 | TC-USB-09 | Multiple flash devices | M2 / M5 | NOT EXECUTED — only one flash available | [Limitation](../usbguard/evidence/TODO_CAPTURE.md) | PENDING QA |
| W1-14 | TC-SERVER-01 | Backend boot | M1 | NOT EXECUTED | Chưa ghi startup log | PENDING QA |
| W1-15 | TC-WEB-01 | Frontend boot | M4 / M5 | PASS | [Execution report](evidence/M5_FRONTEND_VERIFICATION.md) | VERIFIED (frontend) |
| W1-16 | TC-WEB-02, TC-WEB-03 | Event History render, columns, mock | M5 | PASS | [Screenshot / browser](evidence/M5_FRONTEND_VERIFICATION.md) | VERIFIED (frontend) |
| W1-17 | TC-WEB-04 | Event search/filter | M5 | PASS | [Browser + unit tests](evidence/M5_FRONTEND_VERIFICATION.md) | VERIFIED (frontend) |
| W1-18 | TC-WEB-05 | Whitelist/detail fixture isolation | M4 / M5 | PASS | [15/15 tests](evidence/M5_FRONTEND_VERIFICATION.md) | VERIFIED (frontend) |
| W1-19 | TC-AGENT-01 | Heartbeat POST PoC Week 1 | M3 + M1 | BLOCKED | Backend chưa có API | Chờ M1/M3 |
| W1-20 | TC-AGENT-02 | Event POST PoC Week 1 | M3 + M1 | BLOCKED | Backend chưa có API | Chờ M1/M3 |


Tổng 20 mục: 11 PASS M2 reported, 4 PASS frontend VERIFIED, 3 NOT EXECUTED, 2 BLOCKED; 0 FAIL được ghi nhận. Số test tự động được thống kê riêng. Không phải kết luận toàn hệ thống PASS.

## 3. Test tự động hiện có

Frontend đã chạy: 15/15 test PASS, build/lint/browser PASS. Xem [execution report](evidence/M5_FRONTEND_VERIFICATION.md). Agent/Backend chưa chạy lại.

| Module | Source | Phạm vi | Lệnh chạy trong module |
|---|---|---|---|
| Frontend | [core.test.mjs](../../frontend/tests/core.test.mjs) | Format, search, sorting, pagination, unavailable API | npm test |
| Frontend | [components.test.mjs](../../frontend/tests/components.test.mjs) | Render, policy lists, endpoint isolation, table states | npm test |
| Frontend | [browser.mjs](../../frontend/tests/browser.mjs) | Browser checks theo script; đọc prerequisites trước khi chạy | npm run test:browser |
| Agent | [UsbGuardEventParserTest.java](../../client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardEventParserTest.java) | Parser | mvn test (cần Maven đã cài) |
| Agent | [UsbGuardEventListenerTest.java](../../client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardEventListenerTest.java) | Listener | Như trên |
| Agent | [LinuxActiveUserResolverTest.java](../../client-agent/src/test/java/com/group/usbshield/agent/session/LinuxActiveUserResolverTest.java) | Resolver | Như trên |
| Agent | [ClientAgentTest.java](../../client-agent/src/test/java/com/group/usbshield/agent/ClientAgentTest.java) | Theo test source | Như trên |
| Backend | [UsbProtectionApplicationTests.java](../../backend/src/test/java/USBProtection/UsbProtectionApplicationTests.java) | Application context | .\mvnw.cmd test (Windows) / ./mvnw test (Linux) |

Test phân trang Event đã cập nhật assertion theo cột USB. Có thêm tests/eventFilters.test.mjs cho bộ lọc trường, ngày local và decision không áp dụng.

## 4. Planned Week 2

Chưa có bản ghi thực thi các mục dưới đây; heartbeat/event POST phụ thuộc API Server.

| ID | Test | Tiêu chí đạt |
|---|---|---|
| W2-01 | Agent nhận USB event thật | Listener nhận đúng event và active user tại thời điểm event |
| W2-02 | Heartbeat POST | Server nhận và phản hồi đúng contract |
| W2-03 | Event POST và lưu central | Server nhận, deserialize, lưu và đọc lại đúng dữ liệu |
| W2-04 | Event History API thật | UI hiển thị event API; empty response không thành mock |
| W2-05 | Endpoint whitelist sync | Policy đúng theo từng endpoint |
| W2-06 | Allow endpoint A | Endpoint B không bị thay đổi quyền |
| W2-07 | Revoke | USB bị block lại |
| W2-08 | Reconnect sau policy sync | Policy central vẫn áp dụng đúng |

## 5. Planned Week 3

| ID | Test | Tiêu chí đạt |
|---|---|---|
| W3-01 | Reboot E2E | Policy central tồn tại và được áp dụng sau reboot |
| W3-02 | Agent service restart | Agent phục hồi theo service configuration |
| W3-03 | Server unavailable/recovery | Agent giữ event và gửi lại theo thiết kế, không mất dữ liệu |
| W3-04 | Multiple endpoints | Policy độc lập giữa endpoint |
| W3-05 | Full E2E | USB → Agent → Server → Web đúng dữ liệu |

Kế hoạch: Week 2 có 8 mục, Week 3 có 5 mục. Local reboot PoC Week 1 không thay thế reboot E2E.
>>>>>>> origin/huy

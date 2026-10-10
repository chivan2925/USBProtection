# USBShield — Week 1 Test Inventory

## 1. Phạm vi và cách đọc

Inventory theo dõi test Week 1 và kế hoạch Week 2–3. ID T* tham chiếu [Test Plan](../TEST_PLAN.md).

Bảng dưới phản ánh evidence trong repository, không phải lần chạy lại QA. PASS (M2 reported) nghĩa là M2 đã báo cáo PASS tại [USBGuard PoC](../usbguard/USBGuard_POC.md); các kết quả M2 vẫn chờ QA runtime; chín mục đã được evidence review tại [M2 verification](WEEK1_M2_POC_VERIFICATION.md). NOT EXECUTED nghĩa là chưa có bản ghi thực thi trong inventory, không khẳng định chưa từng có ai chạy.

## 2. Week 1 Test Cases

| ID | Test Plan ID | Test | Owner | Result / phạm vi | Evidence | Verification |
|---|---|---|---|---|---|---|
| W1-01 | T01 | Unknown flash BLOCK | M2 / M5 | PASS (M2 reported) | [Blocked device](../usbguard/evidence/05-blocked-devices.txt) | PENDING QA |
| W1-02 | T02 | Mouse ALLOW | M2 / M5 | PASS (M2 reported, physical mouse) | [Mouse](../usbguard/evidence/12-hid-mouse-test.txt) | PENDING QA |
| W1-03 | T35 | Keyboard ALLOW | M2 / M5 | NOT EXECUTED | Chưa có evidence bàn phím | PENDING QA |
| W1-04 | T03 | Mass Storage class 08 | M2 / M5 | PASS (M2 reported) | [Blocked device: 08:06:50](../usbguard/evidence/05-blocked-devices.txt) | PENDING QA |
| W1-05 | T08 | Active local user | M2 / M5 | PASS (M2 reported, resolver PoC) | [Resolver](../usbguard/evidence/18-active-user-resolver-poc.txt) | PENDING QA |
| W1-06 | T09 | Second user | M2 / M5 | PASS (M2 reported, resolver PoC) | [student01](../usbguard/evidence/19-active-user-student01.txt) | PENDING QA |
| W1-07 | T10 | Ambiguous session | M2 / M5 | PASS (M2 reported, SIMULATED LOGIC TEST) | [Ambiguity](../usbguard/evidence/resolver-ambiguity.txt) | PENDING QA |
| W1-08 | T07 | USBGuard event stream | M2 / M5 | PASS (M2 reported) | [Watch log](../usbguard/evidence/13-usbguard-watch-full.txt) | PENDING QA |
| W1-09 | T12 | Sample payload complete | M2 / M5 | PASS (M2 reported, manual PoC) | [Sample JSON](../agent-contract/sample-usb-event.json), [contract](../agent-contract/README.md) | PENDING QA |
| W1-10 | T04 | Permanent allow | M2 / M5 | PASS (M2 reported) | [Rules](../usbguard/evidence/09-rules-after-permanent-allow.txt) | PENDING QA |
| W1-11 | T05 | Reconnect blocked flash | M2 / M5 | PASS (M2 reported) | [PoC results and steps](../usbguard/USBGuard_POC.md) | PENDING QA |
| W1-12 | T06 | Reboot persistence | M2 / M5 | PASS (M2 reported, local USBGuard policy) | [Rules after reboot](../usbguard/evidence/10-rules-after-reboot.txt), [PoC](../usbguard/USBGuard_POC.md) | PENDING QA |
| W1-13 | T26 | Multiple flash devices | M2 / M5 | NOT EXECUTED — only one flash available | [Limitation](../usbguard/evidence/TODO_CAPTURE.md) | PENDING QA |
| W1-14 | T13 | Backend boot | M1 | NOT EXECUTED | Chưa ghi startup log | PENDING QA |
| W1-15 | T16 | Frontend boot | M4 / M5 | PASS | [Execution report](evidence/M5_FRONTEND_VERIFICATION.md) | VERIFIED (frontend) |
| W1-16 | T23, T24 | Event History render, columns, mock | M5 | PASS | [Browser smoke](evidence/M5_FRONTEND_VERIFICATION.md) | VERIFIED (frontend) |
| W1-17 | T25, T37 | Event search/filter | M5 | PASS | [Browser + unit tests](evidence/M5_FRONTEND_VERIFICATION.md) | VERIFIED (frontend) |
| W1-18 | T20, T21, T22, T38 | Whitelist/detail fixture isolation | M4 / M5 | PASS | [31/31 tests](evidence/M5_FRONTEND_VERIFICATION.md) | VERIFIED (frontend) |
| W1-19 | T14 | Heartbeat POST PoC Week 1 | M3 + M1 | NOT EXECUTED (review này) | Server API và Agent transport đã có trên main | Chờ evidence live POST |
| W1-20 | T15 | Event POST PoC Week 1 | M3 + M1 | NOT EXECUTED (review này) | Server API và Agent transport đã có trên main | Chờ evidence live POST |


Tổng 20 mục: 11 PASS M2 reported, 4 PASS frontend VERIFIED, 5 NOT EXECUTED; 0 FAIL được ghi nhận. M1/M3 đã có code trên main; runtime chưa được xác minh trong review này. Số test tự động được thống kê riêng. Không phải kết luận toàn hệ thống PASS.

## 3. Test tự động hiện có

Frontend chạy ngày 2026-10-10 (Asia/Bangkok): 31/31 test PASS, build/lint/browser PASS. Xem [execution report](evidence/M5_FRONTEND_VERIFICATION.md). Agent/Backend chưa chạy lại.

| Module | Source | Phạm vi | Lệnh chạy trong module |
|---|---|---|---|
| Frontend | [core.test.mjs](../../frontend/tests/core.test.mjs) | Format, search, sorting, pagination, unavailable API | npm test |
| Frontend | [components.test.mjs](../../frontend/tests/components.test.mjs) | Render, policy lists, endpoint isolation, table states | npm test |
| Frontend | [eventFilters.test.mjs](../../frontend/tests/eventFilters.test.mjs) | Field filters, reset, local date, no-decision | npm test |
| Frontend | [m4_endpoints.test.mjs](../../frontend/tests/m4_endpoints.test.mjs) | Endpoints, detail, per-endpoint whitelist | npm test |
| Frontend | [m5_events.test.mjs](../../frontend/tests/m5_events.test.mjs) | Shared EventTable and Events page, decisions, links, loading/empty | npm test |
| Frontend | [browser.mjs](../../frontend/tests/browser.mjs) | Browser checks theo script; đọc prerequisites trước khi chạy | npm run test:browser |
| Agent | [UsbGuardEventParserTest.java](../../client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardEventParserTest.java) | Parser | mvn test (cần Maven đã cài) |
| Agent | [UsbGuardEventListenerTest.java](../../client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardEventListenerTest.java) | Listener | Như trên |
| Agent | [LinuxActiveUserResolverTest.java](../../client-agent/src/test/java/com/group/usbshield/agent/session/LinuxActiveUserResolverTest.java) | Resolver | Như trên |
| Agent | [ClientAgentTest.java](../../client-agent/src/test/java/com/group/usbshield/agent/ClientAgentTest.java) | Theo test source | Như trên |
| Backend | [UsbProtectionApplicationTests.java](../../backend/src/test/java/USBProtection/UsbProtectionApplicationTests.java) | Application context | .\mvnw.cmd test (Windows) / ./mvnw test (Linux) |
| Admin Server | [AgentEventContractTest.java](../../admin-server/src/test/java/com/group/usbshield/server/agentapi/AgentEventContractTest.java) | Event contract deserialization (source present; not rerun) | mvn test |

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
| W2-07 | Revoke (T36) | USB bị BLOCK trên A sau policy apply và reconnect; quyền B không đổi; lưu policy version và event evidence |
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

## 6. Traceability notes

W1-* identifies inventory rows; T* identifies canonical Test Plan cases. A row may cover several cases. T11 (parser evidence) and T27 (real multi-session ambiguity) are tracked separately in the Test Plan. Simulated ambiguity W1-07/T10 does not establish T27 PASS. T36 functional revoke remains NOT EXECUTED / PLANNED WEEK 2; UI label tests T21/T22 cannot establish enforcement PASS.

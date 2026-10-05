# USBShield — Week 1 QA Summary

## 1. Overall status: M5 frontend verified; integration pending

Cập nhật 2026-10-05. M5 Event History mock và test tự động đã được xác minh trên Windows. M2 PoC đã được review evidence, không chạy lại Ubuntu/hardware. Contract freeze và Agent/Server POST cần M1/M3 hoàn thành.

## 2. Week 1 Gate Summary

| Gate item | Owner | Evidence | Result | Blocker / next action |
|---|---|---|---|---|
| Flash BLOCK / class 08 | M2 | [M2 verification](WEEK1_M2_POC_VERIFICATION.md) | PASS evidence review | Runtime rerun chưa thực hiện |
| Mouse ALLOW | M2 | [M2 verification](WEEK1_M2_POC_VERIFICATION.md) | PASS evidence review | Không suy ra keyboard |
| Keyboard ALLOW | M2 | Chưa có evidence riêng | NOT EXECUTED | M2 test keyboard thật |
| Active user / second user | M2 | [M2 verification](WEEK1_M2_POC_VERIFICATION.md) | PASS evidence review | Attribution trong pipeline thật cần integration |
| Ambiguous session | M2 | [M2 verification](WEEK1_M2_POC_VERIFICATION.md) | PASS evidence review — simulated | Không phải hai phiên thật |
| Sample event complete | M2 | [M2 verification](WEEK1_M2_POC_VERIFICATION.md) | PASS evidence review — manual PoC | Không chứng minh Server deserialize |
| Contract freeze | M1 + M3 | [Handoff](../HANDOFF_MEMBER1_MEMBER3.md) | Chưa xác nhận | M1/M3 freeze protocol |
| Agent heartbeat/event POST | M1 + M3 | Backend chưa có API | BLOCKED | Triển khai PoC endpoints Week 1 |
| Frontend / Event History | M4 + M5 | [Execution report](evidence/M5_FRONTEND_VERIFICATION.md) | PASS frontend | API thật chờ Week 2 |
| Backend boot | M1 | Chưa có startup log trong QA | NOT EXECUTED | M1 chạy và cung cấp evidence |

## 3. M5 frontend checklist

- [x] Event History mock renders; có Timestamp, Endpoint, Linux User, USB, Event, Decision.
- [x] Có LAB-PC-01 / student01 / Kingston / CONNECTED / BLOCKED và LAB-PC-02 / employee07 / SanDisk / CONNECTED / ALLOWED.
- [x] Có bộ lọc Endpoint, Linux Username, Device, Decision, Date; một bộ lọc duy nhất và reset.
- [x] Có type riêng tại frontend/src/types/event.ts; services/models.ts re-export để giữ tương thích. Đây là view model, chưa tuyên bố là contract M1 đã freeze.
- [x] Test render cột/mock, empty API response, sorting/pagination và filter logic.
- [x] npm test: 15/15 PASS. npm run build, npm run lint: PASS.
- [x] Browser smoke PASS: render, decision filter, unmatched endpoint, reset, responsive và runtime/console checks.
- [x] Có [ảnh Event History](evidence/events-desktop.png).
- [ ] Live backend event API và full Agent → Server → Web integration.

Tên file tương đương theo cấu trúc hiện có: pages/Events.tsx, components/EventTable.tsx, components/EventFilters.tsx, types/event.ts, mocks/events.ts; test nằm trong tests/components.test.mjs và tests/eventFilters.test.mjs. Không tạo wrapper rỗng chỉ để khớp tên checklist.

## 4. Kết quả theo Inventory

Đơn vị: 20 mục W1-* trong [Inventory](WEEK1_TEST_INVENTORY.md), không phải số gate/test tự động.

| Trạng thái | Số mục |
|---|---:|
| PASS M2 reported (chín mục PoC đã evidence review, runtime còn pending) | 11 |
| PASS frontend VERIFIED | 4 |
| NOT EXECUTED | 3 |
| BLOCKED (heartbeat/event POST) | 2 |
| FAIL ghi nhận trong bảng W1 | 0 |
| Tổng | 20 |

Báo cáo [M2 verification](WEEK1_M2_POC_VERIFICATION.md) có 9/9 PASS evidence review; không cộng thêm chín mục vào tổng W1 vì trùng coverage. 0 FAIL không chứng minh toàn hệ thống không có lỗi.

## 5. Remaining actions

1. M5 xác nhận báo cáo evidence review M2 nếu dùng làm báo cáo chính thức; ghi tên/commit khi bàn giao.
2. M1/M3 triển khai và freeze heartbeat/event contract, cung cấp boot/POST logs.
3. M2 cung cấp keyboard evidence; multi-flash vẫn thiếu phần cứng. Runtime rerun cần Ubuntu.
4. Week 2 xác minh pipeline event thật, API, central persistence và policy theo endpoint.

Nguồn M2 trong docs/usbguard/, packaging/usbguard/ và sample JSON không bị chỉnh sửa trong công việc này.

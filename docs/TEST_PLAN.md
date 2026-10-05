# USBShield — Test Plan v0.1

## 1. Mục tiêu và phạm vi

Kế hoạch kiểm thử PoC Week 1 và integration Week 2–3: kiểm soát USB Mass Storage, nhận diện active Linux user, thu thập sự kiện, giao tiếp Agent/Server và giao diện quản trị theo endpoint.

Kết quả được theo dõi tại [Test Inventory](qa/WEEK1_TEST_INVENTORY.md), tổng kết tại [QA Summary](qa/WEEK1_QA_SUMMARY.md). Tài liệu này mô tả cách kiểm thử, không thay thế báo cáo thực thi.

## 2. Môi trường và evidence

- USBGuard/active user: Ubuntu có USBGuard, USB flash class 08, chuột/bàn phím USB và hai tài khoản local. Ghi phiên bản OS, USBGuard, policy và thiết bị trước khi chạy; tham khảo [môi trường PoC](usbguard/evidence/00-environment.txt).
- Frontend: Node/npm phù hợp với `frontend/package.json`; chạy trong thư mục `frontend`.
- Agent/Backend: Java/Maven phù hợp với pom.xml của từng module. Kiểm tra cấu hình trước khi khởi động.
- Integration: cần API Server thực sự được triển khai và hai endpoint đã đăng ký; mock UI không chứng minh integration thành công.
- Mỗi lần chạy lưu ngày, người chạy, commit, lệnh/bước thực hiện, expected/actual, kết quả và đường dẫn log hoặc ảnh. Evidence cũ phải ghi là kết quả được báo cáo, không xem là lần QA chạy lại.

## 3. Test cases

### 3.1. USBGuard — M2 thực hiện, M5 xác minh

Chuẩn bị policy mặc định chặn Mass Storage chưa whitelist, cho phép HID; ghi lại policy ban đầu. Lưu log `list-devices`, `list-rules` và event stream vào `docs/usbguard/evidence/`.

| ID | Tuần | Bước thực hiện | Tiêu chí đạt |
|---|---|---|---|
| TC-USB-01 | 1 | Cắm flash chưa whitelist; xem list-devices | Đúng thiết bị có target block |
| TC-USB-02 | 1 | Cắm chuột USB; xem interface và target | Chuột class 03 được allow và sử dụng được |
| TC-USB-03 | 1 | Cắm bàn phím USB; thử nhập | Bàn phím được allow và nhập được; không suy ra từ test chuột |
| TC-USB-04 | 1 | Kiểm tra interface của flash | Có interface class 08 |
| TC-USB-05 | 1 | Thêm permanent allow cho flash cụ thể | Flash được allow; rule định danh đúng thiết bị |
| TC-USB-06 | 2 | Xóa quyền/rule allow; áp dụng policy | Flash bị block lại |
| TC-USB-07 | 1 | Tháo và cắm flash chưa whitelist ba lần | Cả ba lần đều block |
| TC-USB-08 | 1 | Permanent allow, reboot, cắm lại flash | Rule còn tồn tại và flash được allow |
| TC-USB-09 | 1 | Cắm hai flash và kiểm tra riêng từng thiết bị | Quyết định đúng theo policy của từng flash; thiếu thiết bị thì NOT EXECUTED |

### 3.2. Active user và event — M2/M3 thực hiện, M5 xác minh

| ID | Tuần | Bước thực hiện | Tiêu chí đạt |
|---|---|---|---|
| TC-USER-01 | 1 | Chạy resolver trong phiên local active; đối chiếu loginctl | Username, UID, session, seat khớp phiên active |
| TC-USER-02 | 1 | Đăng nhập tài khoản thứ hai; chạy resolver | Trả đúng user thứ hai, không hard-code |
| TC-USER-03 | 1 | Mô phỏng hai candidate hợp lệ ngang nhau | UNKNOWN kèm lý do ambiguity; ghi rõ simulated logic test |
| TC-EVENT-01 | 1 | Chạy usbguard watch; cắm/rút USB | Log có sự kiện insert/remove và thay đổi policy liên quan |
| TC-EVENT-02 | 1 | Review sample JSON với contract | Có endpoint, active user/session, USB, eventType, decision, occurredAt; chỉ xác minh sample PoC |
| TC-EVENT-03 | 2 | Cắm USB và đối chiếu payload Agent gửi | Payload được tạo từ event thật và user tại thời điểm event |

Evidence tham khảo: [USBGuard PoC](usbguard/USBGuard_POC.md), [event contract](agent-contract/README.md), [sample JSON](agent-contract/sample-usb-event.json).

### 3.3. Agent/Server — M3 + M1

| ID | Tuần | Bước thực hiện | Tiêu chí đạt |
|---|---|---|---|
| TC-SERVER-01 | 1 | Khởi động backend; lưu startup log | Server khởi động, không có lỗi fatal; không đồng nghĩa API đã có |
| TC-AGENT-01 | 1 | Gửi heartbeat với endpoint/token hợp lệ | Server nhận request và phản hồi đúng contract đã thống nhất |
| TC-AGENT-02 | 1 | Gửi sample event; đọc lại dữ liệu lưu | Server deserialize, lưu và trả đúng trường dữ liệu |
| TC-POLICY-01 | 2 | Allow USB cho endpoint A; kiểm tra A và B | A được allow, B vẫn áp dụng policy riêng |
| TC-E2E-01 | 3 | USB → Agent → Server → Web | Event thật hiển thị đúng endpoint, user, USB, event và decision |

Nếu API chưa triển khai, ghi BLOCKED cùng dependency cụ thể; chưa có xác nhận freeze contract thì không ghi đã freeze.

### 3.4. Admin Web — M4/M5

| ID | Tuần | Bước thực hiện | Tiêu chí đạt |
|---|---|---|---|
| TC-WEB-01 | 1 | npm run dev; mở URL do Vite cung cấp | Giao diện mở được, không có lỗi render |
| TC-WEB-02 | 1 | Mở #/events khi backend chưa khả dụng | Hiển thị mock và nhãn Dữ liệu mock |
| TC-WEB-03 | 1 | Kiểm tra header và mock rows | Đúng thứ tự Timestamp, Endpoint, Linux User, USB, Event, Decision; có ALLOW/BLOCK và dấu — khi không có decision |
| TC-WEB-04 | 1 | Lọc Endpoint/Linux Username/Device/Decision/Date; nhập giá trị không khớp; reset | Kết quả đúng điều kiện, có trạng thái không tìm thấy, reset khôi phục danh sách |
| TC-WEB-05 | 1 | Dùng fixture nhiều endpoint xem whitelist và endpoint detail | Whitelist chỉ chứa allowed; detail không lẫn thiết bị endpoint khác; chưa chứng minh backend enforcement |
| TC-WEB-06 | 2 | API trả event thật và danh sách rỗng | Render dữ liệu API; danh sách rỗng không bị thay bằng mock |

## 4. Test tự động

Danh sách source và lệnh chạy nằm trong Inventory. Chỉ ghi PASS khi có log lần chạy; việc tồn tại test source không chứng minh test pass. Build/typecheck/lint cũng không thay thế kiểm thử trên trình duyệt hoặc USB thật.

## 5. Quy ước báo cáo

- Result: PASS / FAIL / NOT EXECUTED / BLOCKED.
- Planned week: 1 / 2 / 3, tách khỏi Result.
- Verification: REPORTED — kết quả member báo cáo; PENDING QA — chờ QA xác minh; VERIFIED — QA đã xác minh và có evidence.
- PASS simulated, PASS PoC và PASS E2E phải ghi rõ phạm vi; không dùng kết quả một phạm vi để kết luận phạm vi khác.
- Tổng kết phải ghi mẫu số và nguồn kết quả; không đánh dấu PASS nếu chưa có bằng chứng.

## 6. M5 execution update

2026-10-05: frontend 15/15 tests, build/lint/browser PASS; [execution report](qa/evidence/M5_FRONTEND_VERIFICATION.md). Heartbeat/event POST là PoC Week 1, hiện BLOCKED do API. EventFilters có Endpoint, Linux Username, Device, Decision, Date và reset.


# Week 4 — rà soát Event History của M5

Ngày rà soát: **2026-10-28, Asia/Bangkok (+07:00)**. Source baseline: **`2013ce726ef8cc960b30720435be23e8ccef1903`**. Work ID: **W4-M5-03**, hỗ trợ **W4-M5-02**.

**Kết luận rà soát source:** chưa phát hiện lỗi P0 mới trong phạm vi Event History được đọc. Các trường attribution, thời gian nguồn, USB không serial, bộ lọc và phân trang có implementation cùng assertions hiện hữu phù hợp. Đây là kết quả đọc code/test; chưa thay thế kết quả chạy regression Week 4 hay xác nhận của M4 trên release candidate.

**Trạng thái W4-M5-03: TECHNICAL REVIEW/REGRESSION PASS / CHỜ HANDOFF M4 TRÊN CANDIDATE WEEK 4.** Không sửa application source, shared frontend hoặc tests. Tác vụ đọc source không chạy test; sau đó tác vụ QA chính đã chạy mới **96 frontend tests, 26 server tests, fixture/management/Events và packaged-JAR browser PASS**. [Evidence Week 4](week4-evidence/2026-10-28/windows-regression/README.md), [inventory](WEEK4_TEST_INVENTORY.md) và [release validation](WEEK4_RELEASE_VALIDATION.md) ghi kết quả thực thi và quyết định hiện tại.

## Căn cứ và baseline đã chấp nhận

- Kế hoạch nguồn: `D:\Libarary\Downloads\USBShield_Week4_Work_Plan_M1_M2_M3_M4_M5.md`, đọc UTF-8; yêu cầu W4-M5-03 và W4-M4-05. Tài liệu này là yêu cầu đầu ra, không phải evidence hoàn thành.
- [Week 3 final owner closeout](WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md) đã chốt **`PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`**, H02 **DONE**, H05 **CLOSED**. Rà soát Week 4 không mở lại các gate đã chấp nhận.
- [M4 Week 3 H02 acknowledgment](../admin-web/WEEK3_UI_UX_HARDENING.md) là xác nhận lịch sử có thật. Không suy ra từ đó M4 đã review hoặc ký nhận release candidate Week 4.
- [API canonical](../API.md) và [event view-model mapping](EVENT_VIEW_MODEL_MAPPING.md) xác định ranh giới DTO. Các số test/ngày trong mapping report cũ giữ nguyên giá trị lịch sử; không dùng chúng làm kết quả chạy mới.

## Phạm vi và coverage hiện hữu

Các hàng dưới đây mô tả source và assertions đã đọc, **không gán PASS thực thi mới** chỉ vì test tồn tại.

| Nội dung cần xác minh | Behavior trong source | Assertions hiện hữu có ý nghĩa |
|---|---|---|
| Endpoint và A/B attribution | Mapper giữ `endpointId` cùng hostname của event. Table liên kết bằng ID đã encode, hiển thị hostname và ID; không lấy hostname làm định danh. Live Events không lấy attribution từ endpoint đang chọn. | `m5_event_api`: các event khác endpoint nhưng trùng hostname và lịch sử user switch vẫn độc lập; `m5_events`: link encoded/ID hiển thị; `browser-live`: B không hiện lịch sử user của A, cùng USB hash vẫn lọc đúng A/B. |
| Linux user | `linuxUsername` của từng event trở thành `username`; null/blank thành `UNKNOWN`. UID/session/seat được giữ trong model, không thay bằng tài khoản Admin hay user hiện tại của endpoint. | `m5_event_api`: giữ UID/session gốc, UNKNOWN và user switch; `browser-live`: user A/B, substring không phân biệt hoa thường và `UnKn` khớp UNKNOWN. Đây là dữ liệu HTTP tổng hợp, chưa chạy Linux session resolver. |
| USB và no-serial | Giữ database device ID, tên, VID/PID, serial, hash và `interfaceClass`. Serial rỗng/null không bị tự sinh. Table hiện tên/ID và thông tin identity trong tooltip; device thiếu được thể hiện bằng `—`. | `m5_event_api`: serial `''`/null/undefined giữ hash/ID, không sửa input; `browser-live`: USB no-serial dùng chung có ba event, đúng user và endpoint khi kết hợp filter. |
| Event và decision | DTO chỉ nhận CONNECTED/DISCONNECTED; ALLOWED/BLOCKED ánh xạ riêng với event type. Defensive read cho UNKNOWN giữ trạng thái unknown; table dùng badge UNKNOWN, không biến thành BLOCK. | `m5_event_api`: BLOCKED độc lập với UNKNOWN user, enum sai bị từ chối; `m5_events`: ALLOW/BLOCK/UNKNOWN và các cột bắt buộc. Live filter giới hạn ALLOW/BLOCK đúng API, không hứa hỗ trợ UNKNOWN query. |
| Source time | `occurredAt` được giữ nguyên chuỗi làm `timestamp`; `receivedAt` là trường riêng, không thay thế thời gian nguồn. Table giữ giá trị gốc trong `<time dateTime>` và định dạng chữ theo timezone trình duyệt. | `m5_event_api`: occurredAt khác receivedAt, offset/nanosecond giữ nguyên, ngày/giờ sai bị từ chối; `m5_events`: datetime gốc được giữ; `browser-live`: microsecond timestamp và ranh giới cuối ngày được kiểm tra. |
| Filter và date | Live gửi `endpoint`, `username`, `device`, event type/decision và date bounds đến server. Endpoint/USB tìm tên hoặc ID; username tìm substring; kết hợp filter trước count/paging ở backend. Ngày theo local calendar, gồm ngày đầu/cuối; `to` là nanosecond cuối trước midnight kế tiếp. | `m5_live_api`: query encoding, whitespace, Bangkok/DST và invalid filters; `eventFilters`: khoảng ngày, UNKNOWN/none, reset/local demo matching; `EventSearchIntegrationTest`: wildcard literal, AND filters, legacy hostname, filter trước phân trang; `browser-live`: name/ID/partial username/date và empty/reset thật qua HTTP/H2. |
| Paging và order | Live dùng metadata/order của server, 20 rows/page; không sort/filter/paginate lại một trang bằng logic demo. Thay filter reset trang 0. Có nhánh đọc lại trang cuối hợp lệ khi history co lại. Demo dùng toàn bộ fixture, sort mới nhất trước và local pagination 10 rows. | `m5_event_api`: totals/order, trang cuối thiếu 1 phần, empty/out-of-range và metadata mâu thuẫn; `m5_events`: giữ đủ rows/order/global totals trên trang cuối; `browser-live`: 20+5 rows và filter/reset từ trang 2. Nhánh history co lại chưa có assertion browser trực tiếp trong harness đã đọc. |
| Payload lỗi và unavailable | Sai ID/enum/timestamp/device/page metadata làm fail cả read; không bỏ lặng event sai hoặc tạo dòng thay thế. Loading/error trong LiveEvents chỉ truyền danh sách rỗng cho table. | `m5_event_api`: malformed item/page và excess final-page rows; `m5_live_api`: 400/401/403/500, network, HTML, incomplete payload, cancellation; `m5_events`: loading/unavailable/error/empty không xuất hiện fixture. |
| Shared auth/session | Live Events dùng shared `request()`, cookie cùng origin, JSON validation, `cache: no-store`, timeout và AbortSignal. AuthProvider nhận event session-expired của HTTP 401, hủy session check cũ và về Login. CSRF/login/logout dùng shared adapter. | `m4_auth` và `m5_live_api`: cookie/CSRF rotation, HTML/timeout/cancellation/401; `browser-live`: unauthenticated redirect, login sai/đúng, reload, logout, không lưu credential ở browser storage; shared management harness có server restart/session expiry. |

Source/test chính:

- [Events.tsx](../../frontend/src/pages/Events.tsx), [EventTable.tsx](../../frontend/src/components/event/EventTable.tsx), [EventFilters.tsx](../../frontend/src/components/event/EventFilters.tsx), [event.ts](../../frontend/src/types/event.ts), [eventFilters.ts](../../frontend/src/utils/eventFilters.ts), [eventMapper.ts](../../frontend/src/services/mappers/eventMapper.ts).
- [m5_events.test.mjs](../../frontend/tests/m5_events.test.mjs), [m5_event_api.test.mjs](../../frontend/tests/m5_event_api.test.mjs), [m5_live_api.test.mjs](../../frontend/tests/m5_live_api.test.mjs), [eventFilters.test.mjs](../../frontend/tests/eventFilters.test.mjs), [m4_auth.test.mjs](../../frontend/tests/m4_auth.test.mjs), [browser-live.mjs](../../frontend/tests/browser-live.mjs), [EventSearchIntegrationTest.java](../../admin-server/src/test/java/com/group/usbshield/server/EventSearchIntegrationTest.java).

## Dữ liệu production, demo và evidence tổng hợp

[App.tsx](../../frontend/src/App.tsx) chỉ chọn fixture khi URL có **`?demo=1`**. Live `/events` dùng `LiveEvents` và API thật; không gọi `getDemoSnapshot()` khi server lỗi hoặc trả rỗng. [Demo adapter](../../frontend/src/services/demo.ts) là đường explicit chỉ đọc. Demo có nhãn **“Chế độ demo · Dữ liệu mock”**, **“Demo · Read-only”** và **“Dữ liệu mock · Demo”**; [fixture browser harness](../../frontend/tests/browser.mjs) có assertion demo không phát HTTP/mutation request.

Events được seed bằng HTTP trong test là **dữ liệu tổng hợp đã lưu vào backend thật trong H2 cô lập**. Harness dùng tên `Windows-HTTP-QA`, `Windows HTTP QA storage` và các user `qa-*`; logs và report phải ghi rõ nguồn tổng hợp. UI gọi chúng là **“Dữ liệu máy chủ”**, vì đó là dữ liệu từ server; DTO hiện không có trường provenance để tự phân biệt event USB vật lý với event HTTP do QA tạo. Vì vậy screenshots/logs từ harness cần caption/index ghi **synthetic HTTP / Windows**, và không được dùng làm evidence USBGuard, Linux attribution thực tế hoặc GUI Ubuntu. Đây là giới hạn bằng chứng, không phải phát hiện production tự fallback mock.

## Những phần còn phải đóng cho Week 4

| Mục | Trạng thái và bước tiếp theo |
|---|---|
| Regression trên baseline Week 4 | **PASS** mới: captures 10/11/13/14/15/16/18/19 ở [evidence index](week4-evidence/2026-10-28/windows-regression/README.md). Events có 28 synthetic HTTP records; capture 19 chạy UI/API cùng JAR, giữ 31 dòng assertion gốc. Các mức unit/mock, HTTP/browser và Ubuntu vẫn tách riêng. |
| M4 review shared frontend | Chờ record Week 4 về SHA đã review, adapter `api.ts`/`http.ts`, AuthProvider/App/useResource/DataTable, harness/config liên quan và accepted/open items. Review này do M5/Codex chuẩn bị; không ký thay M4. Week 3 H02 vẫn DONE. |
| History co lại khi đang ở trang cuối | Source có recovery; mapper/component đã có boundary tests, nhưng browser harness hiện không tạo deletion/shrink để thực thi nhánh recovery. Không ghi đây là browser PASS. Nếu release thay đổi logic paging, bổ sung một scenario shrink có hành vi quan sát được; chưa có failure tái hiện để coi là blocker code. |
| Attribution/enforcement trên Ubuntu | **NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4**: xác minh endpoint + user/session thực, USB no-serial thực, USBGuard enforcement và notification. Theo [Ubuntu acceptance](WEEK4_UBUNTU_ACCEPTANCE.md); không suy ra từ HTTP-seeded input. |

Evidence Windows mới đã đạt; W4-M5-03 còn chờ handoff shared frontend Week 4 có nguồn xác nhận từ M4 trên candidate cuối. Không thêm feature, không sửa shared files hoặc tạo test lặp lại implementation chỉ để tăng số lượng; không phát hiện failure ứng dụng mới cần sửa.

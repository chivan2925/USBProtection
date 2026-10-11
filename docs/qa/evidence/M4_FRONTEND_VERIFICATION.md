# Member 4 — Admin Web Week 1 verification

Ngày kiểm tra: 2026-10-11 (Asia/Saigon). Kết luận: **PASS Week 1 — UI skeleton/wireframe demo** theo mục M4 của blueprint `USBShield_Project_Blueprint_Updated_AdminCentral_PerEndpoint (1).md`.

## Kết quả đối chiếu

| Yêu cầu Week 1 | Kết quả |
|---|---|
| React/Vite/TypeScript skeleton | PASS — production build thành công. |
| Login wireframe | PASS — có form và liên kết xem giao diện; chưa tạo session quản trị. |
| Dashboard | PASS — số liệu demo có nhãn mock; protection/service thực tế vẫn chưa xác định. |
| Endpoints | PASS — hai máy, trạng thái, user, policy version, tìm kiếm/lọc và liên kết chi tiết. |
| Endpoint Detail/Whitelist wireframe | PASS — panel được tích hợp vào màn đang sử dụng; chỉ hiển thị whitelist của endpoint được chọn. |

## Những vấn đề đã kiểm tra và xử lý

- Hai file `frontend/src/types/event.ts` và `frontend/src/mocks/events.ts` **đã không còn merge markers ở thời điểm bắt đầu kiểm tra**. Build ban đầu và 31 test đã đạt; không phát sinh sửa merge conflict trong đợt này.
- Tích hợp `EndpointWhitelistPanel` vào `pages/Endpoints.tsx`, loại bỏ liên kết từ panel USB sang trang whitelist tổng hợp để giữ ngữ cảnh endpoint.
- Nối fixture endpoint (`endpointId`, `lastSeen`) sang view model đang dùng (`id`, `lastSeenAt`) trong `mocks/adminPreview.ts`.
- Dashboard, Endpoints và Endpoint Detail hiển thị mock khi adapter báo `BackendUnavailableError`. M4 không thay phản hồi thật rỗng, loading hoặc lỗi tải bằng mock.
- Demo cùng Kingston trên hai máy: LAB-PC-01 có whitelist/allowed; LAB-PC-02 có blocked và whitelist trống. Event mock có endpoint IDs để mở chi tiết và lọc lịch sử theo máy.
- Giữ adapter API chưa tích hợp, không gửi HTTP giả. Allow/Revoke và xác thực thật chưa bật; các nút không có handler cũng bị vô hiệu hóa.
- Cập nhật README frontend với cách demo, trạng thái API và số lượng test hiện tại.

## Kiểm chứng

Chạy tại `frontend/`, Windows, Node.js v24.21.0, Chromium cài sẵn:

| Lệnh | Kết quả |
|---|---|
| `npm run build` | PASS — TypeScript và production bundle. |
| `npm test` | PASS — 34 tests, 0 failures. |
| `npm run lint` | PASS. |
| `npm run test:browser` | PASS — điều hướng, endpoint detail links, whitelist isolation, refresh, filters/reset, 404, login wireframe, mobile menu, responsive overflow, không lỗi runtime/console hoặc Fetch/XHR. |

Hai lần chạy browser trong sandbox timeout tại `Runtime.enable`; chạy ngoài sandbox sau đó đạt. Đây là hạn chế môi trường chạy trình duyệt trong sandbox, không phải kết quả PASS từ các lần timeout.

Ảnh desktop/mobile đã được xem để kiểm tra bố cục. Browser test tạo ảnh trong `frontend/.artifacts/` (gitignored), gồm `endpoint-detail-desktop.png` và `endpoint-detail-mobile.png`; tái tạo bằng lệnh browser test sau build.

## Luồng demo

1. Chạy `npm run dev -- --host 127.0.0.1`.
2. Mở `#/login`, chọn **Xem giao diện quản lý**.
3. Xem Dashboard có nhãn **Dữ liệu mock · Demo Week 1**.
4. Mở Endpoints → LAB-PC-01: Kingston được phép và có trong whitelist.
5. Mở LAB-PC-02: cùng Kingston bị chặn và whitelist trống.
6. Xem Event Logs và liên kết về chi tiết endpoint.

## Giới hạn đánh giá

PASS chỉ áp dụng phạm vi M4 Week 1. Chưa đánh giá PASS cho tích hợp Admin Server/Agent, session/cookie, thay đổi policy thật, policy sync hay enforcement USB trên Ubuntu. Login integration, API mapping và Allow/Revoke thật thuộc công việc tiếp theo theo blueprint.

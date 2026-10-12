# USBShield — Admin Web

React + Vite + TypeScript. Kết quả rà soát Member 4 ngày 2026-10-11 (Asia/Saigon): **PASS cho UI skeleton/wireframe Week 1**. Xem [báo cáo kiểm tra](../docs/qa/evidence/M4_FRONTEND_VERIFICATION.md).

## Chạy và demo

Dùng Node.js 22.20+ và npm; lần kiểm tra này dùng Node.js 24.21.0. Trong thư mục `frontend/`:

```sh
npm ci
npm run dev -- --host 127.0.0.1
```

Mở URL Vite hiển thị (mặc định `http://127.0.0.1:5173/`). Chọn **Mở demo**, hoặc dùng `http://127.0.0.1:5173/?demo=1#/dashboard`. Màn Login có liên kết **Xem demo Admin Web**.

- Demo có nhãn **Chế độ demo · Dữ liệu mock** trên mọi màn quản lý.
- Dashboard có thống kê; Endpoints có LAB-PC-01 và LAB-PC-02.
- Mở LAB-PC-01: whitelist có Kingston; cùng USB bị chặn trên LAB-PC-02 và whitelist máy này rỗng.
- Endpoint Detail chỉ hiển thị thiết bị, whitelist và sự kiện của máy đang chọn.
- Event History có Endpoint, Linux User, USB, Event, Decision và bộ lọc.
- Refresh tải lại fixture; **Thoát demo** quay về trạng thái chờ API.
- Demo chỉ đọc dữ liệu mẫu, không tạo phiên đăng nhập, token hoặc thay đổi quyền USB trên máy thật.

## Kiểm tra

```sh
npm run build
npm run lint
npm test
npm run test:browser
```

- `build`: TypeScript và production bundle trong `dist/`.
- `lint`: Oxlint.
- `test`: 34 kiểm thử logic/component, gồm tích hợp whitelist vào Endpoint Detail và phân tách policy theo endpoint.
- `test:browser`: Vite preview tại `127.0.0.1:4173`, Chrome/Edge headless; kiểm tra cả trạng thái chờ API và demo, điều hướng, bộ lọc, refresh, login, desktop/mobile, lỗi runtime và Fetch/XHR. Chạy build trước, giữ cổng 4173 trống.
- Không tải trình duyệt hoặc thêm dependency. Có thể đặt `CHROME_PATH` tới Chrome/Edge đã cài. Tiến trình được tự đóng, profile tạm được xóa; ảnh nằm trong `.artifacts/` (gitignored).
- Nếu sandbox hạn chế tiến trình con/renderer, kiểm tra trình duyệt có thể báo EPERM hoặc timeout; cần chạy với quyền cho phép Chrome hoạt động.

## Màn hình

| Đường dẫn | Nội dung |
| --- | --- |
| `#/login` | Login wireframe; form chờ API xác thực; liên kết demo |
| `#/dashboard` | Thống kê, hoạt động gần đây, trạng thái endpoints |
| `#/endpoints` | Tìm kiếm/lọc, hostname, status, user, policy version, last seen |
| `#/endpoints/:id` | Thông tin máy, whitelist riêng, USB/policy và sự kiện của máy |
| `#/devices` | Tra cứu thiết bị, VID/PID, serial, endpoint và trạng thái |
| `#/whitelist` | Danh sách thiết bị được phép, lọc theo endpoint |
| `#/blocked` | Thiết bị bị chặn, lý do và endpoint |
| `#/events` | Event History với năm bộ lọc và phân trang |
| `#/settings` | Trạng thái chức năng đang chờ tích hợp |
| Đường dẫn khác | 404 |

Hash routing hỗ trợ reload và Back/Forward. Tham số `?demo=1` được giữ khi điều hướng bằng hash.

## Dữ liệu và API

- `services/api.ts` vẫn trả `BackendUnavailableError`; capabilities xác thực và thay đổi policy đều `false`.
- `admin-server` hiện có các controller dành cho Agent (enroll, heartbeat, events, policy/ack). Frontend chưa tích hợp Admin API; API Agent không thay thế API quản trị Web.
- `services/demo.ts` ánh xạ fixture endpoint (`endpointId`, `lastSeen`) sang view model (`id`, `lastSeenAt`), tạo snapshot riêng mỗi lần đọc, gắn endpoint ID cho event và cung cấp whitelist theo endpoint.
- `services/models.ts` và `types/*` là view model; chưa phải response schema của API quản trị thật.
- Ngoài demo, Dashboard/Endpoints chờ dữ liệu API và không hiển thị thống kê giả. Event History giữ mock có nhãn khi chưa có dữ liệu API; response thật ở trạng thái ready nhưng rỗng luôn giữ rỗng.
- `useResource` xử lý loading/error/unavailable, hủy đọc cũ và tránh cập nhật sau unmount.
- Allow/Revoke trong Endpoint Detail bị vô hiệu hóa và có giải thích chờ API. Panel cũng khóa nút nếu thiếu callback, tránh thao tác không có tác dụng.

## Phần cần làm sau Week 1

1. Chốt Admin routes/schema và ánh xạ response, bao gồm whitelist gắn endpoint ID.
2. Tích hợp auth/session, guard/logout và quyền admin thật.
3. Tích hợp đọc endpoints/events, Allow/Revoke theo endpoint; xử lý xác nhận, lỗi, policy version và trạng thái sync.
4. Chỉ bật capabilities khi API tương ứng hoạt động; giữ demo tách biệt và có nhãn.
5. Kiểm thử Agent → Server → Web cùng USB thật trên Ubuntu.

Frontend PASS không xác nhận enforcement USB, đăng nhập thật hoặc toàn bộ Week 1 system gate.

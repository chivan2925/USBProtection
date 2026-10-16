# USBShield — Admin Web

React + Vite + TypeScript. M4 Week 2 ngày 2026-10-16 (Asia/Saigon): **PASS tích hợp Admin Web với Spring Security/H2 thật**. Xem [báo cáo kiểm tra](../docs/admin-web/week2-evidence/README.md).

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
- Refresh tải lại fixture; **Thoát demo** quay về Login và kiểm tra phiên server.
- Demo chỉ đọc dữ liệu mẫu, không tạo phiên đăng nhập, token hoặc thay đổi quyền USB trên máy thật.

## Kiểm tra

```sh
npm run build
npm run lint
npm test
npm run test:browser
npm run test:live
```

- `build`: TypeScript và production bundle trong `dist/`.
- `lint`: Oxlint.
- `test`: 57 kiểm thử logic/component/HTTP, gồm auth/CSRF, whitelist A/B và hồi quy Events M5.
- `test:browser`: Chrome/Edge headless + HTTP fixture; login/session, Allow/Revoke, lỗi/rỗng/network, demo, desktop/mobile và Events.
- `test:live`: Chrome/Edge + JAR M1 thật, Spring Security/H2 riêng, login/session/CSRF, Allow/Revoke, ACK HTTP có kiểm soát, restart/persistence. Cần Java 21 và build `mvn package -DskipTests` trong `admin-server` trước. Đặt `JAVA_HOME` nếu Java mặc định không phải 21. Cả hai browser tests cần build frontend trước, giữ cổng 4173 trống, chạy từng lệnh lần lượt.
- Không tải trình duyệt hoặc thêm dependency. Có thể đặt `CHROME_PATH` tới Chrome/Edge đã cài. Tiến trình được tự đóng, profile tạm được xóa; ảnh nằm trong `.artifacts/` (gitignored).
- Nếu sandbox hạn chế tiến trình con/renderer, kiểm tra trình duyệt có thể báo EPERM hoặc timeout; cần chạy với quyền cho phép Chrome hoạt động.

## Màn hình

| Đường dẫn | Nội dung |
| --- | --- |
| `#/login` | Login thật bằng session/CSRF M1; liên kết demo |
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

- `services/api.ts` gọi Admin API canonical trong `docs/API.md`: auth, endpoints, events và whitelist per-endpoint. Vite proxy `/api` tới `http://127.0.0.1:8080`; có thể đổi `API_PROXY_TARGET` trong `.env.local`.
- `services/http.ts` gửi cookie cùng origin và CSRF từ `/api/auth/csrf`; không lưu credential trong localStorage. Đăng nhập cần tài khoản M1 cấp ngoài repo.
- `services/demo.ts` ánh xạ fixture endpoint (`endpointId`, `lastSeen`) sang view model (`id`, `lastSeenAt`), tạo snapshot riêng mỗi lần đọc, gắn endpoint ID cho event và cung cấp whitelist theo endpoint.
- `services/models.ts` và `types/*` là view model; adapter/mapper chuyển DTO M1 tại boundary.
- Ngoài demo, Dashboard/Endpoints/Events chỉ dùng dữ liệu API; rỗng/lỗi không tự chuyển sang mock. Protection/service giữ unknown khi chưa có health API.
- `useResource` xử lý loading/error/unavailable, hủy đọc cũ và tránh cập nhật sau unmount.
- Allow chọn USB có HASH và `usbDeviceId` từ events endpoint; POST `{usbDeviceId}`. Revoke dùng entryId; refetch desired/applied sau success. Allow trùng idempotent. Demo luôn khóa mutation.

## Phần cần môi trường Ubuntu

Agent M3 apply/ACK, USBGuard M2, USB vật lý và native notification còn `NOT EXECUTED`. ACK HTTP và events trong `test:live` là đầu vào tổng hợp cho backend thật, không phải evidence hardware.

Hướng dẫn chạy/handoff: [M4 integration](../docs/admin-web/WEEK2_ADMIN_API_INTEGRATION.md). Frontend/backend PASS chưa đóng Week 2 toàn nhóm.

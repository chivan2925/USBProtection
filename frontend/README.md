# USBShield — Admin Web

React + Vite + TypeScript. Event History đã tích hợp Admin API v0.2, đăng nhập/session/CSRF và phân trang, bộ lọc phía server. Các trang quản lý khác tiếp tục dùng demo hoặc trạng thái chưa tích hợp.

## Xem Event History với dữ liệu máy chủ

Chạy Admin Server theo [hướng dẫn M1](../docs/member1/WEEK2_SERVER_INTEGRATION.md). Backend mặc định ở `http://127.0.0.1:8080`; tài khoản Admin được cấu hình bằng `USBSHIELD_ADMIN_USERNAME` và `USBSHIELD_ADMIN_PASSWORD`. Mật khẩu bootstrap chỉ khởi tạo tài khoản chưa tồn tại.

Trong terminal khác, dùng Node.js 22.20+ và dependencies đã cài:

```powershell
cd frontend
npm run dev -- --host 127.0.0.1
```

Mở `http://127.0.0.1:5173/#/login`, đăng nhập Admin rồi vào `#/events`. Trang lấy các sự kiện đã được backend lưu; nếu database chưa có sự kiện, trang hiển thị rỗng. Frontend không tự tạo dữ liệu trong database.

Vite dev/preview proxy `/api` về backend. Đổi đích khi cần, trước khi chạy Vite:

```powershell
$env:USBSHIELD_API_TARGET = 'http://127.0.0.1:8080'
```

Production cần reverse proxy `/api` cùng origin với frontend. Browser dùng session cookie; không lưu password/token trong localStorage hoặc sessionStorage.

## Events và đăng nhập

- Đăng nhập lấy CSRF, gửi `POST /api/auth/login`, làm mới CSRF sau khi session xoay. Mở lại trang kiểm tra phiên qua `/api/auth/me`; đăng xuất gọi `/api/auth/logout` với CSRF hiện tại.
- Events đọc `/api/admin/events`, đủ 6 cột, sắp xếp theo server và 20 bản ghi/trang. Số lượng tổng lấy từ `totalElements`, không tính từ trang hiện tại.
- Endpoint ID và Linux Username lọc chính xác; USB lọc bằng Device ID được hiển thị dưới tên thiết bị. Loại sự kiện và quyết định dùng enum backend.
- Khoảng ngày bao gồm toàn bộ hai ngày được chọn theo múi giờ trình duyệt. Ngày cuối chuyển thành mốc inclusive với nanosecond để loại midnight của ngày sau. Giá trị hiển thị dùng timestamp đã được backend lưu.
- 401 có liên kết đăng nhập; 403, lỗi server, mất mạng, dữ liệu rỗng và lỗi payload đều giữ trạng thái tương ứng, không tự chuyển sang mock.
- Liên kết endpoint vẫn mở `#/endpoints/:id`; phần đọc Endpoint Detail thật thuộc gói tích hợp quản lý tiếp theo.

## Demo và các trang còn lại

Mở `http://127.0.0.1:5173/?demo=1#/dashboard` để xem demo có nhãn rõ. Demo không gọi Admin API hoặc tạo phiên đăng nhập, và giữ bộ lọc cục bộ. Đăng nhập trong demo bị khóa; dùng liên kết thoát demo để đăng nhập thật.

Dashboard, Endpoints, Devices, Whitelist và Allow/Revoke chưa nối adapter live trong gói này. Ngoài demo, các trang đó hiển thị trạng thái chưa khả dụng. `services/api.ts` là adapter chung cho session và Events; các thao tác quản lý chưa tích hợp vẫn bị khóa.

## Kiểm tra

```powershell
npm test
npm run lint
npm run build
npm run test:browser
npm run test:browser:live
```

- Unit/component/API-fixture tests kiểm tra mapper, query, CSRF/session, empty/error/cancellation, ngày giờ và phân trang.
- `test:browser` chạy Vite preview tại 4173, kiểm tra các route, trạng thái lỗi/chưa tích hợp và explicit demo, desktop/mobile.
- `test:browser:live` cần JAR `admin-server/target/usbshield-admin-server.jar`, JDK (`JAVA_HOME` hoặc JDK 22 tại đường dẫn cài Windows hiện tại) và Chrome/Edge. Script chạy Spring Boot/H2 riêng tại 18083, proxy/preview 4174, tạo 25 sự kiện QA qua Agent HTTP và kiểm tra login, Events, phân trang/lọc/ngày, reload/logout và lỗi. Cổng kiểm thử cần trống.
- Cả hai browser scripts dùng Chrome/Edge đã cài (`CHROME_PATH` có thể chọn executable), không tải browser/dependency. Tiến trình chạy ẩn; profile/database kiểm thử được dọn trong phạm vi `.artifacts/` của workspace. Ảnh kiểm chứng được giữ ở `.artifacts/`.

[Xem evidence Windows/API/browser](../docs/qa/week2-evidence/M5_LIVE_EVENTS_VERIFICATION.md). Sự kiện QA được tạo qua HTTP trong H2 cô lập; kiểm thử USB vật lý/USBGuard/notification Ubuntu vẫn để NOT EXECUTED theo phạm vi hiện tại.

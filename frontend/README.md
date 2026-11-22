# USBProtection — Admin Web

React 19 + Vite 8 + TypeScript, source `frontend/`. Production frontend được build thành static assets trong **Admin Server Spring Boot JAR** (cùng origin `/api/**`); dùng **PostgreSQL/Flyway** ở backend, không kết nối DB trực tiếp từ browser.

> **Đối chiếu source:** `chivan2925/USBProtection` `main` `c7381fc7b9ba34aa561083431ce7c68b9a514a51` (20/11/2026). Trang **Settings đã bị xóa** ở cả frontend và Admin API, không phải chỉ ẩn menu. Xem [Admin Guide](../docs/ADMIN_GUIDE.md), [Run Guide](../docs/RUN_GUIDE.md), [API addendum](../docs/API_PHASE1_TO_PHASE3_ADDENDUM.md) và [bản đồng bộ docs](../docs/DOCUMENTATION_SYNC_20261120_c7381fc7.md).

## 1. Phát triển (loopback) và runtime

Yêu cầu Node/npm phù hợp với lockfile (`npm ci`), JDK 21/Maven nếu build backend. Từ thư mục `frontend/`:

```powershell
npm ci
npm run dev -- --host 127.0.0.1
```

Vite phục vụ tại `http://127.0.0.1:5173/`. `vite.config.ts` proxy `/api` mặc định tới `https://usbprotection.lab` với **TLS certificate verification bật**; `API_PROXY_TARGET` hoặc `USBSHIELD_API_TARGET` có thể chọn đích dev/test đã phê duyệt. Hostname lab phải được phân giải và CA tin cậy. Không tắt `secure`, không thêm HTTP fallback/đường bypass Spring/Nginx cho Agent `live`.

Admin Web triển khai thật: truy cập HTTPS origin được Nginx cấu hình, ví dụ `https://usbprotection.lab/#/login`; cookie session và CSRF quản lý bởi Spring Security. Không đưa token/mật khẩu vào localStorage, không lấy dữ liệu giả để thay sự cố API thực. `/demo` hoặc `/demo/` là **mock chỉ đọc** với navigation qua hash, không gọi API hay thay đổi USBGuard. `?demo=1` không mở demo trong Admin Web thật.

`npm run build` cập nhật `frontend/dist/` nhưng **không sửa JAR đang chạy**; muốn thay UI trong ứng dụng phải đóng gói lại Admin JAR bằng Maven profile `with-frontend` hoặc `build-release.ps1`, sau đó triển khai JAR tương ứng. Không cài React/Vite trên Ubuntu Client.

## 2. Các màn hình hiện hành

| Hash route | Hành vi |
|---|---|
| `#/login` | Đăng nhập Admin, session/CSRF; đăng xuất qua điều khiển tài khoản |
| `#/dashboard` | Tổng hợp số liệu server và sự kiện gần đây, không tự suy tổng từ trang đầu |
| `#/endpoints` | Tìm kiếm/lọc online-offline và phân trang server; hiển thị version và USBGuard health |
| `#/endpoints/:id` | Chi tiết endpoint/modal, USB đã quan sát, whitelist, Allow/Revoke có xác nhận và policy ACK |
| `#/devices` | Trạng thái USB, lọc/tìm kiếm/phân trang server; “Bị chặn” là **bộ lọc**, không có trang Blocked riêng |
| `#/whitelist` | Danh sách whitelist toàn hệ thống có pagination; mutation vẫn theo endpoint |
| `#/events` | Event Logs: filter endpoint/user/USB/decision/type/date và phân trang server |
| `#/settings` | **ĐÃ XÓA**; khi truy cập hash cũ, UI hiển thị trang 404, không còn API đổi mật khẩu Admin qua Web |

Các bảng phân trang sử dụng **10 bản ghi mỗi trang** trong UI hiện tại. Online (heartbeat), ACK/applied policy và USBGuard health `HEALTHY/DEGRADED/UNAVAILABLE/UNKNOWN` là các khái niệm khác nhau; trạng thái Web không tự chứng minh USB đang cắm đã bị chặn vật lý. API lỗi/empty/401/403 không được thay bằng dữ liệu mock ngoài `/demo`.

**Mật khẩu Admin:** `POST /api/admin/account/password` và Settings đã bị loại bỏ. Login, logout, CSRF, phiên và bootstrap Admin ban đầu vẫn còn; quên mật khẩu cần maintenance profile `admin-reset` **không mở HTTP** trên máy Server, không phải đổi biến bootstrap rồi restart. Xem [Developer Guide](../docs/DEVELOPER_GUIDE.md).

## 3. Kiểm thử frontend và tích hợp

Từ **root** repository (PowerShell; `.env` untracked chứa **chỉ** thông tin PostgreSQL test):

```powershell
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
.\scripts\with-env.ps1 -RequireTestDb -Command { npm --prefix frontend run test:browser:live }
.\scripts\with-env.ps1 -RequireTestDb -Command { npm --prefix frontend run test:live }
```

Hoặc chạy gate Windows có tích hợp Maven/Agent/DEB theo nhu cầu:

```powershell
.\packaging\tests\verify-windows.ps1 -AdminOnly
.\packaging\tests\verify-windows.ps1
```

- `npm test`: component, auth/CSRF, Endpoints/Whitelist, Events, paged API, empty/error states, regression sau khi xóa Settings.
- `test:browser`: fixture browser, routing, Allow/Revoke mock, demo/read-only, desktop/mobile; cần Chrome/Edge tương thích.
- `test:browser:live` và `test:live`: backend thật với **PostgreSQL test-only** (`usbshield_browser_test`, schema `usbshield_e2e_*`), synthetic HTTP events; **không phải USBGuard hardware E2E**. Chỉ dùng `usbshield_test`/`usbshield_browser_test`; không chạy test vào application DB `usbshield`.
- Source đã bỏ `frontend/src/pages/Settings.tsx` và test Settings cũ. Các suite hiện hành không được import `Settings.tsx` hoặc kỳ vọng menu/route đó còn hoạt động.

**Báo cáo owner ngày 20/11/2026:** Windows verifier trả `RESULT: PASS for executed Windows verification gates`; các gate Linux-only không được thay bằng Windows PASS. `build-release.ps1` tạo JAR nhưng manifest ở `releases/` ghi `workingTreeCleanAtStart=false` và source commit `b12bb597`, nên **chưa release-qualified và không phải build provenance sạch từ HEAD `c7381fc7`**. Xem [Admin release](../docs/ADMIN_SERVER_RELEASE.md).

**Phạm vi chưa chứng minh bằng frontend/Windows tests:** native Ubuntu USBGuard, USB vật lý, Linux notifier IPC, reboot/offline, negative TLS, Client-IP HTTPS/ACL. `packaging/tests/verify-phase3-https-acl.sh` vẫn là bài kiểm tra riêng từ Admin/Client IP thực, không nằm trong Windows verifier. Bằng chứng Week 1–4 và 09/11 là **snapshot lịch sử**, không sửa lại số liệu cũ.

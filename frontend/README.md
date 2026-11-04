# USBShield — Admin Web

React + Vite + TypeScript; tích hợp vào Admin Server Spring Boot JAR, sử dụng **PostgreSQL/Flyway**. Current branch `vinhkhang` commit `035b8ed5a4f5b111dace06cf839cf5f1c33f1142` (04/11/2026, chưa merge `main`). Hướng dẫn vận hành tập trung: [RUN_GUIDE](../docs/RUN_GUIDE.md); [API hiện hành](../docs/API_PHASE1_TO_PHASE3_ADDENDUM.md); [Admin Guide](../docs/ADMIN_GUIDE.md); [Implementation status](../docs/IMPLEMENTATION_STATUS_20261104.md).

## Dev và demo (chỉ loopback)

Node.js 22.20+ và npm; thư mục `frontend/`:

```sh
npm ci
npm run dev -- --host 127.0.0.1
```

Admin Server local ở `http://127.0.0.1:8080`, Vite tại `http://127.0.0.1:5173/`; proxy `/api` về backend (tùy chọn `API_PROXY_TARGET`). Trang Login dùng session/CSRF thật; mật khẩu bootstrap chỉ tạo Admin nếu chưa tồn tại. **Không dùng Vite/HTTP loopback làm cấu hình production/LAN.** Mở demo qua liên kết Login hoặc `?demo=1#/dashboard`: dữ liệu mock, chỉ đọc, không có credential và không thay đổi USBGuard/policy.

## Runtime và quyền truy cập

- **Production Admin:** React được build thành static assets trong một Spring Boot JAR; Admin workstation chỉ dùng HTTPS từ Nginx/IP allowlist, cùng origin với `/api/auth/**` và `/api/admin/**`. Chuyển hướng trực tiếp cổng backend không được phép trên LAN.
- **Ubuntu Client:** không cài Web; Agent `live` dùng HTTPS có CA/hostname verification, và được ACL **chỉ cho `/api/agent/**`**. Auth/CSRF ở Spring vẫn bắt buộc, ACL không thay thế xác thực. Rate limiting không nằm trong scope theo quyết định owner.
- **Database:** Web không kết nối trực tiếp PostgreSQL; Admin Server dùng Flyway V1/V2 và Hibernate `validate`, không dùng H2 runtime. Server-paged APIs chịu trách nhiệm tìm kiếm/đếm/giới hạn.

## Màn hình

| Hash route | Nội dung |
|---|---|
| `#/login` | Admin session/CSRF login hoặc demo chỉ đọc |
| `#/dashboard` | Thống kê backend toàn cục và hoạt động gần đây |
| `#/endpoints` | Server-side search/filter/pagination; online/offline totals toàn hệ thống |
| `#/endpoints/:id` | Metadata, `Healthy/Degraded/Unavailable/Unknown`, checkedAt/stale, desired/applied ACK, USB/whitelist scoped theo endpoint |
| `#/devices` | Device lookup/filter với server pagination |
| `#/whitelist` | Một API chung `/api/admin/whitelist/page`, phân trang và tìm kiếm; không N+1 theo từng endpoint |
| `#/blocked` | Sự kiện/thiết bị bị block được ghi nhận, không phải phép đo enforcement trực tiếp |
| `#/events` | Event History: filter và phân trang ở Server, giữ source timestamps |
| `#/settings` | **Đổi mật khẩu Admin** khi có phiên, đăng xuất các phiên cũ; cấu hình USBGuard trực tiếp từ Web chưa có API |

**Status boundary:** online chỉ là heartbeat; ACK chỉ là Agent-reported policy applied version; `Healthy` chỉ được hiển thị khi báo cáo USBGuard còn mới và đồng bộ, **không phải xác nhận USB vật lý độc lập từ Server**. Giao diện rỗng/lỗi/401/403 không được tự sinh mock records ngoài Demo.

## Kiểm thử Frontend và browser-live

Từ thư mục gốc repository, dùng `.env` *untracked* cùng wrapper PowerShell cho các test PostgreSQL (IntelliJ DB credentials không tự truyền cho npm/Maven):

```powershell
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
mvn -f admin-server/pom.xml -Pwith-frontend -DskipTests package
.\scripts\with-env.ps1 -RequireTestDb -Command { npm --prefix frontend run test:browser:live }
.\scripts\with-env.ps1 -RequireTestDb -Command { npm --prefix frontend run test:live }
```

- `npm test`: components, auth/CSRF, endpoint/whitelist paged read, USBGuard health view, Events search/filters và API errors.
- `test:browser`: browser fixture, session/demo/desktop/mobile; cần Chrome hoặc Edge phù hợp.
- `test:live`: Spring Security + PostgreSQL **test DB** với controlled synthetic Agent ACK, không phải Ubuntu Agent thật.
- `test:browser:live`: PostgreSQL `usbshield_browser_test`, schema `usbshield_e2e_*` riêng theo run, tạo HTTP test events tổng hợp, cleanup trên normal completion. Browser harness có thể dùng cổng 4173/4174 và backend riêng; các lệnh chạy lần lượt. Có thể đặt `CHROME_PATH` khi cần. Không bao giờ chạy vào app database `usbshield`.
- Nếu tiến trình browser bị kill, kiểm tra schema test orphan trước khi dọn; không tự xóa bất kỳ schema ứng dụng nào. Screenshots `.artifacts/` là test artifacts cục bộ.

**Chưa nghiệm thu bằng các test này:** USBGuard enforcement thực tế, Agent live TLS/ACL trên LAN, USB thật, notifier GUI Ubuntu, reboot/offline. Xem [HTTPS/ACL/Paging](../docs/PHASE3_HTTPS_ACL_AND_PAGING.md) và [Ubuntu Acceptance](../docs/qa/WEEK4_UBUNTU_ACCEPTANCE.md).

Historical UI screenshots/tests: [M4 Week 2](../docs/admin-web/week2-evidence/README.md), [M5 Events evidence](../docs/qa/week2-evidence/M5_LIVE_EVENTS_VERIFICATION.md); số liệu cũ không được tự đổi thành PASS của HEAD hiện tại.

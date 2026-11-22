# USBShield — API/Transport Addendum (Phase 1–3, 04/11/2026)

> **Trạng thái tài liệu (20/11/2026):** Đây là phần mở rộng hợp đồng HTTP/API từ giai đoạn 04/11, hiện đã có trong nhánh `main` tại baseline kiểm tra `c7381fc7b9ba34aa561083431ce7c68b9a514a51`. Dòng ghi chú cũ “`vinhkhang` chưa merge `main`” không còn đúng. Tài liệu này bổ sung [API v0.2](API.md) và [Agent–Server Protocol v0.2](AGENT_SERVER_PROTOCOL.md); không thay thế hai hợp đồng đó. Không suy ra Windows/Ubuntu E2E PASS chỉ từ source hoặc từ tài liệu này. Xem [Admin Server release](ADMIN_SERVER_RELEASE.md), [Run Guide](RUN_GUIDE.md) và [Implementation status 09/11 — snapshot lịch sử](IMPLEMENTATION_STATUS_20261109.md).

## Admin authentication và tình trạng F03 tại HEAD

- **Đang hoạt động:** `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`. Spring Security quản lý cookie session, CSRF, phân quyền `ROLE_ADMIN` và invalidation theo credential version.
- **Đã loại bỏ trong commit `c7381fc7`:** `POST /api/admin/account/password` (trước đây là F03/password change qua Settings). Không gọi API này; controller và bài test tương ứng đã bị xóa. Đây là thay đổi hành vi API so với tài liệu Phase 1–3 ngày 04/11, **không được hiểu endpoint vẫn hiện hữu**.
- **Giữ lại:** `AdminBootstrap` chỉ tạo Admin ban đầu nếu username chưa tồn tại; `USBSHIELD_ADMIN_PASSWORD` không reset account đã có. `AdminPasswordResetRunner` dùng profile `admin-reset`, `spring.main.web-application-type=none` và biến `USBSHIELD_ADMIN_RESET_*` cho khôi phục offline; **không mở route HTTP reset**. Chính sách mật khẩu 12+ ký tự in được, tối đa 72 UTF-8 bytes vẫn áp dụng khi reset.

## Endpoint list (F14) — server-side pagination

`GET /api/admin/endpoints/page?page=0&size=20&q=<search>&status=online|offline`

- `page` đánh số từ 0, tối đa 100000; `size` từ 1 đến 100; `q` tối đa 200 ký tự, tìm không phân biệt hoa/thường trên endpoint ID/hostname/IP/currentUser, ký tự `%/_` được xử lý literal.
- `status` không có, `online`, hoặc `offline`; backend lọc trước khi phân trang, sắp xếp theo `endpointId`.
- Response envelope: `{content: EndpointResponse[], page, size, totalElements, totalPages, onlineEndpoints, offlineEndpoints}`. `totalElements` theo bộ lọc; `onlineEndpoints/ offlineEndpoints` là tổng **trên toàn hệ thống**, không phải đếm theo trang.
- `EndpointResponse` gồm `endpointId`, `hostname`, `ipAddress`, `operatingSystem`, `currentUser`, `status`, `lastSeen`, `policyVersion`, `appliedPolicyVersion`, `usbGuardStatus`, `usbGuardCheckedAt`, `usbGuardStale`.
- `GET /api/admin/endpoints` là **deprecated compatibility endpoint**, chỉ trả về tối đa **100** endpoint. Dùng `/page` cho tất cả consumer mới.

## Global whitelist page (F12)

`GET /api/admin/whitelist/page?page=0&size=20&endpointId=<optional>&q=<optional>`

- `page/size` giới hạn như Endpoint; `endpointId` không rỗng và tối đa 100; `q` tối đa 200; truy vấn chỉ gồm entries `enabled=true`.
- `q` tìm theo tên thiết bị, endpoint ID, fingerprint, VID, PID và serial; kết quả sắp xếp `createdAt` giảm dần rồi ID giảm dần.
- Response: `{content, page, size, totalElements, totalPages}`. Một truy vấn trang có join với metadata thiết bị, không gọi per-endpoint HTTP cho mỗi endpoint.
- Chi tiết whitelist và mutation Allow/Revoke theo **một endpoint** không thay đổi. Không có global Allow.

## Agent heartbeat, USBGuard health và policy ACK (F04/F06/I03)

`POST /api/agent/heartbeat` bắt buộc Agent identity headers theo v0.2. Request bao gồm `endpointId`, metadata tùy chọn, `currentPolicyVersion` và **optional** `usbGuardStatus: "HEALTHY"|"DEGRADED"|"UNAVAILABLE"|"UNKNOWN"` (Agent phiên bản cũ có thể không gửi).

- Server ghi `usbguard_reported_status`, `usbguard_checked_at` tại thời điểm **server nhận** heartbeat; API Admin trả trạng thái hiệu lực. Nếu không có dữ liệu, offline hoặc checkedAt quá cũ thì `UNKNOWN`. Nếu Agent báo `HEALTHY` nhưng desired/applied policy khác nhau thì trạng thái hiển thị `DEGRADED`. `usbGuardStale` là cờ độc lập.
- `currentPolicyVersion` trong heartbeat là telemetry, **không** cập nhật `appliedPolicyVersion`; chỉ `POST /api/agent/policy/ack` hợp lệ làm việc đó. ACK không chứng minh trạng thái USB vật lý khi thiết bị đang cắm.
- HASH rule phải hợp lệ ở Server và Agent. Agent validate **toàn bộ** whitelist trước apply; payload sai bị từ chối, không áp dụng một phần rồi ACK thành công.
- `occurredAt` của USB event là thời điểm nguồn; chặn timestamp bất thường trong tương lai (mặc định quá 5 phút), nhưng vẫn giữ khả năng gửi offline event cũ hợp lệ. `receivedAt` vẫn riêng với `occurredAt`.

## Deployment TLS và route ACL (F01/F02/I02)

- Agent profile `live` **phải** dùng `USBSHIELD_SERVER_URL=https://...`; chứng chỉ được kiểm tra chain/trust và SAN hostname. Với internal CA, cấu hình `USBSHIELD_TLS_CA_CERTIFICATE` tới public CA PEM; không trust-all, không HTTP fallback, không follow redirect mang token.
- Nginx chỉ cho source IP Admin tới Web/static và `/api/auth/**`, `/api/admin/**`; source Client chỉ được tới `/api/agent/**` (vẫn yêu cầu Agent auth). Backend bind `127.0.0.1:18080`, không lộ trực tiếp trên LAN; PostgreSQL 5432 không mở cho Client.
- **Không triển khai rate limiting** (owner decision); ACL không ngăn brute-force từ Admin source được phép. IP ACL không phân biệt được Admin/Client nếu chúng bị NAT về cùng source IP.

Chi tiết vận hành: [RUN_GUIDE](RUN_GUIDE.md), [Phase 3 HTTPS/ACL/Paging](PHASE3_HTTPS_ACL_AND_PAGING.md), [Implementation status 04/11 — snapshot lịch sử](IMPLEMENTATION_STATUS_20261104.md).

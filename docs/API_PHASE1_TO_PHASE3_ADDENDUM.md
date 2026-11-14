# USBShield — API/Transport Addendum (Phase 1–3, 04/11/2026)

**Source:** `vinhkhang` `035b8ed5a4f5b111dace06cf839cf5f1c33f1142` (chưa merge `main`). Tài liệu này **bổ sung** [API v0.2](API.md) và [Agent–Server Protocol](AGENT_SERVER_PROTOCOL.md) lịch sử; không thay đổi các HTTP evidence đã công bố. Test Windows không chứng minh TLS/ACL/USBGuard thực tế trên Ubuntu.

## Admin authentication và đổi mật khẩu (F03)

- `POST /api/admin/account/password` — **Admin session + CSRF**. JSON body: `{ "currentPassword": "...", "newPassword": "..." }`.
- Mật khẩu mới phải từ **12 ký tự in được**, không vượt **72 UTF-8 bytes** và khác mật khẩu cũ. Mật khẩu cũ sai: HTTP 401; mật khẩu mới không hợp lệ: HTTP 400.
- Khi thành công, `{"changed":true}` và session hiện tại bị invalidate; version credential tăng, các session cũ không còn hợp lệ. Không reset bằng cách đổi `USBSHIELD_ADMIN_PASSWORD` rồi restart.
- Reset khẩn cấp chỉ qua **maintenance profile không mở Web HTTP** và quy trình root/operator; không cung cấp route reset công khai cho Client.

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

Chi tiết vận hành: [RUN_GUIDE](RUN_GUIDE.md), [Phase 3 HTTPS/ACL/Paging](PHASE3_HTTPS_ACL_AND_PAGING.md), [Implementation status](IMPLEMENTATION_STATUS_20261104.md).

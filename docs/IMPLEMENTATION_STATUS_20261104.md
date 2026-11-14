# USBShield — Current implementation & verification boundary (04/11/2026)

**Branch:** `vinhkhang` — `035b8ed5a4f5b111dace06cf839cf5f1c33f1142`. Branch chưa merge `main` tại thời điểm lập tài liệu. Đây là đối chiếu source/requirements, **không phải biên bản chạy Ubuntu E2E**.

## Ba đợt đã tích hợp trên branch

| Đợt | Finding | Trạng thái implementation | Giới hạn cần chứng minh |
|---|---|---|---|
| 1 — Correctness | F03, F04, F05, F06, F08, I03 | Admin change/offline reset + session invalidation; aligned HASH validation and no invalid ACK; storage fail-closed, future-skew check; Agent USBGuard probe/heartbeat, DB status/time, Web 4-state/stale/policy ACK | Ubuntu rules/enforcement actual state; offline event replay; reset operational drill |
| 2 — Reliability | F07, F09, F10, F11 | Flyway V1/V2, Hibernate `validate`, reviewed baseline path; notifier stale-socket recovery; per-class and per-browser-run PostgreSQL schema isolation/cleanup | Existing DB backup/restore baseline drill; Ubuntu stale socket; abnormal-test cleanup |
| 3 — Access & scale | F01, F02, F12, F14, I02 | Agent live requires HTTPS, TLS chain/hostname verification, no HTTP fallback; Nginx/firewall ACL templates; server-side paged Endpoint and global Whitelist API | TLS handshake and Admin/Client ACL from distinct hosts; real LAN configuration |

**Scope decisions:** F13 (global event ranking optimization) **DEFERRED** do lượng dữ liệu demo nhỏ. I01 (global catalog lock) **ACCEPTED** for current low-scale workload. F02 không làm rate limiting: chấp nhận rủi ro brute-force từ các nguồn Admin được ACL cho phép; HTTPS và ACL không thay thế login/CSRF.

## Kiến trúc runtime và hợp đồng

- `admin-server` là một executable Spring Boot JAR với React assets, PostgreSQL service riêng; `prod` bind `127.0.0.1:18080` khi cấu hình. HTTPS proxy công khai TLS/443 với route ACL, **không công khai Spring/DB ports**.
- Admin workstation được phép tới Web/static/Auth/Admin API; Client workstation chỉ tới `/api/agent/**`. Spring Security vẫn xác thực từng luồng; Admin/Client IP phải phân biệt được (không cấp Admin allowlist trùm cả Client subnet).
- Agent `live`: `USBSHIELD_SERVER_URL=https://...`, CA PEM nếu dùng PKI nội bộ qua `USBSHIELD_TLS_CA_CERTIFICATE`, certificate SAN khớp URL. External `agent.env` root-owned `0600`, chuẩn bị trước `.deb` postinst; không có HTTP fallback.
- Admin list: `GET /api/admin/endpoints/page?page=0&size=20&q=...&status=online|offline` (metadata tổng + toàn cục); whitelist: `GET /api/admin/whitelist/page?page=0&size=20&endpointId=...&q=...`. Page max 100; legacy endpoints list giới hạn 100. Allow/Revoke per-endpoint unchanged.
- USBGuard health là **báo cáo từ Agent** (`HEALTHY`, `DEGRADED`, `UNAVAILABLE`, `UNKNOWN`) và thời điểm kiểm tra; server còn tính online/stale và version mismatch. **Không** đồng nhất heartbeat, ACK hay báo cáo Healthy với bằng chứng USB vật lý đã thực hiện.
- Flyway: V1 schema nền, V2 `credential_version`, `usbguard_reported_status`, `usbguard_checked_at`; existing unversioned DB cần backup+preflight rồi baseline V1 một lần; fresh DB chạy V1/V2 bình thường. Never baseline test databases.

## Verification ledger — phân biệt rõ mức bằng chứng

| Nhóm | Trạng thái hiện tại |
|---|---|
| Maven Admin, Maven Agent, frontend unit/lint/build và browser-live PostgreSQL (Windows) | Owner **đã báo BUILD SUCCESS/PASS** sau các bản sửa; không đính kèm một bộ release-evidence độc lập mới vào commit này |
| Agent JUnit Linux-only notifier socket recovery | Có thể **SKIPPED trên Windows** theo điều kiện `@EnabledOnOs(LINUX)`; chưa có kết quả Ubuntu mới |
| Nginx TLS/ACL from real Client/Admin source IPs, CA rejection/hostname mismatch | **NOT EXECUTED / NOT VERIFIED** trên mạng thực |
| Ubuntu `.deb`, USBGuard live, physical USB, GUI notification, reboot/offline | **NOT EXECUTED / NOT VERIFIED** trong bộ evidence mới |

**Không tự đánh dấu release FULL PASS hay PR merged** trước khi Ubuntu/LAN acceptance có đầy đủ evidence. Historical Week 1–4 logs ở `docs/qa/` giữ nguyên thời điểm/SHA và ý nghĩa đã ghi.

## Tài liệu canonical

1. [Run Guide](RUN_GUIDE.md) — một runbook cho build, PostgreSQL, `.env`, Server và `.deb`.
2. [PostgreSQL Flyway baseline](../admin-server/ops/POSTGRES_FLYWAY_BASELINE.md) — chỉ cho DB ứng dụng đã có schema.
3. [Phase 3 HTTPS/ACL/Paging](PHASE3_HTTPS_ACL_AND_PAGING.md) — Nginx/firewall/CA và HTTP contract mới.
4. [Architecture](ARCHITECTURE.md), [Developer Guide](DEVELOPER_GUIDE.md), [Admin Guide](ADMIN_GUIDE.md).
5. [Ubuntu acceptance](qa/WEEK4_UBUNTU_ACCEPTANCE.md) — checklist cần thực thi trước full sign-off.

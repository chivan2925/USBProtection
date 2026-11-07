# USBShield Ubuntu — Hệ thống ngăn chặn và quản lý USB Flash Disk trên Ubuntu

> **Source review baseline:** `vinhkhang` `f446c04a8983315c52c16e377820a99b8fd3297f` (07/11/2026). Các mã finding F01–F14 trong báo cáo lịch sử ở các dòng dưới không phải cùng nhóm ID với static-review correction này. Windows/Ubuntu verification cho các sửa đổi mới **chưa được chạy**; không suy diễn từ các lần PASS trước.

> Seminar 4 tuần / demo trong LAN. Ubuntu Client: **24.04 LTS amd64**. Backend: **Java 21 + Spring Boot**. Admin Web: **React + Vite + TypeScript**. USB enforcement: **USBGuard + systemd**. Database: **PostgreSQL (dịch vụ riêng, lưu bền vững)**.

## Tổng quan

| Thành phần | Vai trò |
|---|---|
| `admin-server/` | Spring Boot: Admin login, Agent enrollment/heartbeat, per-endpoint policy, audit/event API, PostgreSQL |
| `frontend/` | React Admin Web, build thành static assets và phục vụ từ cùng JAR Admin Server |
| `client-agent/` | Agent không có Web; gửi event/heartbeat, đồng bộ chính sách USBGuard |
| `packaging/` | Debian `.deb`, `systemd`, USBGuard base rule, notifier helper |
| `docs/` | Blueprint, API/contract, guide, QA/test/evidence và tài liệu kỹ thuật |

### Nguyên tắc cốt lõi

1. USB Mass Storage class `08` chưa được cấp phép phải BLOCK tại Client theo USBGuard local policy; non-storage/HID bình thường không bị chặn theo base rule.
2. Admin Allow/Revoke theo **từng endpoint**, Agent đồng bộ local policy. Người dùng **rút USB rồi cắm lại** để áp dụng quyết định mới; không yêu cầu thay đổi runtime ngay trên thiết bị vẫn đang cắm.
3. Sự kiện ghi nhận endpoint, USB và **tài khoản/session Ubuntu local active**, không khẳng định ai trực tiếp cắm vật lý.
4. Normal user không có sudo/root hoặc quyền sửa USBGuard IPC/policy không được tắt/sửa protection. Root vẫn có thể quản trị hệ thống.
5. **Mạng LAN thực tế:** HTTPS + xác thực chứng chỉ là bắt buộc với Agent `live`; Nginx/firewall ACL chỉ cho Client truy cập `/api/agent/**`, còn Web/Auth/Admin API thuộc allowlist IP máy Admin. Backend bind loopback, không mở port trực tiếp. **Không triển khai rate limiting** theo quyết định owner.

## Trạng thái kỹ thuật hiện tại (sau 3 đợt)

- **F03/F04/F05/F06/F08, I03:** Admin đổi mật khẩu khi đã đăng nhập và reset bằng chế độ bảo trì; fingerprint được kiểm tra hai đầu, policy sai không ACK; Agent `live` không fallback storage; timestamp tương lai bị giới hạn nhưng offline event cũ được giữ. Agent báo cáo USBGuard `Healthy / Degraded / Unavailable / Unknown`; server xét độ mới, endpoint online và desired/applied ACK, không đồng nhất heartbeat với enforcement.
- **F07, F09–F11:** Flyway V1/V2 trước Hibernate `validate`; database PostgreSQL hiện hữu cần baseline có kiểm tra/backup trước khi khởi động bằng bản mới. Notifier khôi phục stale Unix socket có kiểm tra; test Java và browser có schema PostgreSQL riêng và cleanup.
- **F01, F02, F12, F14, I02:** HTTPS bắt buộc và TLS certificate/hostname verification trên Agent `live`; ACL Nginx tách máy Admin/Client; danh sách Endpoint/Whitelist phân trang và lọc tại backend, không N+1 HTTP ở Web. Không có rate limit theo lựa chọn owner.
- **Giới hạn nghiệm thu:** Windows Maven/frontend/browser synthetic PASS theo xác nhận của owner; ba test notifier phụ thuộc Linux có thể SKIP trên Windows. Ubuntu `.deb`, USB vật lý, notifier GUI, TLS CA thực và ACL từ máy Admin/Client riêng biệt **chưa có bằng chứng E2E mới**.

## Bắt đầu

**Tất cả bước build, test, chạy Admin Server, HTTPS/LAN, đóng gói `.deb`, cấu hình Ubuntu Client và kịch bản Allow/Revoke nằm ở đúng một tài liệu:**

**[docs/RUN_GUIDE.md](docs/RUN_GUIDE.md)**

Build/test hiện hành trên Windows: `./packaging/tests/verify-windows.ps1` (PowerShell 7.4+). Mỗi build `.deb` sinh `packaging/dist/usbshield-agent.release.json` (Version riêng, SHA256, Git SHA) và gói `.deb` tương ứng. Ubuntu tối giản: `docs/USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md`. **B01–B05 chưa PASS** trên source mới.

PostgreSQL phải được cài/khởi động và có database cùng role riêng cho ứng dụng. Dev/test trên Windows có thể dùng `.env` (không commit) với `scripts/with-env.ps1` để nạp test credentials; xem runbook. **Không chạy baseline Flyway trên database test.** Admin Server vẫn là **một JAR**, Client Agent vẫn là **`.deb`**; không cần Docker cho bản demo một server. Ubuntu E2E còn **DEFERRED/NOT EXECUTED**; Windows Maven/frontend/browser (PostgreSQL test DB, dữ liệu tổng hợp) không phải kết quả phần cứng thật. Chưa có bằng chứng Ubuntu hardware E2E từ lần đổi database.

## Tài liệu khác

- [Blueprint chính thức](USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md)
- [Trạng thái triển khai 3 đợt và checklist nghiệm thu](docs/IMPLEMENTATION_STATUS_20261104.md)
- [Flyway baseline cho PostgreSQL hiện hữu](admin-server/ops/POSTGRES_FLYWAY_BASELINE.md) · [HTTPS/ACL và API phân trang](docs/PHASE3_HTTPS_ACL_AND_PAGING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Developer Guide](docs/DEVELOPER_GUIDE.md) — kỹ thuật mã nguồn và API, không phải runbook vận hành
- [API v0.2](docs/API.md) · [Addendum API Phase 1–3](docs/API_PHASE1_TO_PHASE3_ADDENDUM.md) · [Agent–Server protocol](docs/AGENT_SERVER_PROTOCOL.md)
- [Admin Guide](docs/ADMIN_GUIDE.md) — hướng dẫn **sử dụng Web**, không phải hướng dẫn chạy service
- [Ubuntu acceptance](docs/qa/WEEK4_UBUNTU_ACCEPTANCE.md) — checklist đang DEFERRED

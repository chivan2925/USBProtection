# USBShield Ubuntu — Hệ thống ngăn chặn và quản lý USB Flash Disk trên Ubuntu

> Seminar 4 tuần / demo trong LAN. Ubuntu Client: **24.04 LTS amd64**. Backend: **Java 21 + Spring Boot**. Admin Web: **React + Vite + TypeScript**. USB enforcement: **USBGuard + systemd**. Database demo: **H2 file mode**.

## Tổng quan

| Thành phần | Vai trò |
|---|---|
| `admin-server/` | Spring Boot: Admin login, Agent enrollment/heartbeat, per-endpoint policy, audit/event API, H2 |
| `frontend/` | React Admin Web, build thành static assets và phục vụ từ cùng JAR Admin Server |
| `client-agent/` | Agent không có Web; gửi event/heartbeat, đồng bộ chính sách USBGuard |
| `packaging/` | Debian `.deb`, `systemd`, USBGuard base rule, notifier helper |
| `docs/` | Blueprint, API/contract, guide, QA/test/evidence và tài liệu kỹ thuật |

### Nguyên tắc cốt lõi

1. USB Mass Storage class `08` chưa được cấp phép phải BLOCK tại Client theo USBGuard local policy; non-storage/HID bình thường không bị chặn theo base rule.
2. Admin Allow/Revoke theo **từng endpoint**, Agent đồng bộ local policy. Người dùng **rút USB rồi cắm lại** để áp dụng quyết định mới; không yêu cầu thay đổi runtime ngay trên thiết bị vẫn đang cắm.
3. Sự kiện ghi nhận endpoint, USB và **tài khoản/session Ubuntu local active**, không khẳng định ai trực tiếp cắm vật lý.
4. Normal user không có sudo/root hoặc quyền sửa USBGuard IPC/policy không được tắt/sửa protection. Root vẫn có thể quản trị hệ thống.
5. Admin Web dành cho **Admin**; Ubuntu Client chỉ chạy Agent và truy cập `/api/agent/**`. Nếu muốn chặn cả trang đăng nhập từ client subnet, cấu hình reverse proxy/firewall.

## Bắt đầu

**Tất cả bước build, test, chạy Admin Server, HTTPS/LAN, đóng gói `.deb`, cấu hình Ubuntu Client và kịch bản Allow/Revoke nằm ở đúng một tài liệu:**

**[docs/RUN_GUIDE.md](docs/RUN_GUIDE.md)**

Không cần Docker cho bản demo một server. Ubuntu E2E còn **DEFERRED/NOT EXECUTED**; Windows Maven/frontend/browser không phải kết quả phần cứng thật.

## Tài liệu khác

- [Blueprint chính thức](USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Developer Guide](docs/DEVELOPER_GUIDE.md) — kỹ thuật mã nguồn và API, không phải runbook vận hành
- [API](docs/API.md) · [Agent–Server protocol](docs/AGENT_SERVER_PROTOCOL.md)
- [Admin Guide](docs/ADMIN_GUIDE.md) — hướng dẫn **sử dụng Web**, không phải hướng dẫn chạy service
- [Ubuntu acceptance](docs/qa/WEEK4_UBUNTU_ACCEPTANCE.md) — checklist đang DEFERRED

# USBShield Ubuntu — Hệ thống ngăn chặn và quản lý USB Flash Disk trên Ubuntu

> **Source baseline đối chiếu:** branch `vinhkhang` commit `4d2216c7dd277c7ef4af1962e0b04638e3971d09` (07/11/2026). **Windows verifier PASS trên máy owner 09/11/2026** sau bản sửa thứ tự build JAR (chưa commit tại SHA này). Ubuntu 24.04 hardware E2E và LAN HTTPS/ACL vẫn **NOT VERIFIED**. Xem [status 09/11](docs/IMPLEMENTATION_STATUS_20261109.md).

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

## Trạng thái kỹ thuật và nghiệm thu hiện tại (09/11/2026)

- **Lịch sử:** ba đợt correctness/reliability/HTTPS-paging (04/11) được giữ theo mã finding riêng; **không trộn** với review mới F01–F14. Review mới đã sửa F02–F06, F08–F11, F14; F07 là quyết định giữ audit orphan có chủ đích; F01 Spring Boot OSS upgrade **không nằm trong phạm vi sửa demo**; F12 request trùng và F13 notification best-effort **được owner chấp nhận/defer**. Xem [review ledger 09/11](docs/qa/2026-11-09-review-and-packaging-sync.md).
- **Bằng chứng Windows được owner cung cấp (09/11):** `verify-windows.ps1` PASS; Admin **60 tests PASS**, Agent **276 tests (273 PASS, 3 Linux-only SKIP)**, frontend **116 PASS**, lint/build PASS, Browser Live và HTTP Live trên PostgreSQL test DB PASS; `.deb` build và kiểm cấu trúc/MD5/SHA256 PASS. Bản sửa verifier phải được giữ: **build JAR sau Maven clean test và trước live browser tests**.
- **Chưa được nghiệm thu:** Nginx HTTPS/ACL từ IP nguồn Admin/Ubuntu thật, Agent Java trust CA/SAN, `.deb` install/upgrade/remove, USBGuard real, notifier GUI và USB flash Allow/Revoke/replug trên Ubuntu. Không suy ra các việc này từ Windows PASS.

## Bắt đầu

**Tất cả bước build, test, chạy Admin Server, HTTPS/LAN, đóng gói `.deb`, cấu hình Ubuntu Client và kịch bản Allow/Revoke nằm ở đúng một tài liệu:**

**[docs/RUN_GUIDE.md](docs/RUN_GUIDE.md)**

Build/test hiện hành trên Windows: `./packaging/tests/verify-windows.ps1` (PowerShell 7.4+). Mỗi build `.deb` sinh `packaging/dist/usbshield-agent.release.json` (Version riêng, SHA256, Git SHA) và gói `.deb` tương ứng. Ubuntu tối giản: [Minimal Core E2E](docs/USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md); [HTTPS `usbprotection.lab` chi tiết](docs/USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md). Trạng thái các gate B01–B05 cần đối chiếu bằng chứng tương ứng; Windows PASS không tự đóng các mục native.

PostgreSQL phải được cài/khởi động và có database cùng role riêng cho ứng dụng. Dev/test trên Windows có thể dùng `.env` (không commit) với `scripts/with-env.ps1` để nạp test credentials; xem runbook. **Không chạy baseline Flyway trên database test.** Admin Server vẫn là **một JAR**, Client Agent vẫn là **`.deb`**; không cần Docker cho bản demo một server. Ubuntu E2E còn **DEFERRED/NOT EXECUTED**; Windows Maven/frontend/browser (PostgreSQL test DB, dữ liệu tổng hợp) không phải kết quả phần cứng thật. Chưa có bằng chứng Ubuntu hardware E2E từ lần đổi database.

## Tài liệu khác

- [Blueprint chính thức](USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md)
- [Trạng thái cập nhật 09/11 và phân loại review F01–F14](docs/IMPLEMENTATION_STATUS_20261109.md) · [Bản lịch sử 04/11](docs/IMPLEMENTATION_STATUS_20261104.md)
- [Flyway baseline cho PostgreSQL hiện hữu](admin-server/ops/POSTGRES_FLYWAY_BASELINE.md) · [HTTPS/ACL và API phân trang](docs/PHASE3_HTTPS_ACL_AND_PAGING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Developer Guide](docs/DEVELOPER_GUIDE.md) — kỹ thuật mã nguồn và API, không phải runbook vận hành
- [API v0.2](docs/API.md) · [Addendum API Phase 1–3](docs/API_PHASE1_TO_PHASE3_ADDENDUM.md) · [Agent–Server protocol](docs/AGENT_SERVER_PROTOCOL.md)
- [Admin Guide](docs/ADMIN_GUIDE.md) — hướng dẫn **sử dụng Web**, không phải hướng dẫn chạy service
- [Ubuntu acceptance](docs/qa/WEEK4_UBUNTU_ACCEPTANCE.md) — checklist đang DEFERRED

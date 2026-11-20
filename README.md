# USBShield Ubuntu — Hệ thống ngăn chặn và quản lý USB Flash Disk trên Ubuntu

> **Source kiểm tra tài liệu 20/11/2026:** `main` HEAD [`721f164f63533461b6c18b62e1607baa4ab1b731`](https://github.com/chivan2925/USBProtection/commit/721f164f63533461b6c18b62e1607baa4ab1b731), gồm các script được đưa vào tại [`79384e2`](https://github.com/chivan2925/USBProtection/commit/79384e26563ea08db7c16c223c618e1f51e9a3d9). Đây là mốc **source/script**, không tự chứng minh các test hay artifact binary tại mốc này đã PASS. **Bằng chứng lịch sử 09/11/2026:** Windows verifier PASS theo log owner; HTTPS/ACL VMnet8 PASS tại lab; Ubuntu Minimal Core P0 + C1–C5 PASS theo báo cáo owner. Extended Ubuntu E2E: **DEFERRED BY OWNER / NOT EXECUTED**. Xem [owner E2E closeout](docs/qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md) và [snapshot trạng thái 09/11](docs/IMPLEMENTATION_STATUS_20261109.md).

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

## Trạng thái kỹ thuật và nghiệm thu (các bằng chứng lịch sử ngày 09/11/2026)

- **Lịch sử:** ba đợt correctness/reliability/HTTPS-paging (04/11) được giữ theo mã finding riêng; **không trộn** với review mới F01–F14. Review mới đã sửa F02–F06, F08–F11, F14; F07 là quyết định giữ audit orphan có chủ đích; F01 Spring Boot OSS upgrade **không nằm trong phạm vi sửa demo**; F12 request trùng và F13 notification best-effort **được owner chấp nhận/defer**. Xem [review ledger 09/11](docs/qa/2026-11-09-review-and-packaging-sync.md).
- **Bằng chứng Windows do owner cung cấp (09/11):** `verify-windows.ps1` PASS tại source lúc đó; Admin **60 tests PASS**, Agent **276 tests (273 PASS, 3 Linux-only SKIP)**, frontend **116 PASS**, lint/build PASS, Browser Live và HTTP Live trên PostgreSQL test DB PASS; `.deb` build và kiểm cấu trúc/MD5/SHA256 PASS. Đây **không phải kết quả của HEAD 20/11**.
- **Đã kiểm tại lab theo owner:** Windows Nginx HTTPS Admin login; Ubuntu A/B `403/403/403/401` trên bốn path; bypass 18080/5432 không truy cập được; IP nguồn Nginx A/B tách biệt. Ubuntu Agent `.deb` bản `1.0.0+build20261109091706653` hiện diện trên cả hai VM, C1–C5 (BLOCK/Allow/replug/isolation/Revoke) **PASS theo lời owner**. Không có log C1–C5 đầy đủ để review độc lập; Java Agent enrollment/auth thành công được **suy luận từ kết quả Core PASS**, không dùng log heartbeat 401 trước remediation làm bằng chứng. **Chưa chạy và owner hoãn:** `.deb` remove/upgrade, negative TLS, offline/recovery, reboot, Linux-only notifier IPC và các edge case.
- **20/11:** `main` đã thay đổi release build và Windows verifier. Không tái sử dụng số lượng test hoặc hash cũ làm kết quả cho source mới; phải chạy lại các gate trên checkout thực tế, ghi commit SHA và log mới.

## Bắt đầu

**[docs/RUN_GUIDE.md](docs/RUN_GUIDE.md)** là runbook triển khai đầy đủ (PostgreSQL, Admin Server, HTTPS/LAN, `.deb` Ubuntu, Allow/Revoke). **[docs/ADMIN_SERVER_RELEASE.md](docs/ADMIN_SERVER_RELEASE.md)** là hướng dẫn tập trung cho **build/phân phối JAR, kiểm thử Windows và nạp cấu hình runtime**.

Tại root repository, dùng PowerShell 7.6+ trên Windows:

```powershell
# Chỉ kiểm tra Admin Server + React + các live suites; dùng .env TEST.
.\packaging\tests\verify-windows.ps1 -AdminOnly

# Toàn bộ gate Windows, gồm Agent và .deb.
.\packaging\tests\verify-windows.ps1

# Build JAR riêng; kiểm tra Git working tree sạch, tests chạy theo mặc định.
.\admin-server\build-release.ps1
```

`build-release.ps1` sinh `admin-server/target/usbshield-admin-server.jar` và bản phát hành đặt trong `admin-server/target/release/` (hoặc `-OutputDirectory`), kèm manifest `.json` và checksum `.sha256`. JAR chứa frontend React và Flyway migrations; không cần cung cấp source frontend hay npm cho máy chỉ chạy JAR. **JAR không chứa PostgreSQL, `.env`, mật khẩu hoặc chứng chỉ TLS.** Chỉ phân phối bản release đạt gate cần thiết và ghi chính xác SHA-256 của artifact.

**Tách cấu hình:** `.env` (không commit) **chỉ dành cho test** (`USBSHIELD_TEST_DB_*`, role `usbshield_test`, database `usbshield_test`/`usbshield_browser_test`). `.env.e2e.local` (không commit) là cấu hình **runtime `prod` tại lab E2E**, không phải database test. `java -jar ... --spring.profiles.active=prod` **không tự đọc `.env.e2e.local`**; cần nạp biến môi trường trước, ví dụ:

```powershell
.\scripts\with-env.ps1 -EnvFile .env.e2e.local -Command {
    java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod
}
```

Khi biến runtime đã được thiết lập sẵn ở hệ điều hành/PowerShell, có thể gọi `java -jar ...` trực tiếp. **Profile `prod` yêu cầu `USBSHIELD_DB_URL`, `USBSHIELD_DB_PASSWORD` và `USBSHIELD_ENROLLMENT_TOKEN`**; PostgreSQL dịch vụ riêng phải đang hoạt động. `build-release.ps1` nạp `.env` trong lúc test và khôi phục môi trường sau build; nó **không cấu hình runtime cho lần chạy JAR sau này**.

Windows gate không thay thế HTTPS/ACL từ Client IP hoặc Ubuntu USBGuard E2E. Nginx/TLS, CA trust, firewall và ACL cấu hình **ngoài JAR**. Tránh đưa bí mật `.env.e2e.local`, token hoặc database dump vào GitHub Release.

## Tài liệu khác

- [Blueprint chính thức](USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md)
- [Trạng thái/snapshot review 09/11 và phân loại F01–F14](docs/IMPLEMENTATION_STATUS_20261109.md) · [Bản lịch sử 04/11](docs/IMPLEMENTATION_STATUS_20261104.md)
- [Admin Server release, JAR và runtime](docs/ADMIN_SERVER_RELEASE.md) · [Flyway baseline cho PostgreSQL hiện hữu](admin-server/ops/POSTGRES_FLYWAY_BASELINE.md)
- [HTTPS/ACL và API phân trang](docs/PHASE3_HTTPS_ACL_AND_PAGING.md) · [Cấu hình HTTPS lab](docs/USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md)
- [Architecture — current runtime](docs/ARCHITECTURE.md) · [Tổng rà soát và phân loại docs (20/11)](docs/DOCUMENTATION_AUDIT_20261120.md)
- [Developer Guide](docs/DEVELOPER_GUIDE.md) — kỹ thuật mã nguồn và API, không phải runbook vận hành
- [API v0.2 — hợp đồng đóng băng](docs/API.md) · [Addendum API Phase 1–3](docs/API_PHASE1_TO_PHASE3_ADDENDUM.md) · [Agent–Server protocol v0.2](docs/AGENT_SERVER_PROTOCOL.md)
- [Admin Guide](docs/ADMIN_GUIDE.md) — hướng dẫn **sử dụng Web**, không phải chạy service · [Demo hiện hành](docs/DEMO_RUNBOOK_20261120.md) · [Kịch bản Week 4 lịch sử](docs/DEMO_SCRIPT.md)
- [Ubuntu Minimal Core E2E](docs/USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md) · [Ubuntu acceptance mở rộng](docs/qa/WEEK4_UBUNTU_ACCEPTANCE.md) (**DEFERRED BY OWNER**)

**Closeout lịch sử:** [Owner acceptance P0/C1–C5](docs/qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md) · [M4/M5 UI guardrails](docs/admin-web/POST_CORE_E2E_UI_CHANGE_GUARDRAILS.md).

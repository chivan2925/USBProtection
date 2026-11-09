# USBShield — Current implementation and verification boundary (09/11/2026)

**Source baseline sau docs-sync:** `chivan2925/USBProtection`, branch `vinhkhang`, HEAD **`6923be001890bee9a303a8cee8e7dcc2cf2db180`** (`docs(packaging): sync F01-F14 docs, HTTPS/E2E guides and DEB build scripts`, 09/11/2026). Windows verifier sửa đã được commit trong baseline này. Runtime lab/JAR/DEB SHA chính xác vẫn cần ghi riêng nếu tái lập acceptance.

## Hai hệ mã finding khác nhau

1. **Ba đợt 04/11**, ghi trong [snapshot 04/11](IMPLEMENTATION_STATUS_20261104.md): mật khẩu, HASH, storage, health, Flyway, HTTPS/ACL, pagination. Mã F01–F14 ở đó là **ID của giai đoạn cũ**, không phải các ID bên dưới.
2. **Static review gần nhất (07–09/11):** F01–F14 hiện hành và B01–B05. Xem [QA ledger](qa/2026-11-09-review-and-packaging-sync.md) để phân loại source fix, scope waiver và native verification cần chạy.

## Nội dung sau review hiện hành

| Nhóm | Code/contract hiện tại | Phạm vi còn thiếu |
|---|---|---|
| F02/F03 | Outbox quarantine lỗi 400 (trừ future-skew), 409, 413, 422; 400 future-skew/401/403/network/5xx vẫn được retry; bootstrap Admin từ chối mật khẩu yếu | Mô phỏng lỗi payload/auth thật trên Ubuntu; kiểm secure bootstrap |
| F04/F05 | USBGuard health kiểm tra hiệu lực/thứ tự rule, không dùng ACK thay enforcement; Debian `prerm` có quy trình cleanup managed rules có kiểm tra | Native USBGuard daemon + upgrade/remove và persistence/reboot |
| F06/F08 | Bounded Admin events page/query; giới hạn output `loginctl` khi chọn GUI session | Vận hành thực và regression khi output bất thường |
| F09/F11 | Endpoint unknown filter/UI không báo sai trạng thái bảo vệ; unknown phải trình bày là chưa xác minh | E2E trên nguồn dữ liệu thật và trạng thái Agent thực |
| F10/F14 | Windows `.deb` version theo UTC build stamp + `release.json`; docs/script references được đồng bộ | Install/upgrade thật và reproducibility khi working tree dirty |
| F07 | Giữ lịch sử `usb_events.usb_device_id` orphan theo contract, **không tạo FK mới khiến mất audit cũ** | Chỉ xem xét thay đổi nếu owner sửa policy giữ audit |
| F01/F12/F13 | **Owner-deferred/accepted:** không nâng Spring Boot OSS trong bản demo; không đổi thêm duplicate calls; notifier best-effort drop không bảo đảm delivery | Không được tuyên bố resolved/fully fixed |

## Evidence owner 09/11/2026 (Windows 11 / PowerShell 7.6.9)

| Gate | Evidence đọc được từ log đính kèm | Kết luận có giới hạn |
|---|---|---|
| Admin Maven `clean test` | 60 tests, 0 failed/error/skipped; BUILD SUCCESS | **PASS trên máy owner** với PostgreSQL test DB |
| Agent Maven `clean test` | 276 tests, 0 failed/error, **3 skipped** | **PASS phần Windows; Linux-only SKIP** |
| Frontend | 116 tests PASS; lint 0 errors/warnings; production build PASS | **PASS** |
| Admin JAR | Repackage Spring Boot PASS; `BOOT-INF/classes/static/index.html` được verifier kiểm | **PASS**; build phải sau `clean test` |
| Browser Live + HTTP Live | Cả hai PASS trên schema cô lập `usbshield_browser_test`; HTTP QA events/ACK tổng hợp | **PASS synthetic**, không phải USBGuard hardware |
| Windows `.deb` | DEB built; version `1.0.0+build...`, `release.json` trùng SHA256; `ar`, tar.gz, modes, `md5sums`, JAR entries PASS | **PASS format/contents**, không phải Ubuntu dpkg runtime |

**Artifact SHA256 theo log owner:** `E67C53C75728C4438227C3CD695DEDB4724D9D5C8CA6418D88D620D646EF4406` của bản build cụ thể. Hash này **không áp dụng cho các build tiếp theo**. Git HEAD trong manifest chỉ là SHA commit nền, **không chứng minh working tree sạch**. Không ghi thông tin đăng nhập/secrets trong evidence.

## Native acceptance — Minimal Core đã PASS theo owner; phần mở rộng được hoãn

- **Windows HTTPS/ACL:** Nginx TLS/443 → Spring loopback 18080, PostgreSQL localhost 5432; Admin login bằng browser thật; Ubuntu A/B nhận `403/403/403/401` cho Web/Auth/Admin/Agent-unauth; TCP/18080 và 5432 không bypass được; Nginx thấy đúng source IP .133/.141. **PASS theo owner**, không phải synthetic Windows verifier.
- **Ubuntu A/B:** cả hai có `.deb` version `1.0.0+build20261109091706653` (theo logs). **P0 và C1–C5 PASS theo báo cáo owner 09/11** — USB Kingston: A BLOCK → Allow A/ACK → replug A ALLOW → B BLOCK → Revoke A/ACK → replug A BLOCK. Không có raw per-checkpoint output kèm theo, nên gọi `OWNER_REPORTED_PASS`, không gọi independent verified. Chi tiết: [owner closeout](qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md).
- **P0 historical incident:** trước khi fix, hai Agent heartbeat 401, local `endpoint.json` là ID của 02/11 và SELECT đối chiếu DB hiện hành trả 0 hàng; không dùng trạng thái lỗi trước remediation để đánh giá final pass.
- **DEFERRED BY OWNER / NOT EXECUTED:** `.deb` remove/upgrade, `prerm` cleanup, reboot, offline recovery/outbox, bad-CA/SAN TLS negative, Linux-only notifier IPC, device edge cases, stress/race và security hardening/full Week4 acceptance. Mã B01–B05 và finding owner-waived không tự đóng vì C1–C5 PASS.
- **M4/M5 UI change-control:** [guardrails](admin-web/POST_CORE_E2E_UI_CHANGE_GUARDRAILS.md); CSS-only cần frontend/browser smoke, đổi Allow/Revoke/auth/mapping phải re-run Core C1–C5 trên build mới.

## Canonical next steps

1. [Run Guide](RUN_GUIDE.md): chuẩn bị PostgreSQL 3 DB/role và build/test Windows; script tổng `packaging/tests/verify-windows.ps1`.
2. [HTTPS `usbprotection.lab`](USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md): Windows Nginx, CA, ACL; thử từ Admin và Ubuntu riêng biệt.
3. [Minimal Core Ubuntu E2E](USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md) + [owner closeout](qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md): kết quả P0/C1–C5 đã PASS theo owner, cần lưu evidence và artifact SHA nếu audit.
4. [M4/M5 UI guardrails](admin-web/POST_CORE_E2E_UI_CHANGE_GUARDRAILS.md): tiếp tục polish UI trong scope có kiểm soát; không tự chuyển PASS cũ sang code mới. [Ubuntu acceptance mở rộng](qa/WEEK4_UBUNTU_ACCEPTANCE.md) **được owner hoãn, chưa thực hiện**.

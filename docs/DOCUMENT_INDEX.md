# USBProtection — Danh mục tài liệu hiện hành và lịch sử

> **Source đối chiếu docs:** `main` `c7381fc7b9ba34aa561083431ce7c68b9a514a51` (20/11/2026); release/Windows scripts from `79384e26563ea08db7c16c223c618e1f51e9a3d9`. **Evidence lab lịch sử 09/11/2026:** Windows verifier PASS theo log; HTTPS/ACL và Ubuntu **P0+C1–C5 PASS theo owner** trên baseline lab khi đó, không phải independent re-run của HEAD hiện tại. Extended Ubuntu acceptance **DEFERRED BY OWNER**.

## Tài liệu hiện hành (sử dụng theo mục đích)

| Mục đích | Tài liệu |
|---|---|
| **Runbook tổng hợp: build/test, PostgreSQL, Spring JAR, Client DEB** | **[RUN_GUIDE.md](RUN_GUIDE.md)** |
| **Đồng bộ sau khi xóa Settings (HEAD `c7381fc7`)** | **[DOCUMENTATION_SYNC_20261120_c7381fc7.md](DOCUMENTATION_SYNC_20261120_c7381fc7.md)** — phạm vi thay đổi, evidence Windows/build và file lịch sử giữ nguyên; [audit trước đó](DOCUMENTATION_AUDIT_20261120.md) là snapshot tại `721f164` |
| **Admin Web: Login/Dashboard/Endpoints/Allow/Revoke/Events; Settings đã bị xóa** | **[ADMIN_GUIDE.md](ADMIN_GUIDE.md)** |
| **Kiến trúc runtime PostgreSQL, Nginx HTTPS và Agent USBGuard** | **[ARCHITECTURE.md](ARCHITECTURE.md)** |
| **Demo mới và rehearsal Week 4 lịch sử** | **[DEMO_RUNBOOK_20261120.md](DEMO_RUNBOOK_20261120.md)** (hiện hành); [DEMO_SCRIPT.md](DEMO_SCRIPT.md) (snapshot lịch sử) |
| **Admin JAR: Windows verifier, `build-release.ps1`, `.env` vs `.env.e2e.local`, phân phối và chạy `prod`** | **[ADMIN_SERVER_RELEASE.md](ADMIN_SERVER_RELEASE.md)** |
| **Cấu hình HTTPS `usbprotection.lab` từng bước, Windows Nginx + CA + Ubuntu A/B** | **[USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md](USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md)** |
| **Core E2E USB Kingston trên Ubuntu A/B** | **[USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md](USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md)** (giữ filename lịch sử, nội dung cập nhật) |
| **Owner sign-off Minimal Core 09/11 (có ranh giới evidence)** | [qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md](qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md) |
| **M4/M5: giới hạn sửa UI sau E2E** | [admin-web/POST_CORE_E2E_UI_CHANGE_GUARDRAILS.md](admin-web/POST_CORE_E2E_UI_CHANGE_GUARDRAILS.md) |
| **Snapshot 09/11: trạng thái code, review F01–F14 và Windows PASS lịch sử** | **[IMPLEMENTATION_STATUS_20261109.md](IMPLEMENTATION_STATUS_20261109.md)**; [QA ledger](qa/2026-11-09-review-and-packaging-sync.md). Không coi là test mới cho HEAD 20/11. |
| Cấu hình HTTPS/ACL contract và API phân trang | [PHASE3_HTTPS_ACL_AND_PAGING.md](PHASE3_HTTPS_ACL_AND_PAGING.md) |
| Phạm vi chức năng và hợp đồng | [Blueprint](../USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md), [API v0.2 — frozen](API.md), [API addendum — Phase 1–3](API_PHASE1_TO_PHASE3_ADDENDUM.md), [Agent–Server protocol v0.2](AGENT_SERVER_PROTOCOL.md) |
| Hướng dẫn phát triển / dùng Web | [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md), [ADMIN_GUIDE.md](ADMIN_GUIDE.md) |
| PostgreSQL migration cho **database hiện hữu, có dữ liệu** | [POSTGRES_FLYWAY_BASELINE.md](../admin-server/ops/POSTGRES_FLYWAY_BASELINE.md) — preflight + backup + baseline V1 một lần; **không** baseline DB trống/test |
| Danh mục/plan kiểm thử | [TEST_CATALOG.md](TEST_CATALOG.md) (entrypoints hiện hành), [TEST_PLAN.md](TEST_PLAN.md) (kế hoạch lịch sử), [Ubuntu acceptance](qa/WEEK4_UBUNTU_ACCEPTANCE.md) (native extended) |
| Tham chiếu USBGuard / M2 | [usbguard/WEEK4_M2_TECHNICAL_QA.md](usbguard/WEEK4_M2_TECHNICAL_QA.md) |

## Scripts/tests hiện hành tại source baseline 20/11

- **Admin JAR release:** `admin-server/build-release.ps1` (PowerShell 7, dùng `.env` test khi không `-SkipTests`; yêu cầu clean tree trừ khi `-AllowDirtyTree`; JAR + manifest + `.sha256` ở `admin-server/target/release/` mặc định). Không nhập `.env.e2e.local` vào JAR.
- **Windows verification canonical:** `packaging/tests/verify-windows.ps1` (Windows PowerShell **7.6+**, nguồn `admin-server/target/usbshield-admin-server.jar`); `-AdminOnly` bỏ Agent/DEB; mặc định chạy các gate Admin, frontend, Agent và `.deb`. Hai live suites dùng DB `usbshield_browser_test` và schema test riêng. Kết quả PASS chỉ được xác nhận sau khi chạy trên source hiện hành.
- **Nạp biến môi trường cho một lệnh:** `scripts/with-env.ps1 -EnvFile .env.e2e.local -Command { java -jar ... --spring.profiles.active=prod }`. `prod` không tự đọc `.env.e2e.local`.
- **Windows-native Agent `.deb` builder:** `packaging/scripts/build-deb.ps1`; **Linux/Ubuntu builder:** `packaging/scripts/build-deb.sh`. Hai builder dùng manifest `packaging/dist/usbshield-agent.release.json` và SHA256.
- **Native Ubuntu/HTTPS checks:** `packaging/tests/check-usbshield-core.sh`, `packaging/tests/test-notifier-ipc-ubuntu.sh`, `packaging/tests/verify-phase3-https-acl.sh` (chạy từ **IP nguồn thực**). `verify-phase3-https-acl.sh` **chưa được gộp** vào Windows verifier; giữ script để kiểm ACL từ phía Admin/Client. Không coi đây là Windows verification.
- **Không còn là workflow hiện hành:** `admin-server/verify-week1.ps1`, `verify-week2.ps1`, `verify-week3.ps1`, cùng các `verify-week*-windows.ps1`, `prepare-staging.ps1` và `apply-fixes.ps1` cũ. Giữ nguyên tên trong báo cáo lịch sử; không chạy lại Week 3 verifier có `ddl-auto=create`.

## Tài liệu/bằng chứng lịch sử — không sửa retroactive

- [IMPLEMENTATION_STATUS_20261104.md](IMPLEMENTATION_STATUS_20261104.md) mô tả **ba đợt sửa trước**. Finding ID trong đó **khác phạm vi** static review F01–F14 ngày 07–09/11.
- `API.md` và `AGENT_SERVER_PROTOCOL.md` giữ **wire contract v0.2 đã freeze**: tên file có Week 2 là provenance, không phải lý do tự phát minh v0.3. Addendum Phase 1–3 bổ sung API/runtime mới. `admin-web/SCALABILITY_PHASE3.md` giữ snapshot Phase 3, còn trang Blocked riêng đã được gộp vào bộ lọc Devices trong giao diện hiện hành.
- `DEMO_SCRIPT.md` là rehearsal Week 4, không phải kịch bản hiện hành; xem `DEMO_RUNBOOK_20261120.md`.
- Các báo cáo `docs/member1/`, `docs/client-agent/WEEK*.md`, `docs/admin-web/WEEK*.md`, `docs/usbguard/WEEK*.md`, `docs/rc/`, `docs/qa/` và thư mục evidence vẫn giữ thời điểm/SHA/test mà chúng ghi nhận. H2 và các tên `.deb` cố định trong evidence cũ không phải runtime hiện tại.
- `CLIENT_INSTALLATION.md` là redirect lịch sử tới `RUN_GUIDE.md`. Không dùng hướng dẫn staging/patch cũ.

**Giới hạn hiện tại:** Bằng chứng Windows/HTTPS/Ubuntu PASS ngày 09/11 thuộc baseline và artifact của lần đó. Owner **báo PASS cho gate Windows đã thực thi ngày 20/11** sau sửa Settings/tests; JAR trong `releases/` mang source `b12bb597` với dirty working tree, **không fully release-qualified**. Không suy ra native HTTPS/Ubuntu acceptance mới chỉ từ Windows PASS. Ubuntu extended: Debian remove/upgrade/prerm cleanup, offline/reboot, negative TLS, Linux-only notifier IPC, stress/security vẫn deferred. Không đóng B01–B05 hoặc đánh dấu full production acceptance.

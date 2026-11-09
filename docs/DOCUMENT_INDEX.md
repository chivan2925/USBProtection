# USBShield — Danh mục tài liệu hiện hành và lịch sử

> Baseline đối chiếu: branch `vinhkhang`, `4d2216c7dd277c7ef4af1962e0b04638e3971d09` (07/11/2026). Cập nhật tài liệu và Windows verifier từ log owner **09/11/2026**. Không coi đây là Ubuntu E2E sign-off.

## Tài liệu hiện hành (sử dụng theo mục đích)

| Mục đích | Tài liệu |
|---|---|
| **Runbook tổng hợp: build/test, PostgreSQL, Spring JAR, Client DEB** | **[RUN_GUIDE.md](RUN_GUIDE.md)** |
| **Cấu hình HTTPS `usbprotection.lab` từng bước, Windows Nginx + CA + Ubuntu A/B** | **[USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md](USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md)** |
| **Core E2E USB Kingston trên Ubuntu A/B** | **[USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md](USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md)** (giữ filename lịch sử, nội dung cập nhật) |
| **Trạng thái code / bằng chứng sau review F01–F14 và Windows PASS** | **[IMPLEMENTATION_STATUS_20261109.md](IMPLEMENTATION_STATUS_20261109.md)**; [QA ledger chi tiết](qa/2026-11-09-review-and-packaging-sync.md) |
| Cấu hình HTTPS/ACL contract và API phân trang (khái quát) | [PHASE3_HTTPS_ACL_AND_PAGING.md](PHASE3_HTTPS_ACL_AND_PAGING.md) |
| Phạm vi chức năng và hợp đồng | [Blueprint](../USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md), [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), [API addendum](API_PHASE1_TO_PHASE3_ADDENDUM.md), [AGENT_SERVER_PROTOCOL.md](AGENT_SERVER_PROTOCOL.md) |
| Hướng dẫn phát triển / dùng Web | [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md), [ADMIN_GUIDE.md](ADMIN_GUIDE.md) |
| PostgreSQL migration cho **database hiện hữu, có dữ liệu** | [POSTGRES_FLYWAY_BASELINE.md](../admin-server/ops/POSTGRES_FLYWAY_BASELINE.md) — preflight + backup + baseline V1 một lần; **không** baseline DB trống/test |
| Danh mục/plan kiểm thử | [TEST_CATALOG.md](TEST_CATALOG.md), [TEST_PLAN.md](TEST_PLAN.md), [Ubuntu acceptance](qa/WEEK4_UBUNTU_ACCEPTANCE.md) |
| Tham chiếu USBGuard / M2 | [usbguard/WEEK4_M2_TECHNICAL_QA.md](usbguard/WEEK4_M2_TECHNICAL_QA.md) |

## Scripts/tests đang dùng

- Windows build + regression gate: `packaging/tests/verify-windows.ps1` — PowerShell 7.4+, log owner sử dụng **7.6.9**, build Admin JAR **sau** `clean test`, trước browser suites, rồi build/kiểm `.deb`.
- Native Windows package builder: `packaging/scripts/build-deb.ps1`; Linux/Ubuntu package builder: `packaging/scripts/build-deb.sh`. Cả hai sinh `.deb` có version riêng và `packaging/dist/usbshield-agent.release.json`.
- Native checks trên Ubuntu: `packaging/tests/check-usbshield-core.sh`, `packaging/tests/test-notifier-ipc-ubuntu.sh`, `packaging/tests/verify-phase3-https-acl.sh` (chạy từ **IP nguồn thực**). Không gọi các script này là Windows verification.
- Không còn `prepare-staging.ps1`, `verify-week2-windows.ps1`, `verify-week3-windows.ps1`, `verify-week4-windows.ps1` hoặc script `apply-fixes.ps1` thuộc luồng **hiện hành**. Nếu gặp tên trong báo cáo Week 1–4, đó là lịch sử.

## Tài liệu/bằng chứng lịch sử — không sửa retroactive

- [IMPLEMENTATION_STATUS_20261104.md](IMPLEMENTATION_STATUS_20261104.md) mô tả **ba đợt sửa trước**. Finding ID trong đó **khác phạm vi** static review F01–F14 ngày 07–09/11. Coi là snapshot, không đánh đồng F01 của hai lần review.
- Các report `docs/member1/`, `docs/client-agent/WEEK*.md`, `docs/admin-web/WEEK*.md`, `docs/usbguard/WEEK*.md`, `docs/rc/`, `docs/qa/` và thư mục evidence vẫn giữ thời điểm/SHA/test mà chúng ghi nhận. H2 và các tên `.deb` cố định trong evidence cũ không phải runtime hiện tại.
- `CLIENT_INSTALLATION.md` là redirect lịch sử tới `RUN_GUIDE.md`. Không dùng các bản hướng dẫn staging/patch cũ.

**Gate cuối:** Windows verifier **PASS theo log 09/11** chỉ xác minh Windows build, Java/frontend/browser synthetic, format `.deb`. Cần bằng chứng riêng cho HTTPS/ACL mạng thực, Ubuntu Debian install/remove, USBGuard hardware BLOCK/ALLOW/REVOKE, GUI popup, offline/reboot và Linux-only notifier tests trước khi công bố nghiệm thu Ubuntu.

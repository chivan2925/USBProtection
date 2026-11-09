# USBShield — Static review F01–F14 và packaging/scripts/tests synchronization (09/11/2026)

**Baseline code:** `vinhkhang` `4d2216c7dd277c7ef4af1962e0b04638e3971d09`. **Evidence:** owner-supplied `verify-windows.ps1` output 09/11/2026. Tài liệu được điều chỉnh theo code + bằng chứng, **không chạy E2E native Ubuntu**.

**Cảnh báo mã ID:** review F01–F14 dưới đây là *static review gần nhất*. Đừng nhầm với F01–F14 của ba đợt trước 04/11 trong `IMPLEMENTATION_STATUS_20261104.md` hoặc các `docs/qa/2026-11-02-F01-F11-*`.

| ID | Review finding gần nhất | Quyết định/mức xác minh |
|---|---|---|
| **F01** | Spring Boot 3.3.4 không còn được OSS support | **DEFERRED/owner demo scope**, không nâng version; không gọi là fixed |
| **F02** | Outbox bị block FIFO vô hạn bởi HTTP 400/409/413/422 vĩnh viễn | Code phân loại vĩnh viễn/quarantine và retry transient; JUnit `OutboxPermanentRejectionRegressionTest` PASS trên Windows; native audit persistence cần test |
| **F03** | Bootstrap password quá yếu | Admin password-policy enforcement; JUnit `AdminBootstrapPasswordPolicyTest` PASS |
| **F04** | Health bỏ sót DENY/block đứng trước Allow | `UsbGuardPolicyOrderSafety` và probe; `UsbGuardPolicyOrderHealthRegressionTest` PASS trên Windows, Ubuntu real order cần test |
| **F05** | Debian `prerm` để lại `usbshield-managed` Allow | `packaging/agent-deb/DEBIAN/prerm` đã sửa; Windows `.deb` mode/bytes PASS; Ubuntu remove/upgrade **chưa chạy** |
| **F06** | Event page/query không bounded | `AdminEventController`/`AdminEventPagingLimitTest` PASS |
| **F07** | Relational FK bị thiếu | **Không thêm FK** theo audit historical orphan contract; xem `docs/qa/FINDING_F07_RELATIONAL_CONTRACT.md` |
| **F08** | `loginctl` stdout không bounded | `ProcessLoginctlGateway` giới hạn capture; cần Ubuntu runtime stress check |
| **F09** | Unknown filter danh sách endpoint rỗng giả | UI lấy dữ liệu/filter thay vì tự trả empty placeholder; frontend tests PASS; kiểm live data |
| **F10** | `.deb` giữ tên/version `1.0.0` | Windows builder đã đổi `1.0.0+build<UTC>`, JSON release manifest; Windows package inspect PASS; Linux builder được đồng bộ trong package này (chưa test Ubuntu install) |
| **F11** | UI cho protection fields UNKNOWN thành trạng thái tốt | UI không mặc định Healthy/Protected khi chưa có evidence; cần kiểm với actual Agent/USBGuard |
| **F12** | Frontend duplicate/snapshot HTTP call | Owner **ACCEPTED/NO CHANGE** cho demo; không tự tuyên bố fixed |
| **F13** | Notification best-effort có thể drop | Owner **ACCEPTED/NO CHANGE**; không bảo đảm GUI delivery khi lỗi IPC |
| **F14** | Docs tham chiếu scripts/staging cũ | Đồng bộ README, RUN_GUIDE, index, status, Minimal Core, Phase 3/HTTPS; giữ lịch sử evidence không chỉnh retroactive |

## Các file script/test hiện hành — đã đối chiếu

| Path | Trạng thái 09/11 |
|---|---|
| `packaging/scripts/build-deb.ps1` | Đã PASS PowerShell parser, Windows `.deb` build, ar/tar/md5/sha checks; **giữ code không đổi** |
| `packaging/scripts/build-deb.sh` | Phát hiện khác Windows: version/naming cố định, `staging/` dùng chung. Đồng bộ unique Debian version, `release.json`, control/md5sums, Linux-only staging path; fixture `dpkg-deb`/manifest/md5sums PASS ở sandbox, vẫn cần xác nhận trên Ubuntu. |
| `packaging/tests/verify-windows.ps1` | Đồng bộ bản owner vừa chạy: JAR build sau `clean test` trước browser tests; không gọi các verifier Week 2/3/4 cũ |
| `packaging/tests/check-usbshield-core.sh` | Read-only Ubuntu A/B: service/IPC/base rule, CA/HTTPS test, optional device USBGuard. Không tự chứng minh physical mount, popup hoặc ACK. Giữ nguyên vì phù hợp contract |
| `packaging/tests/verify-phase3-https-acl.sh` | Chạy riêng Admin/Client: 200/401 và Client Web 403 / Agent 401; **không chạy trên Windows để chứng minh Client ACL**. Giữ nguyên |
| `packaging/tests/test-notifier-ipc-ubuntu.sh` | Kiểm socket/ACL thực trên Ubuntu GUI session, không chứng minh popup render. Giữ nguyên |

## Những việc không được suy từ Windows PASS

1. USBGuard rule ordering, Allow/Revoke, mouse/HID, reconnect, agent token + CA/SAN và Nginx source-IP ACL chỉ được khép bằng actual Ubuntu A/B and Windows Nginx acceptance.
2. Debian format PASS không đồng nghĩa `postinst`, `prerm` thành công trên Ubuntu; cần ghi thiết bị, nguồn IP, `dpkg-deb`, `systemctl`, `usbguard list-rules` và nhật ký có redact.
3. Linux builder file sau đồng bộ là script candidate; cần chạy Ubuntu/Linux packaging test để xác nhận artifact và `dpkg -I/-c` trước công bố parity.

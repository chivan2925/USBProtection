# USBShield — Danh mục tài liệu hiện hành và lịch sử

> Kiểm kê trên `vinhkhang` `035b8ed5a4f5b111dace06cf839cf5f1c33f1142` (04/11/2026; chưa merge `main`). Đây là chỉ mục, **không phải** tuyên bố Ubuntu E2E đã hoàn thành.

## Tài liệu dùng hiện tại

| Mục đích | File duy nhất/canonical |
|---|---|
| **Build, test, chạy Admin Server, HTTPS/LAN, build/phân phối/cài Client `.deb`** | **[RUN_GUIDE.md](RUN_GUIDE.md)** — runbook duy nhất |
| Phạm vi đồ án + yêu cầu chức năng | [Blueprint](../USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md) |
| Kiến trúc và giao thức | [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), [API Phase 1–3 addendum](API_PHASE1_TO_PHASE3_ADDENDUM.md), [AGENT_SERVER_PROTOCOL.md](AGENT_SERVER_PROTOCOL.md) |
| Thông tin dành cho developer | [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) |
| **PostgreSQL setup/role, backup và database test isolation** | **[RUN_GUIDE.md](RUN_GUIDE.md)** (mục 2–3) — runbook vận hành chính |
| **Flyway V1/V2: chuyển schema PostgreSQL cũ** | [POSTGRES_FLYWAY_BASELINE.md](../admin-server/ops/POSTGRES_FLYWAY_BASELINE.md) — preflight + backup + baseline một lần |
| **HTTPS, Agent CA/hostname, Nginx/firewall ACL, paging** | [PHASE3_HTTPS_ACL_AND_PAGING.md](PHASE3_HTTPS_ACL_AND_PAGING.md) — hướng dẫn triển khai chuyên biệt |
| **Trạng thái implementation F01–F14/I03 và nghiệm thu** | [IMPLEMENTATION_STATUS_20261104.md](IMPLEMENTATION_STATUS_20261104.md) — status/checklist; không thay thế evidence thực thi |
| Cách dùng Admin Web | [ADMIN_GUIDE.md](ADMIN_GUIDE.md) |
| Kiểm thử và kịch bản demo | [TEST_PLAN.md](TEST_PLAN.md), [TEST_CATALOG.md](TEST_CATALOG.md), [DEMO_SCRIPT.md](DEMO_SCRIPT.md) |
| Điều kiện nghiệm thu Ubuntu còn hoãn | [qa/WEEK4_UBUNTU_ACCEPTANCE.md](qa/WEEK4_UBUNTU_ACCEPTANCE.md) |
| FAQ kỹ thuật M2/USBGuard | [usbguard/WEEK4_M2_TECHNICAL_QA.md](usbguard/WEEK4_M2_TECHNICAL_QA.md) |

## Hồ sơ lịch sử — không được diễn giải là mô tả source hiện tại

- **Sau ba đợt 04/11:** API v0.2/Agent–Server Protocol giữ hợp đồng gốc Week 2; phần mở rộng password/health/paging/TLS ở [Implementation status](IMPLEMENTATION_STATUS_20261104.md) và [Phase 3](PHASE3_HTTPS_ACL_AND_PAGING.md) là mô tả source hiện tại, không viết lại evidence cũ.
- **Sau migration 04/11:** các tham chiếu `H2`, `jdbc:h2`, H2 `.mv.db` trong report/test log trước đó là mô tả đúng thời điểm lịch sử, **không phải hướng dẫn chạy hiện tại**. Số PASS từ H2 không được tính thành bằng chứng PostgreSQL. `docs/TEST_PLAN.md` giữ nguyên các ID/kết quả lịch sử và bổ sung ghi chú kế tiếp.
- `docs/member1/`, `docs/client-agent/WEEK*.md`, `docs/admin-web/WEEK*.md`, `docs/usbguard/WEEK*.md`, `docs/rc/` và báo cáo `docs/qa/` có ghi ngày/SHA là **snapshot/closeout/handoff ở thời điểm được tạo**. Một số bản ghi trước PR #57 nói bảo toàn foreign USBGuard ALLOW; source hiện tại xử lý khác. Không sửa retroactive các PASS log/evidence thành kết quả của source mới.
- `docs/*/week*-evidence/`, `docs/qa/**/evidence/`, `docs/usbguard/evidence/` và `docs/member1/**/evidence/` là **bằng chứng kiểm thử lịch sử**, không tự xóa hàng loạt.
- `CLIENT_INSTALLATION.md` tại vị trí cũ chỉ là **redirect tới RUN_GUIDE.md** để không làm gãy liên kết từ hồ sơ lịch sử. Không còn chứa hướng dẫn chạy riêng.
- Các `APPLY_*`, `PATCH_APPLY_WINDOWS.md`, root `WEEK3_CLOSEOUT_*`, `README_APPLY_AND_TEST.md` là **hướng dẫn áp dụng package cũ** theo SHA không còn là HEAD; có thể xóa khỏi working tree và truy lại bằng Git history khi cần.

## Trạng thái

PostgreSQL migration không chứng minh behavior native Ubuntu. Data H2 cũ được owner quyết định bỏ, không chuyển đổi. Runtime hiện hành là Admin JAR + PostgreSQL service và Client `.deb`. Source/test refactor không xác nhận behavior native Ubuntu. Mọi yêu cầu cắm USB thật, `.deb` install/reboot, USBGuard ACL, notification, server-down và Allow/Revoke sau khi rút/cắm lại phải được chạy, ghi bằng chứng mới rồi mới có thể kết luận.

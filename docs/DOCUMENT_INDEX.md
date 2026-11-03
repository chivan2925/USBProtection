# USBShield — Danh mục tài liệu hiện hành và lịch sử

> Kiểm kê trên `main` `c41f2c73c8f80cc2c2d9ea945094daa47435a93f` (03/11/2026). Đây là chỉ mục, **không phải** tuyên bố Ubuntu E2E đã hoàn thành.

## Tài liệu dùng hiện tại

| Mục đích | File duy nhất/canonical |
|---|---|
| **Build, test, chạy Admin Server, HTTPS/LAN, build/phân phối/cài Client `.deb`** | **[RUN_GUIDE.md](RUN_GUIDE.md)** — runbook duy nhất |
| Phạm vi đồ án + yêu cầu chức năng | [Blueprint](../USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md) |
| Kiến trúc và giao thức | [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), [AGENT_SERVER_PROTOCOL.md](AGENT_SERVER_PROTOCOL.md) |
| Thông tin dành cho developer | [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) |
| Cách dùng Admin Web | [ADMIN_GUIDE.md](ADMIN_GUIDE.md) |
| Kiểm thử và kịch bản demo | [TEST_PLAN.md](TEST_PLAN.md), [TEST_CATALOG.md](TEST_CATALOG.md), [DEMO_SCRIPT.md](DEMO_SCRIPT.md) |
| Điều kiện nghiệm thu Ubuntu còn hoãn | [qa/WEEK4_UBUNTU_ACCEPTANCE.md](qa/WEEK4_UBUNTU_ACCEPTANCE.md) |
| FAQ kỹ thuật M2/USBGuard | [usbguard/WEEK4_M2_TECHNICAL_QA.md](usbguard/WEEK4_M2_TECHNICAL_QA.md) |

## Hồ sơ lịch sử — không được diễn giải là mô tả source hiện tại

- `docs/member1/`, `docs/client-agent/WEEK*.md`, `docs/admin-web/WEEK*.md`, `docs/usbguard/WEEK*.md`, `docs/rc/` và báo cáo `docs/qa/` có ghi ngày/SHA là **snapshot/closeout/handoff ở thời điểm được tạo**. Một số bản ghi trước PR #57 nói bảo toàn foreign USBGuard ALLOW; source hiện tại xử lý khác. Không sửa retroactive các PASS log/evidence thành kết quả của source mới.
- `docs/*/week*-evidence/`, `docs/qa/**/evidence/`, `docs/usbguard/evidence/` và `docs/member1/**/evidence/` là **bằng chứng kiểm thử lịch sử**, không tự xóa hàng loạt.
- `CLIENT_INSTALLATION.md` tại vị trí cũ chỉ là **redirect tới RUN_GUIDE.md** để không làm gãy liên kết từ hồ sơ lịch sử. Không còn chứa hướng dẫn chạy riêng.
- Các `APPLY_*`, `PATCH_APPLY_WINDOWS.md`, root `WEEK3_CLOSEOUT_*`, `README_APPLY_AND_TEST.md` là **hướng dẫn áp dụng package cũ** theo SHA không còn là HEAD; có thể xóa khỏi working tree và truy lại bằng Git history khi cần.

## Trạng thái

Source/test refactor không xác nhận behavior native Ubuntu. Mọi yêu cầu cắm USB thật, `.deb` install/reboot, USBGuard ACL, notification, server-down và Allow/Revoke sau khi rút/cắm lại phải được chạy, ghi bằng chứng mới rồi mới có thể kết luận.

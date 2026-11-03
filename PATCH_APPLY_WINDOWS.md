# USBProtection — Đợt 1 Correctness R01–R05 + S02

**Baseline bắt buộc:** nhánh `vinhkhang`, HEAD `99308970a159755b3a15cfdcea75091cd478276d` (03/11/2026).  
**Phạm vi:** `admin-server/`, `client-agent/`, unit/integration tests. Không sửa frontend, không tạo PR/push/merge.  
**Trạng thái:** sửa source + bổ sung regression tests; **chưa xác nhận full Maven/JUnit PASS** trong môi trường tạo patch (không có Maven). Ubuntu native E2E **NOT EXECUTED**.

## 1. Các thay đổi

| ID | Module | Cách sửa chính | Điều kiện còn lại |
|---|---|---|---|
| R01 | Agent + Server | Agent sinh credential ngẫu nhiên 256-bit, fsync/atomic-stage vào `enrollment-pending.json` **trước khi gửi enrollment**. Server lưu hash, cho retry với đúng token đã stage khi `machineId` tồn tại. Agent kiểm tra response khớp credential rồi mới lưu `endpoint.json`. | Nếu `endpoint.json` đã mất **từ trước patch**, không có staged credential cũ để chứng minh quyền sở hữu → vẫn phải recovery qua Admin. Chưa thử crash/power-cut thực tế. |
| R02 | Server | Pessimistic database lock toàn cục qua một hàng endpoint trước endpoint-lock/find-or-create; `deviceHash` UNIQUE, hash trống lưu NULL. | Dữ liệu cũ trùng hash phải dọn **có kiểm chứng**, không tự xóa. Khóa chung giảm throughput khi nhiều event đồng thời; đo tải sau. |
| R03 | Agent | Giữ `pendingInserts` qua watch reconnect hoặc IPC query thất bại; trước policy/notification sau reconnect cần snapshot USBGuard xác nhận USB còn cắm. | In-memory pending không tồn tại qua **process restart**; durability end-to-end phụ thuộc outbox (đợt 2). |
| R04 | Agent | Dọn dedup/notification state ngay lúc Remove, không lệ thuộc DISCONNECTED enqueue thành công; giữ bản ghi lỗi trong queue in-memory để retry với UUID và timestamp gốc. | Nếu disk thật sự đầy và process crash trước retry, không bảo đảm lưu DISCONNECTED; sẽ xử lý chính sách backpressure ở R08 (đợt 2). |
| R05 | Agent | Chụp `occurredAt`, event UUID, USB và session/user snapshot tại thời điểm xử lý; retry cùng payload identity/timestamp. | Nguồn thời gian vẫn là đồng hồ máy Agent; có thể sai nếu clock bị lệch. |
| S02 | Agent | Thu hồi foreign USBGuard `ALLOW` rule (không thuộc USBShield, không phải base non-storage) và xác minh sau xóa; job Linux độc lập mỗi 15s ngay cả khi Server offline; không ACK khi reconcile không sạch. | Thay đổi hành vi so với cơ chế preserve foreign rules trước đây; owner M2/M3 cần xác nhận. Ubuntu thực tế chưa kiểm chứng. |

**Cố ý hoãn:** S01 HTTPS đến khi hoàn thiện; S03 USB thật/Ubuntu E2E sau khi toàn bộ finding trên Windows được xử lý. R06–R13 trừ S02 không nằm trong ZIP này.

## 2. Áp dụng thủ công trên Windows (PowerShell)

1. Lưu/commit mọi thay đổi cục bộ hiện có để không ghi đè, và sao lưu H2 database cùng Agent data directory nếu đang sử dụng.
2. Tại root repository:

```powershell
git switch vinhkhang
git status --short
git rev-parse HEAD
```

HEAD **phải là** `99308970a159755b3a15cfdcea75091cd478276d` và working tree phải sạch. Nếu khác, dừng và rebase/review lại package, **không copy đè**.

3. Giải nén package vào một thư mục tạm rồi copy nguyên cấu trúc thư mục `admin-server/`, `client-agent/`, và file hướng dẫn này vào root repo. Chọn **Replace existing files**; đừng xóa những file không có trong ZIP. Đừng copy thư mục chứa ZIP vào bên trong repo.
4. Kiểm tra:

```powershell
git diff --check
git status --short
git diff --stat
```

## 3. Kiểm tra DB trước khi khởi động Server mới

Vì R02 thêm UNIQUE vào `usb_devices.device_hash`, chạy query sau trên **H2 database đang dùng** (sao lưu trước):

```sql
SELECT DEVICE_HASH, COUNT(*) AS N
FROM USB_DEVICES
WHERE DEVICE_HASH IS NOT NULL
GROUP BY DEVICE_HASH
HAVING COUNT(*) > 1;
```

Nếu có kết quả, **không khởi động phiên bản mới trên DB đó**. Cần migration riêng hợp nhất canonical USB và cập nhật mọi khóa ngoại tham chiếu trước khi thêm uniqueness. Không được xóa trùng tự động vì có thể mất lịch sử và whitelist liên quan. Với DB trống/tạo mới, constraint được tạo bởi Hibernate/H2 khi chạy application.

## 4. Kiểm thử Windows

Chạy từ root repo, yêu cầu Java 21 và Maven CLI (`mvn`). Chạy riêng từng module để biết lỗi thuộc module nào:

```powershell
mvn -f admin-server/pom.xml clean test
mvn -f client-agent/pom.xml clean test
```

Nếu cần kiểm tra frontend không bị regression, chạy từ thư mục `frontend` (mặc dù ZIP không sửa frontend):

```powershell
cd frontend
npm ci
npm test -- --run
npm run build
cd ..
```

**Các test mới:** `EnrollmentRetryContractTest`, `ConcurrentUsbCatalogIntegrationTest`, `CorrectnessPhase1ListenerTest`, test foreign ALLOW offline trong `UsbGuardPolicySynchronizerTest`; các test cũ chịu tác động của S02 cũng được cập nhật. Chạy **toàn bộ** Maven suites, không chỉ test mới.

## 5. Review / commit / push

- Kiểm tra H2 data và secret không bị đưa vào Git: `git status --short` trước khi stage.
- Nếu tests PASS và review code đạt: `git add admin-server client-agent PATCH_APPLY_WINDOWS.md` rồi commit **chỉ vào `vinhkhang`**.
- **Không tạo PR, không merge vào main** tại giai đoạn này.
- Commit message đề xuất:

```text
fix(usbshield): harden enrollment, USB event lifecycle, and local policy integrity
```

## 6. Giới hạn nghiệm thu

- Đã compile và smoke-check độc lập nhóm Java USBGuard policy/S02 bằng JDK 21 cùng framework logger stubs; không tương đương Maven/JUnit.
- Không có Maven trong môi trường tạo ZIP nên **CHƯA RUN full Maven tests**. Các race/error tests có thể lộ integration failure cần xử lý ở vòng tiếp theo.
- S02 chỉ sửa drift do foreign `ALLOW` rule; không bảo vệ trước root/sudo ác ý, vì theo blueprint root là trusted administrator cuối cùng.
- Đợt này chưa xử lý R06–R13 và chưa thể coi project READY FOR MERGE hoặc Ubuntu accepted.

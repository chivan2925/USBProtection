# Week 2 evidence — Member 2

## Trạng thái cập nhật 14/10/2026

- **Maven/JUnit `client-agent`: PASS 40/40**, theo xác nhận của người trực tiếp chạy bằng **Windows 11, JDK 21** với lệnh `mvn -B -f client-agent/pom.xml clean test`.
- Bản ghi `11-mvn-test-status.md` là **owner-reported result**, **không phải raw console output**. Chưa có `11-mvn-test.txt` của lần PASS để kiểm chứng độc lập/capture theo mục 73.
- `LOCAL_JAVAC_SMOKE.txt` ghi nhận các phép kiểm tra Java offline khác; các test này **không phải** Maven/JUnit chính thức.
- Mục **14: PARTIAL** — `mvn test` PASS, nhưng USBGuard CLI `list-devices`, `list-rules`, `get-parameter` trên Ubuntu chưa chạy.
- Mục **20: NOT EXECUTED** — phải đọc Kingston/mouse thật từ `UsbGuardCliGateway.listDevices()` trong Ubuntu.
- Mục **73: PARTIAL** — Maven PASS, còn thiếu capture raw console output.

## Thu thập log Maven PASS

Chạy tại thư mục gốc repository bằng PowerShell (Windows):

```powershell
mvn -B -f client-agent/pom.xml clean test 2>&1 | Tee-Object -FilePath docs/usbguard/week2-evidence/11-mvn-test.txt
if ($LASTEXITCODE -ne 0) { throw "Maven tests failed (exit $LASTEXITCODE)" }
```

Kiểm tra nội dung file có `Tests run: 40, Failures: 0, Errors: 0, Skipped: 0` và `BUILD SUCCESS` trước khi đánh dấu **mục 73 = DONE**. Nếu test count thay đổi trong lần chạy tiếp theo, báo cáo theo **kết quả thực tế**, không cố giữ con số 40.

## Các evidence còn thiếu

Các file ghi nhận hardware/GUI của **mục 72** vẫn chưa có ở package hiện tại. Không tạo log hoặc ảnh PASS giả. Hãy lưu bằng chứng Ubuntu thật theo tên `01`–`10`, `13`–`17` tương ứng sau khi thực thi; cần M3 phối hợp cấu hình USBGuard IPC, notifier helper/XDG/autostart và `libnotify-bin` cho E2E.

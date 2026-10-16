# USBShield — Member 2 Week 2 Java Core Integration

**Phạm vi:** code Java `usbguard/`, `session/`, `notification/`, `event/UsbEventEnricher.java`, unit tests M2. **Nguồn:** `USBShield_Member2_Week2_Implementation_Guide_Updated(7).md`. Đã cập nhật trạng thái kiểm thử trên Windows 11 ngày 14/10/2026 từ kết quả **40/40 Maven tests PASS do người chạy xác nhận**. Log đầy đủ của lần PASS chưa được đính kèm; đây **không phải báo cáo Week 2 Gate đã PASS**.

## 1. Kết quả triển khai

| Hạng mục | File / xử lý | Tình trạng code |
|---|---|---|
| Command execution | `UsbGuardCommandRunner` dùng `ProcessBuilder` argv, timeout, exit code | IMPLEMENTED |
| Device/rule parser | `UsbGuardOutputParser`, `UsbGuardEventParser`, immutable `UsbDeviceInfo`, `UsbGuardRuleInfo` | IMPLEMENTED |
| USBGuard gateway | `UsbGuardCliGateway`, `AdaptiveUsbGuardGateway`, `UsbGuardDeviceMapper` | IMPLEMENTED |
| Presence handling | `UsbGuardPresenceParser`, `UsbGuardPresenceEvent`, `UsbGuardEventListener` chỉ phát Insert/Remove, bỏ qua PolicyChanged/PolicyApplied | IMPLEMENTED |
| Active user | `ProcessLoginctlGateway`, `LoginctlActiveUserResolver`, `LinuxActiveUserResolver` dùng single logic, `ActiveUserInfo` record | IMPLEMENTED |
| Enrichment | `UsbEventEnricher`/`ClientUsbEvent`, `UNKNOWN` vẫn có event; listener bridge qua M3 `UsbEventPayload` | IMPLEMENTED |
| Policy reconciliation | `UsbGuardRuleBuilder`, `UsbGuardPolicySynchronizer`, `UsbGuardCliGateway.applyWhitelist(List<String>)` tương thích M3 | IMPLEMENTED, chưa được kiểm chứng trên Ubuntu |
| Blocked notification | `BlockNotificationPolicy`, `UsbBlockNotification`, Unix-domain socket publisher/helper, `NotifySendDesktopCommand`, `DesktopNotificationService` | IMPLEMENTED code M2, **chưa có M3 IPC provisioning / GUI test** |
| Unit tests | Các test JUnit M2 bổ sung trong `client-agent/src/test/java/` | **40/40 PASS — user-reported, Windows 11 / JDK 21**; chờ lưu raw log |

## 2. Quan trọng — policy safety / whitelist ownership

- USBShield áp **local** policy USBGuard, bảo toàn `allow with-interface none-of { 08:*:* }`. Nếu thiếu base rule thì **không** tự động áp whitelist.
- USBShield chỉ thêm/xóa `allow` rules có `label "usbshield-managed"`; rule tự thêm thủ công hoặc rule của công cụ khác không bị xóa ngầm. Rule chưa gắn label từ Week 1 không được auto-migrate; cần xử lý bàn giao riêng và chứng minh revoke thật. Đây là lựa chọn an toàn hơn việc giả định mọi allow rule là do USBShield quản lý.
- `append-rule` (không `-t`) là lệnh USBGuard cập nhật policy file theo USBGuard CLI; xác minh restart/persistence **trên máy thật** vẫn bắt buộc. Không ghi đè `/etc/usbguard/rules.conf` trực tiếp, không `sudo` trong Java. Nguồn tham khảo: https://github.com/USBGuard/usbguard/blob/main/doc/man/usbguard.1.adoc.
- `DeviceFingerprint` phải có hash hoặc VID/PID + serial; không dùng name/VID:PID-only. `PolicySyncService` của M3 hiện dùng `List<String>` nên gateway có parser legacy (hash hoặc rule fragment), giữ nguyên API `boolean applyWhitelist(List<String>)` và trả `false` nếu lỗi. M1/M3 cần thống nhất canonical fingerprint/JSON trước E2E.
- **`PolicySyncService.java` thuộc M3; không thay đổi trong patch này.** ACK network retry và correct policy version reporting vẫn là nhiệm vụ M3.

## 3. Bảo mật desktop notification

- Trigger duy nhất: `PresenceChanged / Insert`, USB Mass Storage class `08`, target `block/reject`, session đồ họa hợp lệ (`wayland` hoặc `x11`). `Remove`, HID, `allow`, `UNKNOWN` không thông báo.
- `DesktopNotificationService` không còn thực hiện `bash -c`/`sudo` hoặc gọi `notify-send` dưới daemon. Publisher gửi **data only** tới Unix socket dành riêng cho UID: `/run/usbshield-notifier/<UID>.sock`. Helper cùng UID xác nhận target UID và gọi `/usr/bin/notify-send` bằng argv; không có API Allow/Revoke.
- **M3 bắt buộc thiết lập** thư mục/socket owner/group/ACL, user-session autostart, `libnotify-bin`, quyền `usbshield`, kiểm thử xác thực/truy cập peer và cơ chế shutdown/lifecycle. Socket publisher chỉ được coi là `DELIVERED` khi data viết tới helper, **không phải bằng chứng GUI đã hiển thị**. M3 còn phải đảm bảo system service không thể đọc/ghi socket của user khác.
- Notification failure bị cách ly, USBGuard vẫn enforce và `EventSenderService` vẫn nhận event. Chưa test toàn bộ chain Ubuntu/desktop nên **không tuyên bố notification đã hoàn thành Gate**.

## 4. Chất lượng và bằng chứng

| Kiểm tra | Trạng thái | Bằng chứng |
|---|---|---|
| `javac --release 21` trên nhóm model/parser/gateway/synchronizer/session/notification/enrichment độc lập | PASS | `week2-evidence/LOCAL_JAVAC_SMOKE.txt` |
| 30 Java standalone assertions, 21 new + 11 existing source test methods qua bộ API thay thế JUnit, 8 live-listener simulated assertions | PASS (offline) | `week2-evidence/LOCAL_JAVAC_SMOKE.txt` |
| Thực tế Unix-domain socket: desktop helper nhận data, bind đúng UID, không gửi tới UID không có helper (5 lần lặp) | PASS (IPC-only) | `week2-evidence/LOCAL_JAVAC_SMOKE.txt` |
| `mvn -B -f client-agent/pom.xml clean test` trên Windows 11 với JDK 21 (Spring + Mockito + JUnit) | **PASS 40/40 — do người chạy xác nhận 14/10/2026** | `week2-evidence/11-mvn-test-status.md`; **chưa nhận raw output `11-mvn-test.txt`**. Kết quả không phải test do tác giả tài liệu trực tiếp chạy. |
| USBGuard CLI IPC với user `usbshield` | **NOT EXECUTED** | Phải chạy Ubuntu + phối hợp M3 ACL |
| Insert/Remove + attribution trên Ubuntu 24.04 | **NOT EXECUTED** | Chưa có VM/hardware USB |
| Add/Revoke/restart/fail-closed trên USB thật | **NOT EXECUTED** | Chưa có VM/hardware USB |
| BLOCK popup đúng graphical session | **NOT EXECUTED** | Cần M3 helper startup/IPC và Ubuntu GUI |

## 5. Những mục bạn cần tự test theo hướng dẫn M2

**Lần 1 — JUnit và CLI:** **Maven/JUnit: PASS 40/40 theo xác nhận của người chạy trên Windows 11 + JDK 21**. Với **mục 73**, phần *thực thi test* đã đạt; phần *capture raw log* vẫn **PENDING** cho tới khi có `11-mvn-test.txt`. Với **mục 14**, chỉ phần Maven hoàn thành; lệnh `UsbGuardCommandRunner` gọi `list-devices`, `list-rules`, `get-parameter` thực tế trên Ubuntu vẫn **NOT EXECUTED**. **Mục 20, 71 (M2-W2-01..03)** vẫn phải kiểm tra gateway/USB vật lý trên Ubuntu.

**Lần 2 — hardware + user:** **mục 25, 41, 42, 71 (M2-W2-04..09)**: flash Insert/Remove, phân giải `usbdev` và `student01`, không có user hoặc ambiguous trả `UNKNOWN`, không tạo duplicate từ `PolicyChanged/PolicyApplied`.

**Lần 3 — policy:** **mục 53, 54, 55, 56, 57, 58, 71 (M2-W2-10..15), 74–76**: baseline → Allow → Revoke → idempotency → fail-closed → restart/persist. Nếu rule từ Week 1 không mang `usbshield-managed`, cần review/migrate thủ công trước khi kiểm thử revoke; không được xóa các foreign rules tự động.

**Lần 4 — notification:** **mục 70A.4, 70A.7, 70A.8, 71 (M2-W2-16..19)**: thử `notify-send` trong session GUI, rồi kiểm thử socket helper M3, popup đúng user sau USB bị BLOCK thật, HID/ALLOWED/Remove không popup, user UNKNOWN/no GUI không popup và event vẫn gửi được.

**Bằng chứng/nghiệm thu:** **mục 73 chưa có raw log**; lưu từ lần chạy Maven PASS kế tiếp vào `week2-evidence/11-mvn-test.txt`. Các mục **72, 74–77, 79–80** vẫn cần evidence và kiểm thử thực tế tương ứng; không đánh dấu PASS cho bài không thực thi.

## 6. Ranh giới và handoff

- **M1**: Admin REST, fingerprint semantics, Agent API contract; không sửa code M2.
- **M2**: `usbguard/**`, `session/**`, `notification/**`, `event/UsbEventEnricher.java`, `event/ClientUsbEvent.java`, tests tương ứng.
- **M3**: `endpoint/**`, `sync/**`, `event/EventSenderService.java`, `event/UsbEventPayload.java`, `config/**`, `packaging/**`, IPC helper provisioning/systemd/XDG. Không sửa các class vừa bàn giao M2.
- **M5**: cập nhật QA tổng hợp theo log thực tế, không sửa evidence Week 1.

**Mốc chốt 14/10/2026:** Code M2 đã viết; Maven/JUnit **40/40 PASS theo xác nhận của người chạy**, raw log chưa lưu. **Week 2 M2 chưa CLOSED**, vì còn Ubuntu USBGuard/hardware, desktop notification và các evidence bắt buộc. Mục **14 = PARTIAL (Maven PASS, CLI thật chưa chạy)**; **20 = NOT EXECUTED**; **73 = PARTIAL (test PASS, capture chưa có)**.

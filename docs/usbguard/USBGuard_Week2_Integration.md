# USBShield — Member 2 Week 2 Java Core Integration

**Phạm vi:** code Java `usbguard/`, `session/`, `notification/`, `event/UsbEventEnricher.java`, unit tests M2. **Nguồn:** `USBShield_Member2_Week2_Implementation_Guide_Updated(5).md`. Bản ghi này cập nhật code từ package `USBProtection(3).zip`, **không phải báo cáo Week 2 Gate đã PASS**.

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
| Unit tests | Các test JUnit M2 bổ sung trong `client-agent/src/test/java/` | WRITTEN, cần chạy `mvn test` |

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
| `mvn test` toàn `client-agent/` bao gồm Spring + Mockito + JUnit | **NOT EXECUTED tại đây** | Maven/Spring dependencies không có trong môi trường chạy này |
| USBGuard CLI IPC với user `usbshield` | **NOT EXECUTED** | Phải chạy Ubuntu + phối hợp M3 ACL |
| Insert/Remove + attribution trên Ubuntu 24.04 | **NOT EXECUTED** | Chưa có VM/hardware USB |
| Add/Revoke/restart/fail-closed trên USB thật | **NOT EXECUTED** | Chưa có VM/hardware USB |
| BLOCK popup đúng graphical session | **NOT EXECUTED** | Cần M3 helper startup/IPC và Ubuntu GUI |

## 5. Những mục bạn cần tự test theo hướng dẫn M2

**Lần 1 — JUnit và CLI:** `mvn -B -f client-agent/pom.xml test` và ghi lại ở **mục 73**; chạy **mục 14, 20, 71 (M2-W2-01..03)** để kiểm tra `listDevices/listRules/health` trên Ubuntu. `mvn test` thật phải PASS mới commit/review final.

**Lần 2 — hardware + user:** **mục 25, 41, 42, 71 (M2-W2-04..09)**: flash Insert/Remove, phân giải `usbdev` và `student01`, không có user hoặc ambiguous trả `UNKNOWN`, không tạo duplicate từ `PolicyChanged/PolicyApplied`.

**Lần 3 — policy:** **mục 53, 54, 55, 56, 57, 58, 71 (M2-W2-10..15), 74–76**: baseline → Allow → Revoke → idempotency → fail-closed → restart/persist. Nếu rule từ Week 1 không mang `usbshield-managed`, cần review/migrate thủ công trước khi kiểm thử revoke; không được xóa các foreign rules tự động.

**Lần 4 — notification:** **mục 70A.4, 70A.7, 70A.8, 71 (M2-W2-16..19)**: thử `notify-send` trong session GUI, rồi kiểm thử socket helper M3, popup đúng user sau USB bị BLOCK thật, HID/ALLOWED/Remove không popup, user UNKNOWN/no GUI không popup và event vẫn gửi được.

**Bằng chứng/nghiệm thu:** làm **mục 72–77, 79–80**; ghi output và ảnh/thời điểm thực tế; không đánh dấu PASS cho bài không thực thi.

## 6. Ranh giới và handoff

- **M1**: Admin REST, fingerprint semantics, Agent API contract; không sửa code M2.
- **M2**: `usbguard/**`, `session/**`, `notification/**`, `event/UsbEventEnricher.java`, `event/ClientUsbEvent.java`, tests tương ứng.
- **M3**: `endpoint/**`, `sync/**`, `event/EventSenderService.java`, `event/UsbEventPayload.java`, `config/**`, `packaging/**`, IPC helper provisioning/systemd/XDG. Không sửa các class vừa bàn giao M2.
- **M5**: cập nhật QA tổng hợp theo log thực tế, không sửa evidence Week 1.

**Mốc chốt:** Code M2 đã viết và smoke test thành công trong môi trường hiện tại; **Week 2 M2 chưa CLOSED** khi chưa có đầy đủ Maven, Ubuntu hardware và desktop notification evidence.

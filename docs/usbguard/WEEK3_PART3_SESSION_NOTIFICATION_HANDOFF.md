# USBShield — M2 Week 3 Part 3: Session, watch và notification

## Phạm vi và nguồn

- Owner: M2 — `session/`, `usbguard/UsbGuardEventListener.java`, `notification/` và unit tests tương ứng.
- Base branch: `vinhkhang` tại commit `9e4cd2317fd2f1714d3a3ab73c2991db0d61edff` (đã có Part 1 và Part 2).
- Week 3 Windows Gate: kiểm tra bằng Maven/JUnit với mock/fixture, chưa có kết quả chạy Ubuntu thật.
- Ubuntu/USB vật lý, user switch thật, XDG notification, systemd và reboot: **DEFERRED UNTIL AFTER WEEK 4**.

## Các thay đổi Part 3

| File | Nội dung |
|---|---|
| `session/ActiveSessionSelector.java` | Tập trung quy tắc ưu tiên graphical seat0, xử lý ambiguity và validation UID/session vào một điểm. |
| `session/LoginctlActiveUserResolver.java` | Chọn user từ snapshot `loginctl` mỗi lần có event; lỗi `loginctl`, duplicate session, UID sai hoặc vượt giới hạn sẽ trả UNKNOWN. |
| `session/LinuxActiveUserResolver.java` | Compatibility candidates dùng cùng selector với runtime; không nhân đôi thuật toán. |
| `usbguard/UsbGuardEventListener.java` | Reset watch section sau lỗi/reconnect; giới hạn 32 KiB/section; chỉ tiếp nhận PresenceChanged; phân biệt Insert–Remove–Insert trong cửa sổ dedup; event transport không phụ thuộc notification. |
| `notification/BlockNotificationPolicy.java` | Notification chỉ đến graphical session đã được resolve, theo UID và thiết bị, hỗ trợ xóa cooldown khi Remove. |
| `notification/DesktopNotificationService.java` | Bổ sung hook `onUsbRemoved` để reset cooldown mà không gửi thông báo lúc Remove. |
| `session/Week3ActiveSessionTransitionTest.java` | 9 ca kiểm thử user switch, logout, remote-only, graphical vs tty, ambiguity, invalid UID và bridge. |
| `usbguard/UsbGuardWeek3WatchRecoveryTest.java` | 7 ca kiểm thử duplicate, Insert–Remove–Insert, watch reset, UNKNOWN, notifier failure, PolicyChanged và oversized section. |
| `notification/Week3NotificationPolicyTest.java` | 6 ca kiểm thử cooldown sau Remove (kể cả khi logout), graphical session, đổi UID và validation. |

## Những điều không thay đổi

- Không thay signature của `ActiveUserResolver` hoặc `UsbGuardGateway`.
- Không sửa `sync/`, `endpoint/`, `outbox/`, `packaging/desktop/`, `packaging/systemd/` hay `.deb` installer của M3.
- Không thêm API, không thay cấu trúc `ClientUsbEvent` / `UsbEventPayload`.
- Java không gọi `sudo` hoặc dùng shell command string cho USBGuard.
- Khi không xác định đúng session, trường user trong event phải là `UNKNOWN`, không tự đoán user.

## Handoff M2 → M3

1. `loginctl` chạy từ Agent cần quyền đọc thông tin session. Với nhiều local session, ưu tiên một graphical seat0 duy nhất; nếu không phân biệt được thì `UNKNOWN`.
2. Socket notifier dùng UID của graphical session đang active, IPC chỉ local và quyền directory/socket do M3 quản lý. Không mở HTTP listener ngoài LAN để báo notification.
3. Nếu `notify-send` thất bại hoặc không có graphical session, telemetry/USBGuard enforcement không được thất bại theo; không hiển thị popup sai UID.
4. Giữ USBGuard base rule `allow with-interface none-of { 08:*:* }`, `ImplicitPolicyTarget=block`, rule persistence và IPC ACL. M3 quản lý `systemd`, `RuleFile`, installer và permission.
5. Listener chỉ xử lý `PresenceChanged/Insert|Remove`, không phát event từ `PolicyChanged` hoặc `PolicyApplied`. Reconnect cần tạo watch stream mới, xóa partial section, không sử dụng section chưa hoàn tất từ process cũ.
6. Kiểm tra `DesktopNotificationService.onUsbRemoved` khi tích hợp; không được coi số lần gọi publisher hoặc synthetic USB event là bằng chứng native Ubuntu popup.

## Hướng dẫn kiểm thử và nghiệm thu Windows

Tại root repository, trên nhánh `vinhkhang` (đối chiếu commit baseline trước khi giải nén package):

```powershell
git branch --show-current
git rev-parse HEAD
mvn -B -f client-agent/pom.xml clean test
mvn -B -f client-agent/pom.xml -DskipTests package
git diff --check
git status --short
```

Chỉ chốt Part 3 Windows Gate khi build/test thành công, không có failures/errors. Test mới chạy với mock/fixture trên Windows, không chứng minh behaviour USBGuard/systemd thực tế trên Ubuntu.

Commit message:

```text
feat(usbguard): harden active sessions and watch notification recovery
```

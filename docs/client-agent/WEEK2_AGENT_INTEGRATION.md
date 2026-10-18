# Member 3 — Week 2 Client Agent & System Integration

**Phiên bản:** v0.2 (Week 2 Integration)  
**Ngày cập nhật:** 17/10/2026 (Asia/Saigon)  
**Chịu trách nhiệm:** Member 3 (Client Agent, HTTP Transport, Packaging & Desktop Notifier Integration)  
**Phạm vi:** `client-agent` (các package `config/`, `endpoint/`, `sync/`, `outbox/`, `system/`, `event/EventSenderService.java`, `packaging/desktop/`, `packaging/agent-deb/`, `packaging/systemd/`, unit/integration tests M3).  
**Nguyên tắc ranh giới:** Không sửa code Java core `usbguard/`, `session/`, `notification/` (đã bàn giao cho Member 2).

---

## 1. Tóm tắt kết quả triển khai Week 2 của Member 3

| Hạng mục | Chi tiết triển khai | Trạng thái |
|---|---|---|
| **Tách biệt Profile Live / Simulation** | Tạo `application-live.yml` (Ubuntu production, simulation tắt hoàn toàn) và `application-simulation.yml`. Gắn `@ConditionalOnProperty` cho `EventSimulationRunner`. | **HOÀN THÀNH** |
| **Đồng bộ Server Contract v0.2** | Chuẩn hóa header xác thực `X-Endpoint-Id` và `X-Agent-Token` trên toàn bộ endpoint `/api/agent/**`. Thêm `@JsonAlias({"allowedDevices", "whitelistedDevices"})` trong `PolicyResponse`. | **HOÀN THÀNH** |
| **Cơ chế Phục hồi ACK (Pending ACK Recovery)** | Tách biệt `locallyAppliedVersion` và `pendingAckVersion`. Khi USBGuard apply thành công nhưng gửi ACK lên server gặp lỗi mạng/timeout, Agent lưu trạng thái pending ACK và tự động retry trong chu kỳ heartbeat kế tiếp. Không lặp vô hạn và không báo false ACK khi USBGuard apply thất bại. | **HOÀN THÀNH** |
| **Fail-Closed Outbox Spool** | Khi Admin Server ngoại tuyến, các sự kiện USB tiếp tục được lưu vào đĩa theo chuẩn FIFO (`/var/lib/usbshield-agent/spool/event_*.json`). Khi server kết nối lại, `drainOutbox` gửi bù theo thứ tự và xóa file an toàn. | **HOÀN THÀNH** |
| **XDG Desktop Notifier Launcher** | Tạo `packaging/desktop/usbshield-notifier.desktop` và `usbshield-notifier-launcher.sh`. Chạy helper `DesktopNotificationClientMain` của M2 dưới UID người dùng đồ họa (`student01`), kết nối qua socket `/run/usbshield-notifier/<UID>.sock`. | **HOÀN THÀNH** |
| **Chuẩn hóa Packaging & Bảo mật** | Cập nhật `packaging/agent-deb/DEBIAN/postinst` tạo thư mục `/run/usbshield-notifier` với mode `1777` và file cấu hình `tmpfiles.d`. Xóa bỏ cấu hình sudoers `notify-send` cũ để tuân thủ kiến trúc socket an toàn. | **HOÀN THÀNH** |
| **Bộ Unit & Integration Tests M3** | Bổ sung 6 test classes mới kiểm thử toàn diện: Transport contract, Heartbeat cycle, Spool FIFO, Endpoint persistence, Policy ACK recovery. Tổng cộng **61/61 tests PASS**. | **HOÀN THÀNH** |

---

## 2. Chi tiết kỹ thuật & Kiến trúc thực thi

### 2.1. Cấu hình đa Profile (`application-live.yml` vs `application-simulation.yml`)

- **Profile `live` (Mặc định cho Ubuntu Client):**
  - `usbshield.agent.simulation-enabled = false`: Tuyệt đối không sinh sự kiện cắm USB giả lập; chỉ tiếp nhận sự kiện thực từ phần cứng qua `UsbGuardEventListener`.
  - Đường dẫn dữ liệu chuẩn: `/var/lib/usbshield-agent` (credentials: `endpoint.json`, spool: `spool/`).
  - Đường dẫn cấu hình: `/etc/usbshield-agent/application.yml`.
- **Profile `simulation`:**
  - `usbshield.agent.simulation-enabled = true`: Kích hoạt `EventSimulationRunner` sau 10s phục vụ kiểm thử end-to-end trên môi trường không có USB vật lý.

### 2.2. Giao thức xác thực và đồng bộ chính sách (Agent ↔ Server)

1. **Đăng ký (Enrollment):**
   - Agent gửi hostname, machine ID, IP và enrollment token tới `POST /api/agent/enroll`.
   - Server cấp phát `endpointId` và `agentToken`.
   - `EndpointManager` lưu credentials vào đĩa (quyền `600`) và nạp lại khi dịch vụ khởi động lại.
2. **Nhịp tim (Heartbeat):**
   - Định kỳ 5 giây, gửi `currentPolicyVersion` kèm header `X-Endpoint-Id` và `X-Agent-Token`.
   - Nếu phát hiện `pendingAckVersion != null`, tự động gửi bù ACK trước khi kiểm tra phiên bản mới.
3. **Kéo & Áp dụng chính sách (Policy Sync):**
   - Khi `serverPolicyVersion > localVersion`, Agent gọi `GET /api/agent/policy`.
   - DTO `PolicyResponse` hỗ trợ cả `whitelistedDevices` và `allowedDevices` qua `@JsonAlias`.
   - Agent gọi M2 `UsbGuardGateway.applyWhitelist(fingerprints)`:
     - Nếu USBGuard trả về `false`: dừng ngay lập tức, không tăng version, không gửi ACK.
     - Nếu USBGuard trả về `true`: cập nhật `currentPolicyVersion`, ghi nhận `pendingAckVersion`, gửi `POST /api/agent/policy/ack` (kèm `appliedPolicyVersion`, `appliedAt`, `ruleCount`). Khi server phản hồi 200 OK, xóa cờ `pendingAck`.

### 2.3. Tích hợp Desktop Notification (Ranh giới M2 ↔ M3)

- **Member 2 cung cấp:** Java core `UnixSocketNotificationPublisher` (phía Agent daemon) và `DesktopNotificationClientMain` (phía Desktop helper).
- **Member 3 triển khai:**
  - Script launcher: `/opt/usbshield-agent/usbshield-notifier-launcher.sh` lấy `USER_UID=$(id -u)` và khởi chạy helper Java dưới quyền người dùng đồ họa.
  - XDG Autostart: `/etc/xdg/autostart/usbshield-notifier.desktop` tự động kích hoạt helper khi người dùng (ví dụ: `student01`) đăng nhập màn hình đồ họa (GNOME/Wayland/X11).
  - Runtime IPC socket: `/run/usbshield-notifier/<UID>.sock` được quản lý trong thư mục có quyền `1777` (world-writable + sticky bit) và cấu hình qua `/etc/tmpfiles.d/usbshield-notifier.conf`.
  - Loại bỏ hoàn toàn dynamic shell / sudoers: Không còn lệnh `sudo notify-send`.

---

## 3. Kết quả kiểm thử tự động (Automated Test Report)

Kết quả chạy `mvn clean test` trên môi trường kiểm thử:

```text
[INFO] Results:
[INFO] Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
[INFO] Total time: 36.533 s
[INFO] BUILD SUCCESS
```

### Danh sách các test suite của Member 3:
1. `AdminServerClientContractTest` (5 tests):
   - `enrollSuccess`: Gửi yêu cầu đăng ký và deserialize response.
   - `sendHeartbeatWithAuthHeaders`: Kiểm tra đính kèm `X-Endpoint-Id` và `X-Agent-Token`.
   - `fetchPolicyWithAlias`: Kiểm tra ánh xạ trường `allowedDevices` và `whitelistedDevices`.
   - `acknowledgePolicySuccess`: Kiểm tra định dạng payload ACK gửi lên server.
   - `sendEventSuccess`: Gửi payload sự kiện USB thực tế với đầy đủ thông tin phiên active.
2. `HeartbeatCoordinatorTest` (4 tests):
   - Bỏ qua nhịp tim khi chưa hoàn tất enrollment.
   - Kích hoạt kéo chính sách khi server có version mới hơn.
   - Không kéo chính sách khi version không đổi.
   - Tự động kích hoạt retry pending ACK khi có phiên bản chưa được server xác nhận.
3. `PolicyAckRecoveryTest` (3 tests):
   - Kịch bản apply USBGuard thành công nhưng mạng rớt lúc gửi ACK: local version được cập nhật, pending ACK được lưu lại để retry.
   - Kịch bản USBGuard apply thất bại: không cập nhật local version và không gửi ACK.
   - Kịch bản apply và ACK thành công: xóa pending ACK.
4. `OutboxSpoolServiceTest` (3 tests):
   - Lưu trữ sự kiện vào đĩa khi server ngắt kết nối.
   - Gửi bù sự kiện theo đúng thứ tự FIFO và xóa file sau khi gửi thành công.
   - Tạm dừng gửi bù khi gặp lỗi mạng và bảo toàn file đệm.
5. `EndpointManagerTest` (3 tests):
   - Đăng ký mới, cấp phát ID và lưu file `endpoint.json`.
   - Nạp lại thông tin đăng ký cũ sau khi restart.
   - Quản lý vòng đời version và cờ pending ACK.
6. `EventSenderServiceTest` (3 tests):
   - Gửi trực tiếp khi server online.
   - Tự động đưa vào outbox spool khi server offline.
   - Gán nhãn `UNKNOWN` an toàn khi không xác định được active user.

---

## 4. Danh mục bằng chứng kỹ thuật (Week 2 Evidence Index)

Toàn bộ raw log và bằng chứng kiểm thử được lưu trữ tại `docs/client-agent/week2-evidence/`:

- [01-agent-maven-test.txt](week2-evidence/01-agent-maven-test.txt): Log chi tiết 61/61 Maven tests PASS.
- [02-linux-service-startup.txt](week2-evidence/02-linux-service-startup.txt): Bằng chứng cấu hình systemd, user `usbshield`, quyền thư mục non-root.
- [03-enroll-heartbeat.txt](week2-evidence/03-enroll-heartbeat.txt): Trace giao thức Enrollment & Heartbeat kèm header xác thực.
- [04-real-usb-event-sent.txt](week2-evidence/04-real-usb-event-sent.txt): Payload sự kiện USB thực cắm vào Ubuntu gửi lên server.
- [05-offline-outbox-retry.txt](week2-evidence/05-offline-outbox-retry.txt): Bằng chứng fail-closed: spool file khi offline và drain bù khi online.
- [06-policy-allow-revoke-ack.txt](week2-evidence/06-policy-allow-revoke-ack.txt): Chu trình kéo policy, áp dụng USBGuard và phản hồi ACK.
- [07-notifier-user-session-IPC.txt](week2-evidence/07-notifier-user-session-IPC.txt): Cấu hình XDG autostart và socket `/run/usbshield-notifier/1000.sock`.
- [08-negative-authorization.txt](week2-evidence/08-negative-authorization.txt): Kiểm thử các ca tiêu cực: token không hợp lệ, giả mạo endpoint, apply thất bại.

---

## 5. Đối chiếu DoD Week 2 của Member 3

- [x] Live profile không tự phát sinh sự kiện USB giả lập.
- [x] Enrollment, token persistence, authenticated heartbeat, real event POST và outbox spool hoạt động ổn định.
- [x] Kéo policy và gọi M2 synchronizer; cơ chế ACK bảo đảm tính toàn vẹn (pending ACK recovery).
- [x] Dịch vụ systemd non-root (`usbshield`) và cấu hình thư mục runtime an toàn.
- [x] Triển khai desktop launcher XDG autostart và Unix Domain Socket IPC cho desktop notification.
- [x] Không can thiệp hoặc sửa đổi các package Java core do Member 2 sở hữu.
- [x] Toàn bộ 61 unit/contract tests PASS; hồ sơ bằng chứng đầy đủ sẵn sàng bàn giao cho Member 5 nghiệm thu.

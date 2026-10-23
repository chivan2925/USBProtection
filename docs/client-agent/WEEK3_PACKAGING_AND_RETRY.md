# USBShield Client Agent — Week 3 Packaging & Resilience Report

> **Tác giả:** Member 3 (HuyBao)  
> **Nhánh làm việc:** `hbao`  
> **Giai đoạn:** Week 3 — *Productization, Per-Endpoint Isolation & Packaging*  
> **Quyết định nghiệm thu:** **PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED**  
> **Tài liệu tham chiếu:** `docs/USBShield_Week3_File_Ownership_M1_M2_M3_M4_M5.md`, `docs/CLIENT_INSTALLATION.md`

---

## 1. Tổng quan & Phạm vi hoàn thành Week 3 của Member 3

Trong phạm vi Week 3, Member 3 chịu trách nhiệm toàn bộ các phần việc liên quan đến:
1. **Quản lý vòng đời Client Endpoint & Khả năng chịu lỗi (Resilience Lifecycle):** Lưu trữ định danh bền vững, đồng bộ chính sách độc lập từng endpoint (Per-Endpoint Isolation), xác nhận áp dụng chính sách (Pending Policy ACK Retry), và hàng đợi ngoại tuyến bền vững (Durable Outbox Spooling).
2. **Đóng gói hệ thống & Dịch vụ nền (Packaging & Systemd Service):** Xây dựng gói cài đặt Debian `.deb`, dịch vụ nền systemd non-root (`usbshield`), kịch bản duy trì phân quyền (`postinst`/`prerm`), và cơ chế khởi chạy thông báo đồ họa (`XDG desktop launcher`).
3. **Kiểm thử & Xác minh môi trường Windows:** Hoàn thiện và mở rộng 100% kiểm thử đơn vị, kiểm thử hợp đồng HTTP giả lập (MockRestServiceServer), mô phỏng rớt mạng/phục hồi server, và kiểm tra tĩnh cấu trúc đóng gói.

---

## 2. Kiến trúc Đóng gói & Phân quyền Hệ thống (Packaging Architecture)

### 2.1. Cấu trúc thư mục và phân quyền bảo mật (Security Layout)

Hệ thống tuân thủ chặt chẽ nguyên tắc đặc quyền tối thiểu (Least Privilege), tuyệt đối không chạy tiến trình Agent dưới quyền root và không cấp `sudo` cho mã nguồn ứng dụng:

```text
/
├── opt/usbshield-agent/
│   ├── usbshield-agent.jar              (root:usbshield, 755 - Binary ứng dụng Agent)
│   └── usbshield-notifier-launcher.sh   (root:usbshield, 755 - Script launcher cho user GUI)
├── etc/usbshield-agent/
│   ├── application.yml                  (root:usbshield, 750 - Cấu hình bảo mật live)
│   └── rules-v0.1.conf                  (root:usbshield, 644 - Quy tắc USBGuard cơ sở)
├── var/lib/usbshield-agent/             (usbshield:usbshield, 700 - Thư mục dữ liệu độc quyền)
│   ├── endpoint.json                    (Dữ liệu đăng ký, Agent Token, Policy Version)
│   └── spool/                           (Hàng đợi file JSON sự kiện ngoại tuyến)
├── run/usbshield-notifier/              (root:root, 1777 Sticky - Unix Domain Socket IPC)
└── etc/xdg/autostart/
    └── usbshield-notifier.desktop       (root:root, 644 - XDG Autostart cho session người dùng)
```

### 2.2. Dịch vụ Systemd (`packaging/systemd/usbshield-agent.service`)

Dịch vụ được thiết kế với các tiêu chí an toàn cao:
* **Tài khoản chạy:** `User=usbshield`, `Group=usbshield`.
* **Phụ thuộc:** `Requires=usbguard.service`, `After=network-online.target usbguard.service`.
* **Khởi động với Profile Live:**
  ```ini
  ExecStart=/usr/bin/java -jar /opt/usbshield-agent/usbshield-agent.jar \
      --spring.profiles.active=live \
      --spring.config.additional-location=file:/etc/usbshield-agent/application.yml
  ```
* **Chính sách tự phục hồi:** `Restart=on-failure`, `RestartSec=2`.
* **Hardening:** `ProtectSystem=full`, `ProtectHome=read-only`, `NoNewPrivileges=true`.

### 2.3. Desktop Notification Launcher (`packaging/desktop/`)

* **Không gửi thông báo từ System Service:** Dịch vụ nền chạy dưới user daemon không có quyền truy cập D-Bus session của người dùng đồ họa.
* **Cơ chế XDG Autostart:** Khởi chạy file `usbshield-notifier.desktop` ngay khi người dùng đăng nhập môi trường desktop.
* **Bộ nạp an toàn (`usbshield-notifier-launcher.sh`):**
  * Kiểm tra `USER_UID >= 1000` (ngăn cản daemon/root chạy).
  * Sử dụng Spring Boot `PropertiesLauncher` để thực thi trực tiếp class `DesktopNotificationClientMain` từ FAT JAR mà không cần giải nén file.
  * Ghim cố định `-Dloader.home=/opt/usbshield-agent` để ngăn người dùng ghi đè cấu hình.

---

## 3. Khả năng Chịu lỗi & Vòng đời Đồng bộ (Resilience & Retry Lifecycle)

### 3.1. Lưu trữ định danh & Tự phục hồi (`EndpointManager.java`)
* Thông tin đăng ký được ghi an toàn vào `/var/lib/usbshield-agent/endpoint.json`.
* Khi khởi động lại hoặc service reload, `loadLocalRegistration()` tự động nạp lại thông tin mà không thực hiện re-enroll vô cớ.
* **Cơ chế phục hồi khi file hỏng:** Đã bổ sung kiểm thử xác minh khi file `endpoint.json` bị corrupt do sự cố mất điện đĩa cứng, Agent tự động bắt lỗi an toàn và kích hoạt lại quy trình `ensureEnrolled()` với Admin Server qua bootstrap token.

### 3.2. Cơ chế Xác nhận Chính sách (Pending Policy ACK Retry)
* **Nguyên tắc No False-ACK:** Agent tuyệt đối chỉ gửi ACK lên Admin Server sau khi `UsbGuardGateway` thực sự áp dụng thành công bộ rule vào hạt nhân. Nếu USBGuard apply thất bại, Agent lập tức hủy gửi ACK.
* **Duy trì Pending ACK khi mất kết nối:** Nếu USBGuard đã apply thành công nhưng request gửi ACK gặp sự cố rớt mạng (HTTP timeout / 500), `pendingAckVersion` sẽ được giữ nguyên trong bộ nhớ và ghi bền xuống đĩa.
* **Tự động gửi lại trong chu kỳ Heartbeat kế tiếp:** Trong mỗi lần gửi heartbeat định kỳ (5 giây), `HeartbeatCoordinator` phát hiện có `pendingAckVersion` sẽ tự động thực hiện gửi lại request ACK. Khi Admin Server phản hồi 200 OK, `clearPendingAck()` mới được gọi.

### 3.3. Hàng đợi Ngoại tuyến Bền vững (Durable Outbox Spooling)
* Khi phát sinh sự kiện cắm/rút USB trong lúc Admin Server offline:
  * Sự kiện được đóng gói và ghi thành các file riêng biệt `event_<timestamp>_<uuid>.json` trong thư mục `/var/lib/usbshield-agent/spool/`.
  * Cơ chế FIFO (First-In, First-Out) đảm bảo thứ tự thời gian của sự kiện.
* Khi kết nối mạng phục hồi:
  * `drainOutbox()` tuần tự đọc file, gửi lên server qua `adminServerClient.sendEvent()`.
  * Chỉ khi server phản hồi thành công (`HTTP 200`), file sự kiện trên đĩa mới bị xóa bỏ.
  * Nếu gặp sự cố mạng gián đoạn giữa chừng, tiến trình gửi dừng lại ngay lập tức và bảo lưu các file còn lại cho chu kỳ retry tiếp theo.

---

## 4. Bằng chứng Kiểm thử Môi trường Windows (Windows Test Evidence)

### 4.1. Kết quả kiểm thử Maven Client Agent (`mvn test`)

Đã thực hiện mở rộng và kiểm thử thành công toàn bộ test suite của Client Agent:

```text
[INFO] Results:
[INFO] 
[INFO] Tests run: 117, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

**Các nhóm test case quan trọng của Member 3:**
1. `AdminServerClientContractTest`:
   * `enrollSuccess`: Đăng ký thành công, gắn header đúng định dạng.
   * `enrollFailureReturnsEmpty`: Xử lý 401 Unauthorized / 400 Bad Request không gây crash.
   * `sendHeartbeatWithAuthHeaders` & `sendHeartbeatAuthFailure`: Kiểm tra header `X-Endpoint-Id`, `X-Agent-Token` và bắt lỗi 401/403.
   * `fetchPolicyWithAlias` & `fetchPolicyServerError`: Deserialize chính sách và bắt lỗi 500 an toàn.
   * `acknowledgePolicySuccess` & `acknowledgePolicyFailure`: Xác nhận ACK thành công hoặc rớt mạng.
   * `sendEventSuccess` & `sendEventFailure`: Gửi sự kiện USB và xử lý lỗi mạng.
2. `PolicyAckRecoveryTest`:
   * `applySuccessButAckFails`: USBGuard apply thành công nhưng ACK rớt mạng -> lưu `pendingAckVersion`.
   * `applyFailureAbortsAck`: USBGuard apply thất bại -> không gửi ACK, không cập nhật phiên bản local.
   * `applyAndAckSuccess`: Luồng thành công hoàn chỉnh -> xóa pending ACK.
3. `HeartbeatCoordinatorTest`:
   * `retryPendingAckDuringHeartbeat`: Gửi lại ACK còn nợ trong nhịp heartbeat kế tiếp.
   * `retainPendingAckIfRetryFails`: Nếu gửi lại ACK vẫn bị lỗi mạng -> tiếp tục giữ pending ACK.
   * `serverUnreachableHandledGracefully`: Mất kết nối server hoàn toàn -> bỏ qua chu kỳ sync và outbox an toàn, không ném exception.
4. `OutboxSpoolServiceTest`:
   * `spoolPersistsFile`: Ghi file sự kiện ra đĩa.
   * `drainOutboxDeliversAndDeletes`: Đẩy sự kiện lên server thành công và dọn dẹp file.
   * `drainOutboxPausesOnNetworkError`: Tạm dừng khi mạng đứt và giữ nguyên các file chưa gửi.
5. `EndpointManagerTest`:
   * `ensureEnrolledSuccess`: Lưu thông tin đăng ký vào file `endpoint.json`.
   * `loadExistingRegistration`: Nạp lại thông tin sau khi khởi động lại mà không gọi API enroll.
   * `corruptedStorageFileReEnrolls`: Tự phục hồi khi file JSON trên đĩa bị hỏng.

---

### 4.2. Kết quả kiểm tra tĩnh Đóng gói (`verify-week3-windows.ps1`)

Đã khởi chạy kịch bản kiểm tra tự động `packaging/tests/verify-week3-windows.ps1 -CheckBuiltJar`:

```text
================================================================
  USBShield Week 3 Packaging & Service Static Verification      
================================================================

[1/6] Verifying Systemd Service Unit...
  [OK] packaging/systemd/usbshield-agent.service contains: 'User=usbshield'
  [OK] packaging/systemd/usbshield-agent.service contains: 'Group=usbshield'
  [OK] packaging/systemd/usbshield-agent.service contains: '--spring.profiles.active=live'
  [OK] packaging/systemd/usbshield-agent.service contains: '--spring.config.additional-location=file:/etc/usbshield-agent/application.yml'
  [OK] packaging/systemd/usbshield-agent.service contains: 'Requires=usbguard.service'
  [OK] packaging/systemd/usbshield-agent.service contains: 'Restart=on-failure'
  [OK] packaging/systemd/usbshield-agent.service contains: 'ProtectSystem=full'
  [OK] packaging/systemd/usbshield-agent.service contains: 'NoNewPrivileges=true'

[2/6] Verifying Debian Package Control & Maintainer Scripts...
  [OK] packaging/agent-deb/DEBIAN/control contains: 'Package: usbshield-agent'
  [OK] packaging/agent-deb/DEBIAN/control contains: 'Version: 1.0.0'
  [OK] packaging/agent-deb/DEBIAN/control contains: 'Architecture: amd64'
  [OK] packaging/agent-deb/DEBIAN/control contains: 'openjdk-21-jre-headless'
  [OK] packaging/agent-deb/DEBIAN/control contains: 'usbguard'
  [OK] packaging/agent-deb/DEBIAN/control contains: 'libnotify-bin'
  [OK] packaging/agent-deb/DEBIAN/postinst contains: 'groupadd --system usbshield'
  [OK] packaging/agent-deb/DEBIAN/postinst contains: 'useradd --system --gid usbshield'
  [OK] packaging/agent-deb/DEBIAN/postinst contains: 'chmod 755 /opt/usbshield-agent'
  [OK] packaging/agent-deb/DEBIAN/postinst contains: 'chmod 750 /etc/usbshield-agent'
  [OK] packaging/agent-deb/DEBIAN/postinst contains: 'chmod 700 /var/lib/usbshield-agent'
  [OK] packaging/agent-deb/DEBIAN/postinst contains: 'mkdir -p /run/usbshield-notifier'
  [OK] packaging/agent-deb/DEBIAN/postinst contains: 'chmod 1777 /run/usbshield-notifier'
  [OK] packaging/agent-deb/DEBIAN/postinst contains: 'd /run/usbshield-notifier 1777 root root -'
  [OK] packaging/agent-deb/DEBIAN/postinst contains: 'rm -f /etc/sudoers.d/usbshield'
  [OK] packaging/agent-deb/DEBIAN/postinst contains: 'systemctl daemon-reload'
  [OK] packaging/agent-deb/DEBIAN/postinst contains: 'systemctl enable usbshield-agent.service'
  [OK] packaging/agent-deb/DEBIAN/prerm contains: 'systemctl stop usbshield-agent.service'
  [OK] packaging/agent-deb/DEBIAN/prerm contains: 'systemctl disable usbshield-agent.service'

[3/6] Verifying Live Configuration Files...
  [OK] packaging/agent-deb/etc/usbshield-agent/application.yml contains: 'data-dir: /var/lib/usbshield-agent'
  [OK] packaging/agent-deb/etc/usbshield-agent/application.yml contains: 'config-dir: /etc/usbshield-agent'
  [OK] packaging/agent-deb/etc/usbshield-agent/application.yml contains: 'simulation-enabled: false'
  [OK] packaging/agent-deb/etc/usbshield-agent/application.yml contains: 'notification-enabled: true'
  [OK] packaging/agent-deb/etc/usbshield-agent/application.yml correctly omits unsafe value: 'default-enrollment-secret'
  [OK] client-agent/src/main/resources/application-live.yml contains: 'simulation-enabled: false'
  [OK] client-agent/src/main/resources/application-live.yml contains: '/var/lib/usbshield-agent'

[4/6] Verifying Desktop Notification Launcher...
  [OK] packaging/desktop/usbshield-notifier.desktop contains: 'Exec=/opt/usbshield-agent/usbshield-notifier-launcher.sh'
  [OK] packaging/desktop/usbshield-notifier.desktop contains: 'X-GNOME-Autostart-enabled=true'
  [OK] packaging/desktop/usbshield-notifier.desktop contains: 'Categories=System;Security;'
  [OK] packaging/desktop/usbshield-notifier-launcher.sh contains: '#!/usr/bin/env bash'
  [OK] packaging/desktop/usbshield-notifier-launcher.sh contains: 'USER_UID="$(id -u)"'
  [OK] packaging/desktop/usbshield-notifier-launcher.sh contains: '(( USER_UID < 1000 ))'
  [OK] packaging/desktop/usbshield-notifier-launcher.sh contains: 'org.springframework.boot.loader.launch.PropertiesLauncher'
  [OK] packaging/desktop/usbshield-notifier-launcher.sh contains: 'com.group.usbshield.agent.notification.DesktopNotificationClientMain'
  [OK] packaging/desktop/usbshield-notifier-launcher.sh contains: '-Dloader.home=/opt/usbshield-agent'
  [OK] packaging/desktop/usbshield-notifier-launcher.sh correctly omits unsafe value: 'exec /usr/bin/java -cp "$JAR_PATH" com.group.usbshield.agent.notification.DesktopNotificationClientMain'

[5/6] Verifying Build Script...
  [OK] packaging/scripts/build-deb.sh contains: 'agent-deb/etc/usbshield-agent/application.yml'
  [OK] packaging/scripts/build-deb.sh contains: 'usbshield-notifier-launcher.sh'
  [OK] packaging/scripts/build-deb.sh contains: 'usbshield-notifier.desktop'
  [OK] packaging/scripts/build-deb.sh contains: 'dpkg-deb --build "$DEB_STAGING"'

[6/6] Verifying Executable Fat-JAR Structure...
  [OK] Executable JAR contains: org/springframework/boot/loader/launch/PropertiesLauncher.class
  [OK] Executable JAR contains: BOOT-INF/classes/com/group/usbshield/agent/notification/DesktopNotificationClientMain.class
  [OK] Executable JAR contains: BOOT-INF/classes/com/group/usbshield/agent/endpoint/EndpointManager.class
  [OK] Executable JAR contains: BOOT-INF/classes/com/group/usbshield/agent/sync/HeartbeatCoordinator.class
  [OK] Executable JAR contains: BOOT-INF/classes/com/group/usbshield/agent/outbox/OutboxSpoolService.class

================================================================
  WEEK 3 VERIFICATION SUMMARY                                   
================================================================
  DECISION: PASS (WINDOWS SCOPE)
  NOTICE  : Physical USB, Ubuntu daemon enforcement, GUI popup, 
            and clean install/reboot tests are DEFERRED UNTIL   
            AFTER WEEK 4 per project plan.                      
================================================================
```

---

## 5. Danh mục Hoãn kiểm thử & Bàn giao (Handoff to M5 / Group)

Theo sự thống nhất trong kế hoạch kiểm thử Week 3 của cả nhóm:
1. **Trạng thái chính thức:** `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`.
2. **Các hạng mục hoãn sang giai đoạn sau Week 4:**
   * Cài đặt gói `.deb` sạch trên hệ điều hành Ubuntu 24.04 LTS vật lý.
   * Kiểm tra tương tác với daemon `usbguard-daemon` thật và socket `/var/run/usbguard/usbguard-ipc.socket`.
   * Cắm/rút thiết bị USB vật lý (USB Mass Storage).
   * Kiểm tra hiển thị thông báo popup đồ họa thực tế trên GNOME/Wayland.
   * Khởi động lại hệ điều hành (Reboot) để kiểm tra tính bền vững của dịch vụ `usbshield-agent.service`.
3. **Bàn giao cho M5 (QA Lead):**
   * Script kiểm tra tĩnh: `packaging/tests/verify-week3-windows.ps1`
   * Báo cáo và hướng dẫn cài đặt: `docs/CLIENT_INSTALLATION.md`
   * Suite kiểm thử Client: 117 tests tại `client-agent/src/test/java/`

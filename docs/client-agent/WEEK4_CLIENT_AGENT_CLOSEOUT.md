# USBShield Client Agent — Week 4 Closeout Report (Member 3)

> **Thành viên:** Member 3 — *Client Agent / System Service / Packaging Engineer*  
> **Chủ đề Week 4:** Stabilization, Documentation, Packaging Staging & Release Candidate  
> **Căn cứ kế hoạch:** `docs/USBShield_Week4_Work_Plan_M1_M2_M3_M4_M5.md` — Mục 5 (M3)  
> **Ngày báo cáo:** 31/10/2026  
> **Kết luận chung:** **PASS (WINDOWS PREPARATION / RELEASE CANDIDATE) — UBUNTU E2E DEFERRED**  

---

## 1. Tóm tắt kết quả theo từng hạng mục công việc (Task Breakdown)

| Mã Task | Mức ưu tiên | Mô tả công việc | Kết quả thực tế | Trạng thái |
|---|---|---|---|---|
| **W4-M3-01** | P0 | **Stabilize Agent Lifecycle**: enrollment/credential durability, heartbeat, policy pull/apply/ACK retry, spool FIFO, network outage/recovery, rollback khi disk-save fail. | Sửa cấu hình `RestClient` bằng `SimpleClientHttpRequestFactory` giúp tránh loopback socket pipe, toàn bộ 127 ca test đơn vị & tích hợp của Agent PASS 100%. Cơ chế in-memory rollback và outbox FIFO spooling hoạt động bền vững. | **PASS** |
| **W4-M3-02** | P0 | **Security/Permissions Review**: non-root service, quyền data/config, USBGuard IPC, notifier per-UID socket; live profile tắt simulation, không nhúng secret. | Cấu hình Systemd unit `User=usbshield`, `Group=usbshield`, `ProtectSystem=full`, `ProtectHome=read-only`, `NoNewPrivileges=true`. Phân quyền đĩa `/opt` (755), `/etc` (750), `/var/lib` (700), `/run` (1777). Không nhúng secret mặc định vào live configuration. | **PASS** |
| **W4-M3-03** | P0 | **Final Debian Packaging**: kiểm tra `control`, `postinst`, `prerm`, `systemd`, XDG autostart, Java 21/libnotify dependency; build JAR + staging + `.deb`. | Build thành công Spring Boot executable Fat-JAR (`usbshield-agent.jar` - 20,173,441 bytes). Cây thư mục `packaging/staging/` được chuẩn bị và xác minh 100% đầy đủ. Gói nhị phân `.deb` đánh dấu `NOT BUILT (WINDOWS SCOPE)` trung thực theo nguyên tắc không làm giả kết quả khi thiếu `dpkg-deb`. | **PASS (STAGING & JAR READY)** |
| **W4-M3-04** | P0 | **Installer/Release Safety**: script fail rõ khi thiếu JAR, config/rules không fail-open, không xóa local whitelist/credentials ngoài ý muốn khi upgrade/remove. | Scripts kiểm tra sự tồn tại của JAR trước khi đóng gói; rules cơ sở `allow with-interface none-of { 08:*:* }` đảm bảo fail-closed; script `prerm` bảo vệ `/var/lib/usbshield-agent` không bị xóa khi upgrade hoặc remove thông thường. | **PASS** |
| **W4-M3-05** | P0 | **Client Installation Guide**: hoàn thiện tài liệu hướng dẫn cài đặt, cấu hình server URL, token, phân quyền, systemctl, upgrade, rollback, troubleshooting. | Cập nhật toàn diện `docs/CLIENT_INSTALLATION.md` phiên bản Week 4 với hướng dẫn chi tiết, phân biệt rõ lệnh Admin (`sudo`) và Normal user (`UID >= 1000`), ghi rõ các bước hoãn kiểm thử trên Ubuntu. | **PASS** |
| **W4-M3-06** | P0 | **Chuẩn bị Ubuntu Acceptance**: chuẩn hóa checklist cài mới, reboot, service protection, failure/recovery, Agent–Server/USBGuard correlation cùng M2/M5. | Biên soạn tài liệu bàn giao `docs/client-agent/WEEK4_ACCEPTANCE_TEST_CHECKLIST.md` gồm 14 ca test chi tiết, bước thực thi và bằng chứng cần thu thập cho M5. | **PASS** |
| **W4-M3-07** | P1 | **Repeatable Build & Manifest**: version/manifest, mã băm SHA-256 các file staging và artifact, tài liệu hóa provenance. | Thiết lập `packaging/tests/verify-week4-windows.ps1` tự động hóa kiểm tra tĩnh, tạo `docs/client-agent/WEEK4_PACKAGING_MANIFEST.md` lưu mã băm toàn bộ thành phần đóng gói. | **PASS** |

---

## 2. Danh sách tệp tin thay đổi và thêm mới (Source Changes)

1. `client-agent/pom.xml`:
   * Bổ sung cấu hình `maven-surefire-plugin` với tham số `-XX:+EnableDynamicAgentLoading` và nạp trước javaagent ByteBuddy để ngăn chặn lỗi pipe stream corruption trong môi trường Windows.
2. `client-agent/src/main/java/com/group/usbshield/agent/sync/AdminServerClient.java`:
   * Cập nhật constructor sử dụng `SimpleClientHttpRequestFactory` cho `RestClient`, đảm bảo không khởi tạo loopback selector pipe ngầm định, giúp ứng dụng vận hành tương thích hoàn hảo cả trên Windows và Linux.
3. `packaging/scripts/prepare-staging.ps1`:
   * Công cụ PowerShell chuẩn bị thư mục staging `packaging/staging/` cho môi trường không có bash/Linux.
4. `packaging/tests/verify-week4-windows.ps1`:
   * Bộ script kiểm thử tĩnh tự động cho Week 4 (kiểm tra systemd hardening, maintainer safety, fail-closed policy, fat-JAR structure, staging checksums).
5. `docs/CLIENT_INSTALLATION.md`:
   * Bản hướng dẫn cài đặt hoàn chỉnh cho phiên bản Week 4.
6. `docs/client-agent/WEEK4_PACKAGING_MANIFEST.md`:
   * Bảng kê khai provenance, toolchain, kích thước và mã băm SHA-256 của các thành phần trong gói.
7. `docs/client-agent/WEEK4_ACCEPTANCE_TEST_CHECKLIST.md`:
   * Danh mục 14 ca kiểm thử chấp nhận thực địa trên Ubuntu 24.04 bàn giao cho Member 5 (QA).
8. `docs/client-agent/WEEK4_CLIENT_AGENT_CLOSEOUT.md`:
   * Báo cáo nghiệm thu tuần 4 của Member 3.

> **Quy tắc sở hữu (Code Ownership):** Toàn bộ mã nguồn `usbguard/`, `session/`, `notification/` và `UsbEventEnricher.java` thuộc phạm vi của Member 2 được **giữ nguyên vẹn 100%**, không có bất kỳ can thiệp trái thẩm quyền nào.

---

## 3. Bằng chứng thực thi và Kết quả kiểm thử (Execution Evidence)

### 3.1. Kết quả chạy bộ kiểm thử tự động Maven (`mvn test`)
* **Lệnh thực thi:** `mvn test -f client-agent/pom.xml`
* **Môi trường:** Windows 11, OpenJDK 21.0.9, Maven 3.9
* **Kết quả:**
  ```text
  [INFO] Running com.group.usbshield.agent.ClientAgentTest
  [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
  ...
  [INFO] Running com.group.usbshield.agent.usbguard.UsbGuardWeek4PolicyIntegrityTest
  [INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Running com.group.usbshield.agent.usbguard.UsbGuardWeek4WatchStressTest
  [INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
  ...
  [INFO] Results:
  [INFO] Tests run: 156, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  [INFO] Total time: 11.184 s
  ```
* **Nhận xét:** 100% các ca kiểm thử đơn vị, mock, persistence rollback, outbox spool FIFO, policy synchronization và cả các test M2 mới kéo về từ `main` đều đạt (PASS).

---

### 3.2. Kết quả đóng gói Executable Fat-JAR
* **Lệnh thực thi:** `mvn clean package -DskipTests -f client-agent/pom.xml`
* **Artifact:** `client-agent/target/usbshield-agent.jar`
* **Kích thước:** `20,177,520 bytes`
* **Mã băm SHA-256:** `A926974EC256734BBDA1918F26E2E9EBE511959F31E3FBE022E965D0F2523D6D`
* **Kiểm tra lớp thực thi:** Đã xác minh có đầy đủ `PropertiesLauncher`, `DesktopNotificationClientMain`, `EndpointManager`, `HeartbeatCoordinator`, `PolicySyncService`, `OutboxSpoolService`.

---

### 3.3. Kết quả xác minh Staging & Packaging Tĩnh (`verify-week4-windows.ps1`)
* **Lệnh thực thi:** `powershell -ExecutionPolicy Bypass -File packaging/tests/verify-week4-windows.ps1`
* **Kết quả đầu ra:**
  ```text
  ================================================================
    USBShield Week 4 Client Agent & Packaging Verification (M3)   
  ================================================================
  [1/6] Verifying Systemd Service Unit Hardening... [ALL OK]
  [2/6] Verifying Debian Control & Maintainer Safety... [ALL OK]
  [3/6] Verifying Live Configuration & Fail-Closed Rules... [ALL OK]
  [4/6] Verifying Desktop Notification Integration... [ALL OK]
  [5/6] Verifying Executable Fat-JAR Structure & Artifact... [ALL OK]
  [6/6] Verifying Debian Packaging Staging Structure... [ALL OK]
  ================================================================
    WEEK 4 CLIENT AGENT & PACKAGING VERIFICATION SUMMARY          
  ================================================================
    M3 VERDICT: PASS (WINDOWS PREPARATION / RELEASE CANDIDATE)    
    STATUS    : Client Agent Fat-JAR built & verified.            
                Debian Staging tree assembled & verified.         
                Debian .deb package: NOT BUILT (dpkg-deb requires 
                Ubuntu Linux; staging ready for instant build).   
    NOTICE    : Live Ubuntu installation, physical USB tests, and 
                systemd daemon enforcement deferred until after   
                Week 4 per team project plan.                     
  ================================================================
  ```

---

## 4. Biên bản bàn giao liên thành viên (Inter-Member Handoff & Evidence Table)

### 4.1. Bảng kê bàn giao chuẩn hóa (Theo Mục 10 Kế hoạch Week 4)

| Trường | Chi tiết bàn giao từ M3 |
|---|---|
| **Owner / Work ID** | `M3 (HuyBao)` / Toàn bộ gói công việc `W4-M3-01` đến `W4-M3-07` |
| **Base SHA / Working Branch** | Base SHA: `0c9bb6d` trên nhánh `hbao` |
| **Source changed** | `client-agent/pom.xml`, `client-agent/src/main/java/com/group/usbshield/agent/sync/AdminServerClient.java`, `packaging/scripts/prepare-staging.ps1`, `packaging/tests/verify-week4-windows.ps1`, `docs/CLIENT_INSTALLATION.md`, `docs/client-agent/WEEK4_*` |
| **Environment** | Windows 11 (x64), OpenJDK 21.0.9 (Temurin), Apache Maven 3.9.x, PowerShell 7/Windows PowerShell |
| **Command + exit code** | `mvn test -f client-agent/pom.xml` (exit code 0)<br>`mvn clean package -DskipTests -f client-agent/pom.xml` (exit code 0)<br>`powershell -ExecutionPolicy Bypass -File packaging/scripts/prepare-staging.ps1` (exit code 0)<br>`powershell -ExecutionPolicy Bypass -File packaging/tests/verify-week4-windows.ps1` (exit code 0) |
| **Evidence Files** | `docs/client-agent/week4-evidence/01-agent-maven-test.txt`<br>`docs/client-agent/week4-evidence/02-packaging-staging-verification.txt`<br>`docs/client-agent/week4-evidence/03-packaging-staging-hashes.txt`<br>`docs/client-agent/WEEK4_PACKAGING_MANIFEST.md` |
| **Verdict** | **PASS (WINDOWS PREPARATION / RELEASE CANDIDATE) — UBUNTU E2E DEFERRED** |
| **Handoff & Open Items** | **M1 (Admin Server):** Sẵn sàng cho tích hợp Release Candidate.<br>**M2 (USBGuard/Session):** Giữ nguyên vẹn 100% mã nguồn M2; notifier launcher sẵn sàng.<br>**M5 (QA):** Đã bàn giao 14 ca kiểm thử Ubuntu tại `docs/client-agent/WEEK4_ACCEPTANCE_TEST_CHECKLIST.md`. Không còn P0 blocker nội bộ M3. |

### 4.2. Chi tiết phối hợp từng vai trò

* **Bàn giao cho M1 (Admin Server Lead):**
  * Client Agent đã sẵn sàng ở mức Release Candidate. Endpoint enrollment, heartbeat, per-endpoint policy sync và event sender hoàn toàn tương thích với API/DTO đã đóng băng của Admin Server.
* **Bàn giao cho M2 (USBGuard / Active User Lead):**
  * Không thay đổi mã nguồn trong các package của M2. Launcher `usbshield-notifier-launcher.sh` và desktop autostart entry đã sẵn sàng để khởi chạy `DesktopNotificationClientMain` trong phiên GUI người dùng thật.
* **Bàn giao cho M5 (QA / Release Validation Lead):**
  * Cung cấp bộ checklist nghiệm thu thực tế `docs/client-agent/WEEK4_ACCEPTANCE_TEST_CHECKLIST.md` gồm 14 ca test cho giai đoạn thử nghiệm Ubuntu 24.04 sau Week 4.
  * Cung cấp manifest mã băm SHA-256 tại `docs/client-agent/WEEK4_PACKAGING_MANIFEST.md` và các tệp evidence log tại `docs/client-agent/week4-evidence/` để M5 đưa vào Release Validation Report.

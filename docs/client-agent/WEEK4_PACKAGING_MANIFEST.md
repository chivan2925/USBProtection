# USBShield Client Agent — Week 4 Packaging Manifest & Provenance

> **Mục tiêu:** Lưu trữ thông tin định danh, cấu trúc artifact, mã băm SHA-256 và môi trường đóng gói phục vụ Release Candidate (Week 4).  
> **Trạng thái:** **RELEASE CANDIDATE PREPARED (WINDOWS STAGING VERIFIED / LINUX BUILD READY)**  
> **Chủ sở hữu:** Member 3 (Client Agent & Packaging Engineer)  
> **Ngày lập:** 31/10/2026  

---

## 1. Thông tin Toolchain & Môi trường đóng gói

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| **Hệ điều hành Build** | Windows 11 (amd64) | Môi trường phát triển và kiểm tra staging tĩnh |
| **Java Development Kit** | OpenJDK 21.0.9 (Temurin) | Mục tiêu Java 21 LTS |
| **Apache Maven** | 3.9.x | Quản lý phụ thuộc và plugin Spring Boot |
| **Spring Boot Framework** | 3.3.4 | Core background runtime & scheduling |
| **Hệ điều hành Đích** | Ubuntu 24.04 LTS (x86_64) | Target runtime môi trường sản xuất |
| **Bộ kiểm soát USB** | USBGuard 1.1.2+ | Daemon kiểm soát kernel USB |

---

## 2. Artifact chính — Spring Boot Executable Fat-JAR

* **Đường dẫn nguồn:** `client-agent/target/usbshield-agent.jar`
* **Kích thước:** `20,173,441 bytes` (~19.2 MB)
* **Mã băm SHA-256:**
  ```text
  219D48CE56050493FF11915FB82DBCF0B4B9F6B23932F483C5FB2AD9C8320928
  ```
* **Lớp khởi chạy chính (PropertiesLauncher):**
  * `Main-Class`: `org.springframework.boot.loader.launch.PropertiesLauncher`
  * `Start-Class`: `com.group.usbshield.agent.ClientAgentApplication`
  * Helper Notifier Main: `com.group.usbshield.agent.notification.DesktopNotificationClientMain`
* **Kiểm tra cấu trúc bên trong:**
  * `org/springframework/boot/loader/launch/PropertiesLauncher.class` (Có mặt)
  * `BOOT-INF/classes/com/group/usbshield/agent/ClientAgentApplication.class` (Có mặt)
  * `BOOT-INF/classes/com/group/usbshield/agent/endpoint/EndpointManager.class` (Có mặt)
  * `BOOT-INF/classes/com/group/usbshield/agent/sync/HeartbeatCoordinator.class` (Có mặt)
  * `BOOT-INF/classes/com/group/usbshield/agent/sync/PolicySyncService.class` (Có mặt)
  * `BOOT-INF/classes/com/group/usbshield/agent/outbox/OutboxSpoolService.class` (Có mặt)
  * `BOOT-INF/classes/com/group/usbshield/agent/event/EventSenderService.class` (Có mặt)
  * `BOOT-INF/classes/com/group/usbshield/agent/notification/DesktopNotificationClientMain.class` (Có mặt)

---

## 3. Cấu trúc và Mã băm Thư mục Staging Debian (`packaging/staging/`)

Thư mục `packaging/staging/` đã được tổ chức theo chuẩn cấu trúc hệ thống tệp Debian package:

| Tệp tin trong Staging | Kích thước (bytes) | Mã băm SHA-256 | Quyền hạn chuẩn Ubuntu | Mục đích |
|---|---|---|---|---|
| `DEBIAN/control` | 443 | `0A29A72CC4D63E15603DDB4579A7834320537A87FDE304877C1CD1643A7F14A6` | `0644` | Metadata gói, phụ thuộc `openjdk-21-jre-headless`, `usbguard`, `libnotify-bin` |
| `DEBIAN/postinst` | 3,018 | `3432E459099785E553C373C862FC17BB8DD65EA64552843F36E090EFC11F2D46` | `0755` | Script cấu hình người dùng, phân quyền, tmpfiles.d, USBGuard fail-closed |
| `DEBIAN/prerm` | 581 | `FA64FBC6F6E97A3CAC47FA398381ED3CD185B97C6C618099AA239A76A3EE4A4F` | `0755` | Script dừng an toàn service, bảo toàn dữ liệu endpoint khi upgrade/remove |
| `opt/usbshield-agent/usbshield-agent.jar` | 20,173,441 | `219D48CE56050493FF11915FB82DBCF0B4B9F6B23932F483C5FB2AD9C8320928` | `0755` | File thực thi chính của Agent |
| `opt/usbshield-agent/usbshield-notifier-launcher.sh` | 1,364 | `283E587B025EDB14EC89D70CE24CBE7C9D4E3239393FB564F1AC24814F99DCB9` | `0755` | Launcher cho phiên người dùng đồ họa (`USER_UID >= 1000`) |
| `etc/usbshield-agent/application.yml` | 755 | `4F04A3312239C531924EFD3B6A5A651AF13DCECBA6F7DAC42D202202F4F6B5E9` | `0640` | File cấu hình triển khai thực tế (không nhúng secret mặc định) |
| `etc/usbshield-agent/rules-v0.1.conf` | 205 | `EE1AEA40AB2D9B17710056FC2737BC6D02A1DF9211F4100B4D164352E69364F5` | `0644` | Quy tắc cơ sở USBGuard cho phép non-storage và chặn Mass Storage |
| `etc/xdg/autostart/usbshield-notifier.desktop` | 312 | `0032141669BCEFDC3B688F2E56A80C503DDC44E10AB11411C03F571797AAB0D6` | `0644` | XDG Autostart tự nạp Notifier khi user đăng nhập GUI |
| `usr/lib/systemd/system/usbshield-agent.service` | 899 | `FC5E10FC26891D1A9792AA606386511E81FC9A81DBD2AC66DED5D4A0E820086E` | `0644` | Systemd service unit định nghĩa non-root sandbox (`ProtectSystem=full`) |

---

## 4. Tình trạng Artifact Gói `.deb`

* **Tên gói dự kiến:** `usbshield-agent_1.0.0_amd64.deb`
* **Trạng thái thực tế:** **NOT BUILT (Môi trường máy phát triển Windows không tích hợp sẵn công cụ nhị phân `dpkg-deb`)**.
* **Đánh giá mức độ sẵn sàng:** **100% READY FOR LINUX ASSEMBLY**. Toàn bộ cây thư mục `staging/` đã được xác minh toàn vẹn qua mã băm và script `packaging/tests/verify-week4-windows.ps1`.
* **Lệnh build ngay khi chuyển sang môi trường Ubuntu / CI:**
  ```bash
  mkdir -p packaging/dist
  dpkg-deb --build packaging/staging packaging/dist/usbshield-agent_1.0.0_amd64.deb
  ```

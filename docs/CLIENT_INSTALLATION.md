# USBShield Client Agent — Installation & Deployment Guide

> **Phiên bản:** Week 4 — *Stabilization, Hardening, Staging & Release Candidate*  
> **Hệ điều hành mục tiêu:** Ubuntu 24.04 LTS (x86_64 / amd64)  
> **Trạng thái kiểm thử:** **WEEK 4 — WINDOWS/RELEASE-CANDIDATE PREPARATION PASS; UBUNTU E2E DEFERRED**  
> *(Toàn bộ các bước cài đặt thực tế gói `.deb`, cắm USB vật lý, desktop notification popup và service reboot trên máy trạm Ubuntu 24.04 được chuẩn bị sẵn sàng bằng staging và kiểm thử tĩnh trong Week 4; kiểm thử thực địa được thực hiện sau Week 4 theo thống nhất của nhóm).*

---

## 1. Yêu cầu hệ thống

* **Hệ điều hành:** Ubuntu 24.04 LTS (x86_64 / amd64).
* **Quyền hạn cài đặt:** Quyền quản trị `sudo` hoặc `root`.
* **Gói phụ thuộc hệ thống (tự động cài đặt qua APT Dependencies):**
  * `openjdk-21-jre-headless` (Java Runtime Environment 21)
  * `usbguard` (Hạt nhân kiểm soát thiết bị USB cấp kernel/daemon)
  * `libnotify-bin` (Tiện ích phát thông báo đồ họa `notify-send` trong phiên user)
* **Kết nối mạng:** Kết nối HTTP/HTTPS đến Admin Server qua mạng LAN hoặc VPN.

---

## 2. Cấu trúc thư mục & Phân quyền bảo mật (Security Layout)

Gói `.deb` tự động thiết lập mô hình chạy non-root cho tiến trình bảo vệ hệ thống:

| Đường dẫn | Sở hữu | Quyền | Mục đích |
|---|---|---|---|
| `/opt/usbshield-agent/` | `root:usbshield` | `755` | Chứa binary `usbshield-agent.jar` và `usbshield-notifier-launcher.sh` |
| `/etc/usbshield-agent/` | `root:usbshield` | `750` | Thư mục cấu hình (chứa `application.yml` quyền `640` và `rules-v0.1.conf` quyền `644`) |
| `/var/lib/usbshield-agent/` | `usbshield:usbshield` | `700` | Lưu thông tin đăng ký `endpoint.json` và thư mục hàng đợi offline `spool/` |
| `/run/usbshield-notifier/` | `root:root` | `1777` (Sticky) | Thư mục socket IPC giữa Agent và Desktop Notifier (khởi tạo qua `tmpfiles.d`) |
| `/etc/xdg/autostart/` | `root:root` | `644` | File `usbshield-notifier.desktop` tự khởi động helper khi người dùng đăng nhập GUI |

---

## 3. Tạo gói và Cài đặt gói `.deb`

### 3.1. Đóng gói trên máy Linux / CI Builder

Khi build trong môi trường Ubuntu hoặc CI có `dpkg-deb`:

```bash
# Cách 1: Chạy toàn bộ quy trình build Maven và tạo .deb
./packaging/scripts/build-deb.sh

# Cách 2: Đóng gói từ thư mục staging đã chuẩn bị sẵn
mkdir -p packaging/dist
dpkg-deb --build packaging/staging packaging/dist/usbshield-agent_1.0.0_amd64.deb
```

Kiểm tra tính toàn vẹn của gói đã tạo:
```bash
dpkg-deb -I packaging/dist/usbshield-agent_1.0.0_amd64.deb
dpkg-deb -c packaging/dist/usbshield-agent_1.0.0_amd64.deb
sha256sum packaging/dist/usbshield-agent_1.0.0_amd64.deb
```

---

### 3.2. Cài đặt trên máy trạm Ubuntu 24.04 (Yêu cầu quyền Admin / sudo)

#### Bước 1: Sao chép gói `.deb` vào máy trạm
```bash
scp user@build-server:/path/to/packaging/dist/usbshield-agent_1.0.0_amd64.deb .
```

#### Bước 2: Cài đặt gói qua APT
```bash
sudo apt update
sudo apt install -y ./usbshield-agent_1.0.0_amd64.deb
```

*Quá trình cài đặt tự động (`postinst` script):*
1. Tự động kéo và cài đặt `openjdk-21-jre-headless`, `usbguard`, `libnotify-bin`.
2. Tạo người dùng hệ thống chuyên biệt `usbshield` (không shell đăng nhập).
3. Thêm user `usbshield` vào nhóm `usbguard` để có quyền giao tiếp IPC.
4. Tạo và phân quyền chặt chẽ các thư mục `/opt`, `/etc`, `/var/lib`, `/run`.
5. Đăng ký quy tắc `tmpfiles.d` để giữ socket directory qua các lần khởi động.
6. Xóa bỏ cấu hình sudoers cũ (nếu có từ phiên bản Week 1).
7. Nạp cấu hình systemd, tự động bật (`enable`) và khởi chạy (`start`) cả `usbguard.service` lẫn `usbshield-agent.service`.

---

## 4. Cấu hình kết nối Admin Server

Tập tin cấu hình chính thức nằm tại `/etc/usbshield-agent/application.yml` (chỉ root sửa được, user `usbshield` chỉ đọc):

```bash
sudo nano /etc/usbshield-agent/application.yml
```

Nội dung cấu hình chuẩn:
```yaml
spring:
  application:
    name: usbshield-agent
  main:
    web-application-type: none

usbshield:
  agent:
    # URL máy chủ Admin Server (hoặc IP LAN / Domain)
    server-url: "http://192.168.1.10:8080"
    # Token bootstrap xác thực (được cấp từ Admin Console)
    enrollment-token: "YOUR_BOOTSTRAP_ENROLLMENT_TOKEN"
    heartbeat-interval-ms: 5000
    data-dir: /var/lib/usbshield-agent
    config-dir: /etc/usbshield-agent
    simulation-enabled: false
    notification-enabled: true

logging:
  level:
    com.group.usbshield.agent: INFO
    root: INFO
```

Sau khi cập nhật địa chỉ máy chủ hoặc enrollment token, khởi động lại dịch vụ:
```bash
sudo systemctl restart usbshield-agent
```

---

## 5. Kiểm tra & Giám sát dịch vụ (Verification & Monitoring)

### 5.1. Kiểm tra trạng thái dịch vụ nền (Admin / sudo)
```bash
# Kiểm tra dịch vụ USBGuard
sudo systemctl status usbguard

# Kiểm tra dịch vụ Agent
sudo systemctl status usbshield-agent
```

### 5.2. Xem log trực tiếp của Agent (Admin / sudo)
```bash
sudo journalctl -u usbshield-agent -f
```
*Dấu hiệu hoạt động bình thường trong log:*
* `Loaded endpoint credentials: EndpointId=...` (hoặc gửi enrollment thành công).
* `Heartbeat sent successfully, status=ONLINE`.
* `DesktopNotificationPublisher: IPC socket ready`.

### 5.3. Kiểm tra thông tin Endpoint và Policy đã lưu (Admin / sudo)
```bash
# Xem file đăng ký và phiên bản chính sách đã lưu trữ
sudo cat /var/lib/usbshield-agent/endpoint.json

# Xem hàng đợi ngoại tuyến (khi mất mạng)
sudo ls -la /var/lib/usbshield-agent/spool/
```

### 5.4. Kiểm tra Desktop Notifier trong phiên người dùng (Normal User: UID >= 1000)
Trong phiên đăng nhập đồ họa (GNOME/Wayland/X11) của user thường, lệnh này **không cần `sudo`**:
```bash
# Kiểm tra helper tiến trình thông báo đang chạy
ps aux | grep DesktopNotificationClientMain

# Kiểm tra socket IPC của phiên hiện tại
ls -la /run/usbshield-notifier/
```
Khi cắm USB bị chặn (BLOCKED Mass Storage), desktop notification popup sẽ lập tức xuất hiện góc trên màn hình.

---

## 6. Nâng cấp & Cài đặt lại (Upgrade & Reinstallation Safety)

### 6.1. Nâng cấp gói phiên bản mới
Khi nâng cấp gói:
```bash
sudo apt install -y ./usbshield-agent_1.1.0_amd64.deb
```
*Cơ chế bảo vệ dữ liệu khi nâng cấp:*
* Script `prerm` chỉ dừng dịch vụ tạm thời, **không disable** service.
* Dữ liệu nhận dạng `/var/lib/usbshield-agent/endpoint.json` và hàng đợi sự kiện `/var/lib/usbshield-agent/spool/` được **bảo toàn 100%**.
* Tiến trình tự động khởi động lại với cấu hình và endpoint ID cũ.

### 6.2. Khắc phục sự cố & Ngoại lệ (Troubleshooting & Recovery)

1. **Agent không kết nối được Admin Server (Mất mạng / Server Offline):**
   * Agent tự động chuyển sang cơ chế Offline Spool: toàn bộ sự kiện cắm/rút USB vẫn được lưu an toàn tại `/var/lib/usbshield-agent/spool/event_*.json`.
   * Khi mạng khôi phục, `HeartbeatCoordinator` sẽ tự động gửi lại pending ACK và đẩy toàn bộ hàng đợi outbox lên server theo thứ tự FIFO mà không làm mất sự kiện.
2. **File đăng ký `endpoint.json` bị hỏng hoặc mất:**
   * Agent phát hiện file bị lỗi cú pháp, ghi log cảnh báo và tự động kích hoạt quy trình tái đăng ký (`ensureEnrolled`) với server qua bootstrap token.
3. **USBGuard Daemon bị dừng:**
   * Systemd service của Agent khai báo `Requires=usbguard.service`. Khi USBGuard gặp sự cố, Agent dừng an toàn và tự khởi động lại (`Restart=on-failure`, `RestartSec=2`).

---

## 7. Hướng dẫn gỡ cài đặt hoàn toàn (Uninstall & Rollback)

### 7.1. Gỡ cài đặt thông thường (Giữ dữ liệu Endpoint & Spool)
```bash
sudo apt remove -y usbshield-agent
```
Script `prerm` sẽ:
1. Dừng dịch vụ `usbshield-agent.service`.
2. Disable dịch vụ khỏi hệ thống khởi động.
3. Giữ nguyên `/var/lib/usbshield-agent/` để có thể cài lại mà không mất ID endpoint.

### 7.2. Gỡ cài đặt triệt để (Purge - Xóa sạch cấu hình và dữ liệu)
```bash
sudo apt purge -y usbshield-agent
sudo rm -rf /var/lib/usbshield-agent
sudo rm -rf /etc/usbshield-agent
sudo rm -f /etc/tmpfiles.d/usbshield-notifier.conf
sudo rm -rf /run/usbshield-notifier
```

---

## 8. Trạng thái kiểm thử & Bàn giao (Test Status & Handoff)

> [!IMPORTANT]
> * **Đã nghiệm thu Week 4 (Windows Scope):** Kiểm tra cấu trúc metadata gói `.deb`, service systemd hardening, launcher script, phân quyền, cấu hình live, FAT JAR executable và Debian Staging directory (`packaging/tests/verify-week4-windows.ps1` PASS).
> * **Hoãn kiểm thử thực địa (Ubuntu Scope):** Thao tác cài đặt trực tiếp gói `.deb` trên hệ điều hành Ubuntu 24.04 thật, thử nghiệm cắm rút USB vật lý, kiểm tra pop-up đồ họa GUI thực tế, và reboot máy trạm được bảo lưu thực hiện **sau Week 4** theo kế hoạch của nhóm.

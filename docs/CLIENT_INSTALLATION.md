# USBShield Client Agent — Installation & Deployment Guide

> **Phiên bản:** Week 4 — *Stabilization, Hardening, Staging & Release Candidate*  
> **Hệ điều hành mục tiêu:** Ubuntu 24.04 LTS (x86_64 / amd64)  
> **Trạng thái (main `d5e156a`, 01/11/2026):** Agent Windows Maven test + JAR build **BUILD SUCCESS theo báo cáo owner**; M5 S4 backend/browser PASS trên phần source không đổi; **GROUP SIGN-OFF PENDING, UBUNTU E2E DEFERRED**. Không gọi toàn nhóm PASS trước khi owner ký.  
> *(Các hướng dẫn Ubuntu bên dưới là kế hoạch chưa được thực thi. Toàn bộ các bước cài đặt thực tế gói `.deb`, cắm USB vật lý, desktop notification popup và service reboot trên máy trạm Ubuntu 24.04 đã có staging/kiểm thử tĩnh cho source lịch sử, nhưng chưa có bằng chứng static/packaging cập nhật sau PR #50; kiểm thử thực địa được thực hiện sau Week 4 theo thống nhất của nhóm).*

---

## 1. Yêu cầu hệ thống

* **Hệ điều hành:** Ubuntu 24.04 LTS (x86_64 / amd64).
* **Quyền hạn cài đặt:** Quyền quản trị `sudo` hoặc `root`.
* **Gói phụ thuộc hệ thống (tự động cài đặt qua APT Dependencies):**
  * `openjdk-21-jre-headless` (Java Runtime Environment 21)
  * `usbguard` (Hạt nhân kiểm soát thiết bị USB cấp kernel/daemon)
  * `libnotify-bin` (Tiện ích phát thông báo đồ họa `notify-send` trong phiên user)
  * `acl` (cung cấp `/usr/bin/setfacl`/`getfacl` cho IPC runtime theo UID)
* **Kết nối mạng:** Kết nối HTTP/HTTPS đến Admin Server qua mạng LAN hoặc VPN.

---

## 2. Cấu trúc thư mục & Phân quyền bảo mật (Security Layout)

Gói `.deb` tự động thiết lập mô hình chạy non-root cho tiến trình bảo vệ hệ thống:

| Đường dẫn | Sở hữu | Quyền | Mục đích |
|---|---|---|---|
| `/opt/usbshield-agent/` | `root:usbshield` | `755` | Chứa binary `usbshield-agent.jar` và `usbshield-notifier-launcher.sh` |
| `/etc/usbshield-agent/` | `root:usbshield` | `750` | Thư mục cấu hình (chứa `application.yml` quyền `640` và `rules-v0.1.conf` quyền `644`) |
| `/var/lib/usbshield-agent/` | `usbshield:usbshield` | `700` | Lưu thông tin đăng ký `endpoint.json` và thư mục hàng đợi offline `spool/` |
| `/run/user/<UID>/` | GUI user (logind) | Thường `0700`; ACL cấp `u:usbshield:--x` | Runtime directory của phiên đăng nhập đồ họa, không phải thư mục chung `1777` |
| `/run/user/<UID>/usbshield-notifier/` | GUI user | Tạo `0700`; ACL `u:usbshield:--x` | Namespace riêng của GUI user; được tạo lúc helper khởi động |
| `/run/user/<UID>/usbshield-notifier/notice.sock` | GUI user | Mode ban đầu `0600`; ACL `u:usbshield:rw-` | Unix socket; Agent chỉ được kết nối, không có quyền sửa USBGuard |
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
1. APT xử lý các dependency khai báo trong Debian `control`: `openjdk-21-jre-headless`, `usbguard`, `libnotify-bin`, `acl` (không phải `postinst` tự tải gói).
2. Tạo người dùng hệ thống chuyên biệt `usbshield` (không shell đăng nhập).
3. Thêm user `usbshield` vào nhóm `usbguard` để có quyền giao tiếp IPC.
4. Tạo và phân quyền `/opt/usbshield-agent`, `/etc/usbshield-agent`, `/var/lib/usbshield-agent`. Helper chạy trong GUI session tự tạo `/run/user/<UID>/usbshield-notifier/` và ACL tối thiểu.
5. Gỡ cấu hình `tmpfiles.d` legacy đúng chuỗi xác định trước (nếu có); không tạo thư mục IPC chung `1777` nữa. Nếu thư mục legacy không rỗng, `postinst` không xóa cưỡng bức; hãy kiểm tra trước khi dọn.
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
* Kết quả kết nối/notification phải đối chiếu với helper thực sự đang chạy trong GUI session; **không có log-string cố định được cam kết**. `DELIVERED` chỉ có nghĩa thông điệp đến helper, không xác nhận popup đã hiển thị.

### 5.3. Kiểm tra thông tin Endpoint và Policy đã lưu (Admin / sudo)
```bash
# Chỉ ghi metadata tệp (KHÔNG in endpoint.json: tệp chứa Agent token)
sudo stat -c '%n %U:%G %a %s bytes' /var/lib/usbshield-agent/endpoint.json
# Endpoint ID / policy versions chỉ trích xuất bằng công cụ được M3 duyệt,
# lọc credential trước khi đưa vào evidence công khai.

# Xem hàng đợi ngoại tuyến (khi mất mạng)
sudo ls -la /var/lib/usbshield-agent/spool/
```

### 5.4. Kiểm tra Desktop Notifier trong phiên người dùng (Normal User: UID >= 1000)
Trong phiên đăng nhập đồ họa (GNOME/Wayland/X11) của user thường, lệnh này **không cần `sudo`**:
```bash
# Kiểm tra helper tiến trình thông báo đang chạy
ps aux | grep DesktopNotificationClientMain

# Kiểm tra thư mục session theo UID hiện tại; không dùng sudo cho helper
printf 'XDG_RUNTIME_DIR=%s, UID=%s\n' "$XDG_RUNTIME_DIR" "$(id -u)"
ls -ld "$XDG_RUNTIME_DIR" "$XDG_RUNTIME_DIR/usbshield-notifier"
ls -l "$XDG_RUNTIME_DIR/usbshield-notifier/notice.sock"
getfacl -p -- "$XDG_RUNTIME_DIR" "$XDG_RUNTIME_DIR/usbshield-notifier" "$XDG_RUNTIME_DIR/usbshield-notifier/notice.sock"
# M3/M2 còn phải đo khả năng kết nối bằng user usbshield trên Ubuntu.
```
Chỉ khi chạy thử trên Ubuntu/USB thật mới có thể xác nhận popup hiển thị trong đúng graphical session. `notify-send`/D-Bus, ACL, `SO_PEERCRED` và thời điểm helper autostart hiện **chưa được nghiệm thu native**.

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
* Kịch bản giữ endpoint ID/dữ liệu và tự khởi động lại phải được kiểm chứng bằng thử nghiệm upgrade thật trên Ubuntu; không suy ra chỉ từ script.
* Với cơ chế IPC mới, GUI helper đang chạy từ JAR cũ có thể cần đăng xuất/đăng nhập lại để lấy helper mới; không tự xóa socket đang được dùng.

### 6.2. Khắc phục sự cố & Ngoại lệ (Troubleshooting & Recovery)

1. **Agent không kết nối được Admin Server (Mất mạng / Server Offline):**
   * Agent tự động chuyển sang cơ chế Offline Spool: toàn bộ sự kiện cắm/rút USB vẫn được lưu an toàn tại `/var/lib/usbshield-agent/spool/event_*.json`.
   * Khi mạng khôi phục, `HeartbeatCoordinator` sẽ tự động gửi lại pending ACK và đẩy toàn bộ hàng đợi outbox lên server theo thứ tự FIFO mà không làm mất sự kiện.
2. **File đăng ký `endpoint.json` bị hỏng hoặc mất:**
   * Agent phát hiện file bị lỗi cú pháp, ghi log cảnh báo và tự động kích hoạt quy trình tái đăng ký (`ensureEnrolled`) với server qua bootstrap token.
3. **USBGuard Daemon bị dừng:**
   * Agent khai báo `Requires=usbguard.service` và `Restart=on-failure`, nhưng điều đó **không tự chứng minh** Agent phục hồi khi USBGuard dừng rồi khởi động lại. Hãy đo trạng thái thực tế (`systemctl`, journal, policy/ACK) trong Ubuntu test T24.

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
# Chỉ thực hiện trên máy thử nghiệm disposable, sau khi backup bí mật và policy
sudo apt purge -y usbshield-agent
# Không có lệnh rm -rf tự động ở đây: token/spool/policy được giữ hoặc xóa
# theo quyết định của administrator sau khi kiểm tra artifact và backups.
# Không xóa trực tiếp runtime directory của GUI user hoặc socket đang chạy.
```

---

## 8. Trạng thái kiểm thử & Bàn giao (Test Status & Handoff)

> [!IMPORTANT]
> * **Bằng chứng lịch sử:** M5 S3 đã xác minh JAR/staging và static verifier trên source *trước* PR #50. Owner cho biết hai lệnh Maven `clean test` và `-DskipTests package` trên bản sau PR #50 đều `BUILD SUCCESS` (không có raw log/exit code kèm theo trong repo). **Không được lấy staging cũ làm proof packaging/ACL mới.**
> * **Chưa có group owner sign-off:** Xem `docs/rc/WEEK4_OWNER_SIGNOFF_TEMPLATE.md` và `docs/rc/WEEK4_POST_MERGE_REVIEW_D5E156A.md`.
> * **Hoãn kiểm thử thực địa (Ubuntu Scope):** Thao tác cài đặt trực tiếp gói `.deb` trên hệ điều hành Ubuntu 24.04 thật, thử nghiệm cắm rút USB vật lý, kiểm tra pop-up đồ họa GUI thực tế, và reboot máy trạm được bảo lưu thực hiện **sau Week 4** theo kế hoạch của nhóm.

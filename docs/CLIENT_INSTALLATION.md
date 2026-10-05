# USBShield Client Agent — Installation & Deployment Guide

Tài liệu hướng dẫn cài đặt, cấu hình và vận hành Client Agent cho các máy trạm Ubuntu 24.04 LTS.

---

## 1. Yêu cầu hệ thống

* **Hệ điều hành:** Ubuntu 24.04 LTS (x86_64 / amd64).
* **Quyền hạn cài đặt:** Quyền `sudo` hoặc `root`.
* **Kết nối mạng:** Kết nối được đến Admin Server qua giao thức HTTP/HTTPS.

---

## 2. Hướng dẫn cài đặt bằng gói `.deb`

Gói cài đặt `.deb` đã tự động khai báo các dependencies cần thiết (`openjdk-21-jre-headless`, `usbguard`), do đó hệ điều hành sẽ tự động cài các phần phụ thuộc nếu dùng `apt`.

### Bước 1: Sao chép gói `.deb` vào máy trạm
```bash
scp user@build-server:/path/to/usbshield-agent_1.0.0_amd64.deb .
```

### Bước 2: Cài đặt gói
```bash
sudo apt update
sudo apt install -y ./usbshield-agent_1.0.0_amd64.deb
```

*Quá trình cài đặt sẽ tự động:*
1. Cài đặt OpenJDK 21 và USBGuard nếu máy chưa có.
2. Tạo người dùng hệ thống chuyên biệt `usbshield`.
3. Thiết lập các thư mục `/opt/usbshield-agent`, `/etc/usbshield-agent`, `/var/lib/usbshield-agent`.
4. Đăng ký và khởi chạy dịch vụ `usbshield-agent.service` cùng `usbguard.service`.

---

## 3. Cấu hình kết nối Admin Server

Mặc định cấu hình nằm tại `/etc/usbshield-agent/application.yml`.
Nếu cần trỏ đến máy chủ Admin Server thực tế trong mạng LAN:

```bash
sudo nano /etc/usbshield-agent/application.yml
```

Chỉnh sửa địa chỉ máy chủ:
```yaml
usbshield:
  agent:
    server-url: "http://<IP_ADMIN_SERVER>:8080"
```

Sau khi sửa file cấu hình, khởi động lại dịch vụ:
```bash
sudo systemctl restart usbshield-agent
```

---

## 4. Kiểm tra trạng thái dịch vụ

### Kiểm tra dịch vụ USBGuard:
```bash
sudo systemctl status usbguard
```

### Kiểm tra dịch vụ USBShield Agent:
```bash
sudo systemctl status usbshield-agent
```

### Xem log trực tiếp của Agent:
```bash
sudo journalctl -u usbshield-agent -f
```

---

## 5. Hướng dẫn gỡ cài đặt (Uninstall)

Khi cần gỡ bỏ phần mềm:
```bash
sudo apt remove --purge usbshield-agent
```
Lệnh trên sẽ tự động dừng dịch vụ systemd và dọn dẹp các file nhị phân của chương trình.

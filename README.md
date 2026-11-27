# USBShield

USBShield quản lý USB Mass Storage trên Ubuntu. Central gồm ứng dụng JavaFX trên Windows và API Spring Boot/PostgreSQL. Agent Ubuntu dùng USBGuard/systemd và vé đăng ký theo endpoint. Central phân vùng dữ liệu theo workspace. Revoke trực tiếp và file activity thuộc các slice sau của [Blueprint v2.5](BLUEPRINT.md).

- [Cài đặt và vận hành cho IT](docs/IT_SETUP_GUIDE.md)
- [Sử dụng Central Admin Desktop](docs/ADMIN_GUIDE.md)
- [Kiến trúc và sơ đồ luồng](docs/ARCHITECTURE.md)
- [Đọc mã nguồn và chạy kiểm thử](docs/DEVELOPER_GUIDE.md)
- [Hợp đồng API hiện hành](docs/API.md)

Chuẩn bị JDK 21, Maven và PostgreSQL để build và kiểm thử. Hướng dẫn IT mô tả gói Windows Central, gói Agent `.deb` và cấu hình HTTPS nội bộ.

Repository chính thức là [chivan2925/USBShield](https://github.com/chivan2925/USBShield). Quy trình làm việc với Codex nằm ở [AGENTS.md](AGENTS.md).

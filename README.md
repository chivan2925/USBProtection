# USBShield

USBShield quản lý USB Mass Storage trên Ubuntu. Source hiện tại dùng Admin Web (React/Vite), Admin Server (Spring Boot/PostgreSQL) và Agent Ubuntu (USBGuard/systemd). Central phân vùng dữ liệu theo workspace và Agent dùng vé enrollment theo endpoint. Xem hướng dẫn IT về điều kiện nâng cấp dữ liệu. JavaFX trên Windows, Revoke trực tiếp và file activity thuộc các slice sau của [Blueprint v2.5](BLUEPRINT.md).

- [Cài đặt và vận hành cho IT](docs/IT_SETUP_GUIDE.md)
- [Sử dụng Admin Web hiện tại](docs/ADMIN_GUIDE.md)
- [Kiến trúc và sơ đồ luồng](docs/ARCHITECTURE.md)
- [Đọc mã nguồn và chạy kiểm thử](docs/DEVELOPER_GUIDE.md)
- [Hợp đồng API hiện hành](docs/API.md)

Chuẩn bị JDK 21, Maven, Node/npm cho Web và PostgreSQL. Làm theo hướng dẫn IT để build JAR và .deb rồi thiết lập HTTPS nội bộ. Tài liệu trên dựa vào source baseline; nghiệm thu release cần bằng chứng từ runtime và phần cứng tương ứng.

Repository chính thức là [chivan2925/USBShield](https://github.com/chivan2925/USBShield). Quy trình làm việc với Codex nằm ở [AGENTS.md](AGENTS.md).

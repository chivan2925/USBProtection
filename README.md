# USBShield

USBShield quản lý USB Mass Storage trên Ubuntu. Baseline hiện tại dùng Admin Web (React/Vite), Admin Server (Spring Boot/PostgreSQL) và Agent Ubuntu (USBGuard/systemd). [Blueprint v2.5](BLUEPRINT.md) mô tả mục tiêu JavaFX trên Windows, workspace riêng, Revoke trực tiếp và file activity. Các mục tiêu đó cần thiết kế và triển khai ở các slice riêng.

- [Cài đặt và vận hành cho IT](docs/IT_SETUP_GUIDE.md)
- [Sử dụng Admin Web hiện tại](docs/ADMIN_GUIDE.md)
- [Kiến trúc và sơ đồ luồng](docs/ARCHITECTURE.md)
- [Đọc mã nguồn và chạy kiểm thử](docs/DEVELOPER_GUIDE.md)
- [Hợp đồng API hiện hành](docs/API.md)

Chuẩn bị JDK 21, Maven, Node/npm cho Web và PostgreSQL. Làm theo hướng dẫn IT để build JAR và .deb rồi thiết lập HTTPS nội bộ. Tài liệu trên dựa vào source baseline; nghiệm thu release cần bằng chứng từ runtime và phần cứng tương ứng.

Repository chính thức là [chivan2925/USBShield](https://github.com/chivan2925/USBShield). Quy trình làm việc với Codex nằm ở [AGENTS.md](AGENTS.md).

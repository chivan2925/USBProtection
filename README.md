# USBShield Ubuntu — Hệ thống ngăn chặn và quản lý USB Flash Disk trên Ubuntu

> **Dự án seminar 4 tuần:** Xây dựng ứng dụng ngăn chặn người dùng cắm USB Flash Disk vào máy tính Ubuntu chạy như dịch vụ không thể tắt, quản lý tập trung và định danh phiên người dùng active.  
> **Hệ điều hành máy trạm (Client target):** Ubuntu 24.04 LTS (amd64)  
> **Cơ chế thực thi trên Client:** USBGuard + systemd daemon + loginctl  
> **Nền tảng Admin Server:** Java 21, Spring Boot 3.3.x, Spring Data JPA, H2 File Mode  
> **Nền tảng Admin Web Console:** React + Vite + TypeScript  

---

## 1. Cấu trúc thư mục dự án

```text
USBProtection/
├── admin-server/       # [Member 1] Backend Spring Boot quản trị tập trung & API Ingestion
│   ├── pom.xml
│   └── src/
├── client-agent/       # [Member 3] Ubuntu Client Agent service (Java 21 / systemd)
│   ├── pom.xml
│   └── src/
├── frontend/           # [Member 4] Admin Web Console (React + Vite + TypeScript)
├── packaging/          # [Member 3] Đóng gói cài đặt .deb & cấu hình systemd / rules.conf
├── docs/               # Toàn bộ tài liệu kiến trúc, giao thức và bằng chứng kỹ thuật
│   ├── ARCHITECTURE.md          # Kiến trúc hệ thống tổng thể & nguyên tắc vận hành
│   ├── AGENT_SERVER_PROTOCOL.md # Đặc tả chi tiết giao thức JSON giữa Agent và Server
│   ├── API.md                   # Danh mục RESTful API & Swagger guide
│   ├── agent-contract/          # File JSON mẫu chốt giao thức (sample-usb-event.json)
│   └── usbguard/                # [Member 2] Kết quả PoC, parser và bằng chứng USBGuard
└── README.md           # Hướng dẫn tổng quan dự án
```

---

## 2. Các nguyên tắc cốt lõi của hệ thống

1. **Fail-Closed Principle:** Mặc định chặn (`BLOCK`) toàn bộ USB Mass Storage (Class `08`). Cho phép chuột/bàn phím (Class `03`) hoạt động bình thường. Nếu mất kết nối mạng, máy trạm vẫn duy trì việc chặn tự trị.
2. **Per-Endpoint Whitelist:** Whitelist áp dụng riêng biệt cho từng máy trạm (`endpoint_id + device_identity`). Tuyệt đối không tạo Global Allow-All.
3. **Active User Attribution:** Ghi nhận chính xác tài khoản Ubuntu có session local đang active tại thời điểm cắm USB (`linuxUsername`, `linuxUid`, `sessionId`, `sessionType`, `seat`). Nếu không có user active hoặc không thể xác định rõ ràng, bắt buộc gán nhãn `UNKNOWN`.

---

## 3. Admin Server development and deployment

The supported API contract is frozen at v0.2; see [API](docs/API.md) and the
[Agent–Server protocol](docs/AGENT_SERVER_PROTOCOL.md). Detailed build, test,
configuration and operations instructions are in the [Developer Guide](docs/DEVELOPER_GUIDE.md).

### Local development

Requirements: JDK 21; the Week 4 Windows release build was verified with JDK
22.0.2, Maven 3.9.9, Node.js 22.19.0 and npm 10.9.3. The project targets Java 21;
validate other JDK versions against the pinned Lombok compiler integration.
JDK 24 fails to compile with the pinned Lombok 1.18.36; set `JAVA_HOME` to JDK 21/22
(see the Developer Guide).
The default profile is for local development. It binds to port 8080, uses an
ignored local H2 file under `admin-server/data/`, and does not create an Admin
account or enable enrollment until their secrets are configured.

```powershell
$env:USBSHIELD_ADMIN_PASSWORD = '<local-only password>'
$env:USBSHIELD_ENROLLMENT_TOKEN = '<local-only bootstrap token>'
cd admin-server
mvn spring-boot:run
```

Local API docs are available at `http://localhost:8080/swagger-ui.html` and
`http://localhost:8080/api-docs`. They are disabled in the `prod` profile.

### Production profile

Set `USBSHIELD_DB_URL`, `USBSHIELD_DB_PASSWORD`, `USBSHIELD_ADMIN_PASSWORD`, and
`USBSHIELD_ENROLLMENT_TOKEN` outside the repository, then run the packaged JAR
with `--spring.profiles.active=prod`. Production binds to loopback by default,
uses secure HttpOnly SameSite cookies, and requires HTTPS for Admin browser access.
For remote access, terminate TLS at a trusted reverse proxy and proxy to the
loopback listener. Do not expose the server over plain HTTP or set
`USBSHIELD_COOKIE_SECURE=false` outside isolated local smoke tests.

The H2 console and interactive API docs are disabled in production. Keep the DB
file in a persistent private directory, restrict it to the service account, and
back it up while the server is stopped. Runtime DB, endpoint credentials and event
spool data are local state and must not be committed.

---

## 4. Hướng dẫn khởi chạy Admin Web Console (`frontend`)

```bash
cd frontend
npm install
npm run dev
```
Truy cập giao diện tại: `http://localhost:5173`

---

## 5. Tài liệu kỹ thuật chi tiết

- 📐 **Kiến trúc hệ thống:** Xem chi tiết tại [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- 🔌 **Đặc tả giao thức Agent ↔ Server:** Xem chi tiết tại [docs/AGENT_SERVER_PROTOCOL.md](docs/AGENT_SERVER_PROTOCOL.md)
- 📖 **Danh mục REST API:** Xem chi tiết tại [docs/API.md](docs/API.md)
- 🛡️ **Báo cáo PoC USBGuard & Active-User:** Xem chi tiết tại [docs/usbguard/USBGuard_POC.md](docs/usbguard/USBGuard_POC.md)

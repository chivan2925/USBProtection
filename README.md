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

## 3. Hướng dẫn khởi chạy Admin Server (`admin-server`)

### Yêu cầu môi trường:
- Java JDK 21 trở lên (Khuyến nghị JDK 21 hoặc JDK 22).
- Maven 3.9+ (hoặc dùng `./mvnw`).

### Các bước khởi chạy:
```bash
cd admin-server

# Build và chạy kiểm thử
mvn clean test

# Cấu hình secret ngoài repo trước khi chạy (biến môi trường):
# USBSHIELD_ADMIN_PASSWORD, USBSHIELD_ENROLLMENT_TOKEN
# USBSHIELD_ADMIN_USERNAME optional, mặc định admin
mvn spring-boot:run
```

Sau khi server khởi động thành công:
- **Server URL:** `http://localhost:8080`
- **Swagger UI (Interactive API Docs):** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **H2 Console:** tắt và bị chặn qua HTTP ở Week 2. File DB được giữ nguyên.
- **Week 2 API/auth/CSRF:** [API v0.2](docs/API.md); [báo cáo M1](docs/member1/WEEK2_SERVER_INTEGRATION.md).
- Frontend dev cần proxy `/api` cùng origin (M4); không mở CORS wildcard.

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

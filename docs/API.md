# USBShield Ubuntu — RESTful API Reference (v0.1)

> **Dự án:** USBShield Ubuntu — Hệ thống ngăn chặn và quản lý USB Flash Disk trên Ubuntu  
> **Tài liệu:** Danh mục và đặc tả REST API (OpenAPI Draft)  
> **Phiên bản:** v0.1  
> **Interactive Swagger UI:** `http://localhost:8080/swagger-ui.html`  
> **OpenAPI JSON Spec:** `http://localhost:8080/api-docs`  

---

## 1. Nhóm API dành cho Client Agent (`/api/agent/**`)

| Phương thức | Đường dẫn | Mục đích | Yêu cầu Auth |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/agent/enroll` | Đăng ký máy Endpoint mới vào hệ thống | Enrollment Token |
| `POST` | `/api/agent/heartbeat` | Gửi nhịp tim định kỳ, cập nhật trạng thái Online | Header `X-Endpoint-Id` |
| `GET` | `/api/agent/policy` | Lấy danh sách Whitelist cho riêng Endpoint | Header `X-Endpoint-Id` |
| `POST` | `/api/agent/policy/ack` | Báo cáo đã nạp thành công phiên bản policy | Header `X-Endpoint-Id` |
| `POST` | `/api/agent/events` | Đẩy sự kiện phát hiện/chặn USB kèm Active User | Header `X-Endpoint-Id` |

---

## 2. Nhóm API Quản trị & Xác thực (`/api/auth/**`)

| Phương thức | Đường dẫn | Mục đích | Payload |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/login` | Quản trị viên đăng nhập vào Admin Web | `{"username": "admin", "password": "..."}` |
| `POST` | `/api/auth/logout` | Đăng xuất phiên làm việc | Trống |
| `GET` | `/api/auth/me` | Lấy thông tin tài khoản đang đăng nhập | Trống |

---

## 3. Nhóm API Quản trị Endpoint (`/api/admin/endpoints/**`)

| Phương thức | Đường dẫn | Mục đích |
| :--- | :--- | :--- |
| `GET` | `/api/admin/endpoints` | Liệt kê danh sách toàn bộ Endpoint đang quản lý (kèm trạng thái Online/Offline, IP, lastSeen). |
| `GET` | `/api/admin/endpoints/{id}` | Xem chi tiết thông tin và chính sách hiện tại của một Endpoint. |
| `DELETE` | `/api/admin/endpoints/{id}` | Xóa hoặc hủy đăng ký một Endpoint khỏi hệ thống. |

---

## 4. Nhóm API Quản trị Whitelist theo Endpoint (`/api/admin/policies/**`)

| Phương thức | Đường dẫn | Mục đích | Payload ví dụ |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/endpoints/{endpointId}/whitelist` | Lấy danh sách thiết bị được phép của riêng endpoint này. | Không |
| `POST` | `/api/admin/endpoints/{endpointId}/whitelist` | Cấp phép một thiết bị USB cho endpoint này (tăng `policyVersion`). | `{"fingerprintValue": "0951:1666", "fingerprintType": "VID_PID"}` |
| `DELETE` | `/api/admin/endpoints/{endpointId}/whitelist/{id}` | Thu hồi quyền sử dụng USB khỏi endpoint này (tăng `policyVersion`). | Không |

---

## 5. Nhóm API Lịch sử Sự kiện USB (`/api/admin/events/**`)

| Phương thức | Đường dẫn | Mục đích | Query params |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/events` | Lấy danh sách nhật ký sự kiện cắm/rút USB trên toàn bộ hệ thống. | `page`, `size`, `endpointId`, `decision` |
| `GET` | `/api/admin/events/{id}` | Xem chi tiết một sự kiện (thông tin USB, session, user UID, thời điểm). | Không |

---

## 6. Hướng dẫn kiểm thử nhanh qua cURL

### 6.1. Gửi Heartbeat thử nghiệm:
```bash
curl -X POST http://localhost:8080/api/agent/heartbeat \
  -H "Content-Type: application/json" \
  -d '{
    "endpointId": "POC-LAB-01",
    "hostname": "ubuntu-lab-pc",
    "agentVersion": "1.0.0-SNAPSHOT",
    "currentPolicyVersion": 1
  }'
```

### 6.2. Gửi sự kiện USB kèm Active User:
```bash
curl -X POST http://localhost:8080/api/agent/events \
  -H "Content-Type: application/json" \
  -d '{
    "endpointId": "POC-LAB-01",
    "hostname": "ubuntu-lab-pc",
    "linuxUsername": "usbdev",
    "linuxUid": 1000,
    "sessionId": "2",
    "sessionType": "wayland",
    "seat": "seat0",
    "device": {
      "vendorId": "093a",
      "productId": "2510",
      "serial": "",
      "name": "USB Optical Mouse",
      "hash": "L46bZEAKg59EA+puzjHMh8D4jLliJqK4sYxKSd/DRz8=",
      "interface": "03:01:02"
    },
    "eventType": "CONNECTED",
    "decision": "BLOCKED",
    "occurredAt": "2026-09-28T14:10:16+07:00"
  }'
```

### 6.3. Kéo chính sách Whitelist của Endpoint:
```bash
curl -X GET http://localhost:8080/api/agent/policy \
  -H "X-Endpoint-Id: POC-LAB-01"
```

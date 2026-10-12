# USBShield Ubuntu — Agent to Server Protocol Specification (v0.1)

> **Dự án:** USBShield Ubuntu — Hệ thống ngăn chặn và quản lý USB Flash Disk trên Ubuntu  
> **Tài liệu:** Đặc tả giao thức truyền thông Agent ↔ Admin Server (Communication Protocol)  
> **Phiên bản:** v0.1 (Contract Freeze Week 1)  
> **Owner:** Member 1 (Admin Server Lead & Integration Owner)  
> **Đồng thuận:** Member 2 (Linux/USBGuard Core) & Member 3 (Client Agent Lead)  

---

## 1. Nguyên tắc chung & Tiêu chuẩn kỹ thuật

- **Giao thức mạng:** HTTP / REST (chuẩn bị HTTPS cho sản phẩm hoàn chỉnh).
- **Định dạng dữ liệu:** JSON (`Content-Type: application/json; charset=UTF-8`).
- **Định dạng thời gian:** Chuẩn ISO-8601 UTC hoặc có offset (VD: `2026-09-28T14:10:16+07:00` hoặc `2026-09-28T07:10:16Z`).
- **Mã định danh Endpoint:** Mỗi máy trạm sở hữu một mã định danh duy nhất `endpointId` (sinh tự động khi enroll hoặc gán từ cấu hình/PoC).
- **HTTP Headers tiêu chuẩn từ Agent:**
  - `X-Endpoint-Id`: Định danh endpoint gửi request.
  - `X-Agent-Token`: Token xác thực (chuẩn bị cho Week 2).

---

## 2. Đặc tả Endpoint Enrollment (`POST /api/agent/enroll`)

Thực hiện một lần khi Client Agent mới được cài đặt để đăng ký máy vào hệ sinh thái quản lý.

### Request Body
```json
{
  "hostname": "usbshield-lab-01",
  "machineId": "a1b2c3d4e5f6...",
  "ipAddress": "192.168.1.50",
  "enrollmentToken": "pre-shared-lab-secret"
}
```

### Response Body (`200 OK`)
```json
{
  "endpointId": "EP-A1B2C3D4",
  "agentToken": "token-9f8e7d6c-5b4a...",
  "currentPolicyVersion": 1
}
```

---

## 3. Đặc tả Heartbeat (`POST /api/agent/heartbeat`)

Client Agent gửi định kỳ mỗi 15-30 giây để cập nhật trạng thái `ONLINE` và kiểm tra sự thay đổi phiên bản chính sách.

### Request Body
```json
{
  "endpointId": "EP-A1B2C3D4",
  "hostname": "usbshield-lab-01",
  "agentVersion": "1.0.0-SNAPSHOT",
  "currentPolicyVersion": 1
}
```

### Response Body (`200 OK`)
```json
{
  "accepted": true,
  "serverTime": "2026-10-07T18:00:00Z",
  "desiredPolicyVersion": 2,
  "serverPolicyVersion": 2,
  "lastAppliedPolicyVersion": 1,
  "status": "OK"
}
```
*Ghi chú:* Nếu `desiredPolicyVersion > currentPolicyVersion`, Client Agent sẽ lập tức gọi API kéo chính sách mới.

---

## 4. Đặc tả Đồng bộ Whitelist (`GET /api/agent/policy`)

Trả về danh sách thiết bị USB được phép hoạt động **dành riêng cho chính Endpoint này** (Per-endpoint Whitelist).

### Request Headers
- `X-Endpoint-Id: EP-A1B2C3D4`
- `X-Agent-Token: token-9f8e7d6c-5b4a...`

### Response Body (`200 OK`)
```json
{
  "policyVersion": 2,
  "allowedDevices": [
    {
      "vendorId": "0951",
      "productId": "1666",
      "serialNumber": "001A9207...",
      "deviceHash": "k7XyZ...",
      "deviceName": "Kingston DataTraveler 3.0",
      "fingerprintValue": "0951:1666",
      "fingerprintType": "VID_PID"
    }
  ]
}
```

### Xác nhận áp dụng chính sách (`POST /api/agent/policy/ack`)
Sau khi cập nhật file `/etc/usbguard/rules.conf` thành công, Agent gửi xác nhận:
```json
{
  "appliedPolicyVersion": 2,
  "appliedAt": "2026-10-07T18:00:05Z",
  "ruleCount": 3
}
```

---

## 5. Đặc tả Thu thập sự kiện USB (`POST /api/agent/events`)

Được kích hoạt ngay khi USBGuard phát hiện sự kiện cắm (`CONNECTED`) hoặc rút (`DISCONNECTED`) thiết bị USB.

### JSON Payload Chuẩn (Đã Freeze với Member 2 & Member 3)
```json
{
  "endpointId": "POC-UNENROLLED-LAB-PC-01",
  "hostname": "usbshield-lab",
  "linuxUsername": "usbdev",
  "linuxUid": 1000,
  "sessionId": "21",
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
}
```

### Ý nghĩa các trường dữ liệu Active User Attribution:
- `linuxUsername`: Tên tài khoản Ubuntu đăng nhập có phiên tương tác phần cứng tại thời điểm cắm USB.
- `linuxUid`: User ID tương ứng theo hệ điều hành Ubuntu.
- `sessionId`: Session ID lấy từ `loginctl list-sessions` (systemd-logind).
- `sessionType`: Loại phiên đồ họa (`wayland`, `x11`, hoặc `tty`).
- `seat`: Vị trí phần cứng (`seat0`).
- **Quy tắc Fallback:** Nếu không có phiên local nào active hoặc hệ thống không thể xác định duy nhất:
  - `linuxUsername`: `"UNKNOWN"` (bắt buộc)
  - `linuxUid`: `null`
  - `sessionId`: `null`

### Response Body (`200 OK`)
```json
{
  "success": true,
  "eventId": 142,
  "receivedAt": "2026-09-28T14:10:17Z"
}
```

---

## 6. Mã phản hồi HTTP & Xử lý lỗi (Error Codes)

| HTTP Code | Ý nghĩa | Hành động của Agent |
| :--- | :--- | :--- |
| `200 OK` | Yêu cầu xử lý thành công. | Tiếp tục chu kỳ bình thường. |
| `400 Bad Request` | Sai cú pháp JSON hoặc thiếu trường bắt buộc. | Ghi log lỗi, không retry payload lỗi. |
| `401 Unauthorized` | Sai hoặc thiếu Agent Token. | Yêu cầu đăng ký lại enrollment. |
| `404 Not Found` | Endpoint không tồn tại trong DB. | Chuyển sang trạng thái un-enrolled. |
| `500 / 503` | Lỗi nội bộ server hoặc server offline. | Lưu trữ sự kiện vào Outbox Spool cục bộ để retry sau. |

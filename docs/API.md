# Hợp đồng API baseline

Các route dưới đi qua HTTPS và ACL khi deploy. Route Admin cần session và CSRF cho mutation. Central lấy workspace từ session đã xác thực. Detail hoặc mutation trên tài nguyên ngoài workspace trả `404`. Mọi route Agent cần `X-USBShield-Protocol: 2`, nếu thiếu hoặc sai phiên bản thì trả `426`. Sau enrollment, Agent gửi thêm `X-Endpoint-Id` và `X-Agent-Token`. Token chỉ hợp lệ trên endpoint và generation đã cấp.

| Nhóm | Entry point |
| --- | --- |
| Đăng nhập | `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`. [AdminAuthController](../admin-server/src/main/java/com/group/usbshield/server/auth/AdminAuthController.java) |
| Cấp vé Agent | `POST /api/admin/enrollment/slots` nhận `installationId` (UUID do IT xác minh) và `hostname`, trả `endpointId`. `POST /api/admin/enrollment/{endpointId}/ticket` trả `enrollmentToken` và `expiresAt`. `DELETE /api/admin/enrollment/{endpointId}/credential` thu hồi credential và vé của endpoint. [AdminEnrollmentController](../admin-server/src/main/java/com/group/usbshield/server/agentapi/AdminEnrollmentController.java) |
| Audit Admin | `GET /api/admin/audit?page=0&size=20` trả thao tác cấp tài khoản, reset mật khẩu, cấp slot/vé và thu hồi credential trong workspace, kèm `actorUsername`. [AdminAuditController](../admin-server/src/main/java/com/group/usbshield/server/auth/AdminAuditController.java) |
| Quản lý whitelist | `GET/POST /api/admin/endpoints/{endpointId}/whitelist`, `DELETE /api/admin/endpoints/{endpointId}/whitelist/{entryId}`. POST nhận `usbDeviceId` đã quan sát ở endpoint. [AdminEndpointWhitelistController](../admin-server/src/main/java/com/group/usbshield/server/policy/AdminEndpointWhitelistController.java) |
| Đọc sự kiện | `GET /api/admin/events` phân trang và `GET /api/admin/events/{id}`. [AdminEventController](../admin-server/src/main/java/com/group/usbshield/server/event/AdminEventController.java) |
| Enrollment/heartbeat/event | `POST /api/agent/enroll`, `POST /api/agent/heartbeat`, `POST /api/agent/events`. [Agent API package](../admin-server/src/main/java/com/group/usbshield/server/agentapi) |
| Đồng bộ policy | `GET /api/agent/policy` trả `policyVersion` và `whitelistedDevices`. `POST /api/agent/policy/ack` nhận `appliedPolicyVersion` và kiểm tra khoảng revision. [AgentPolicyController](../admin-server/src/main/java/com/group/usbshield/server/policy/AgentPolicyController.java) |

`POST /api/agent/enroll` nhận `installationId`, `requestId`, `hostname`, `machineId`, `enrollmentToken` và candidate `agentToken` (32 byte ngẫu nhiên, biểu diễn bằng 64 ký tự hex thường). `ipAddress` và `operatingSystem` là tùy chọn. Agent lưu candidate trước request. Vé dùng cho đúng slot, hết hạn sau 15 phút. Sau khi claim thành công, retry đúng vé, request và proof trả cùng credential trong 24 giờ nếu generation còn hoạt động. Central trả `currentPolicyVersion: 0` để Agent lấy snapshot sau enrollment. Giữ vé, token và header credential khỏi log.

ID số của catalog, sự kiện, whitelist và audit là ID ngẫu nhiên ổn định trong phạm vi JSON safe integer. Client dùng ID làm khóa tra cứu, còn API quyết định thứ tự kết quả theo thời gian và thứ tự ghi sự kiện. Sau khi thu hồi credential, desired revision vẫn được giữ và applied revision về `0` cho đến khi nhận ACK mới.

Giữ đồng bộ Java DTO phía Agent và Server khi sửa wire contract, rồi cập nhật consumer/mapping frontend và test tương ứng. [Fixture sự kiện](agent-contract/sample-usb-event.json) cung cấp payload synthetic được test đọc trực tiếp. Với local dev, Springdoc khai báo `/api-docs` và `/swagger-ui.html`; profile prod tắt cả hai. Thiết kế API v2.5 còn mở theo [blueprint](../BLUEPRINT.md).

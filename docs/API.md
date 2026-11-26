# Hợp đồng API baseline

Các route dưới đi qua HTTPS và ACL khi deploy. Route Admin cần session và CSRF cho mutation. Agent enrollment dùng secret triển khai và các request Agent tiếp theo gửi `X-Endpoint-Id` và `X-Agent-Token`. Spring xác thực token theo endpoint và controller lấy principal cho các thao tác Agent.

| Nhóm | Entry point |
| --- | --- |
| Đăng nhập | `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`. [AdminAuthController](../admin-server/src/main/java/com/group/usbshield/server/auth/AdminAuthController.java) |
| Quản lý whitelist | `GET/POST /api/admin/endpoints/{endpointId}/whitelist`, `DELETE /api/admin/endpoints/{endpointId}/whitelist/{entryId}`. POST nhận `usbDeviceId` đã quan sát ở endpoint. [AdminEndpointWhitelistController](../admin-server/src/main/java/com/group/usbshield/server/policy/AdminEndpointWhitelistController.java) |
| Đọc sự kiện | `GET /api/admin/events` phân trang và `GET /api/admin/events/{id}`. [AdminEventController](../admin-server/src/main/java/com/group/usbshield/server/event/AdminEventController.java) |
| Enrollment/heartbeat/event | `POST /api/agent/enroll`, `POST /api/agent/heartbeat`, `POST /api/agent/events`. [Agent API package](../admin-server/src/main/java/com/group/usbshield/server/agentapi) |
| Đồng bộ policy | `GET /api/agent/policy` trả `policyVersion` và `whitelistedDevices`. `POST /api/agent/policy/ack` nhận `appliedPolicyVersion` và kiểm tra khoảng revision. [AgentPolicyController](../admin-server/src/main/java/com/group/usbshield/server/policy/AgentPolicyController.java) |

Giữ đồng bộ Java DTO phía Agent và Server khi sửa wire contract, rồi cập nhật consumer/mapping frontend và test tương ứng. [Fixture sự kiện](agent-contract/sample-usb-event.json) cung cấp payload synthetic được test đọc trực tiếp. Với local dev, Springdoc khai báo `/api-docs` và `/swagger-ui.html`; profile prod tắt cả hai. Thiết kế API v2.5 còn mở theo [blueprint](../BLUEPRINT.md).

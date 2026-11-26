# Đọc và phát triển mã nguồn baseline

## Bắt đầu từ entry point

| Module | Nơi đọc trước | Trách nhiệm |
| --- | --- | --- |
| `admin-server` | [UsbShieldServerApplication](../admin-server/src/main/java/com/group/usbshield/server/UsbShieldServerApplication.java), [SecurityConfig](../admin-server/src/main/java/com/group/usbshield/server/config/SecurityConfig.java), [Flyway](../admin-server/src/main/resources/db/migration) | Composition root, HTTP/session/Agent auth và dữ liệu trung tâm. Package `auth`, `agentapi`, `policy`, `endpoint`, `device`, `event` nhóm theo use case hoặc boundary. |
| `client-agent` | [ClientAgentApplication](../client-agent/src/main/java/com/group/usbshield/agent/ClientAgentApplication.java), [AgentProperties](../client-agent/src/main/java/com/group/usbshield/agent/config/AgentProperties.java) | Process Agent non-web. `endpoint` quản lý registration/version, `sync` điều phối HTTP và retry, `usbguard` sở hữu CLI/parser/rules. |
| Notifier trong `client-agent` | [DesktopNotificationClientMain](../client-agent/src/main/java/com/group/usbshield/agent/notification/DesktopNotificationClientMain.java), [desktop launcher](../packaging/desktop/usbshield-notifier-launcher.sh) | Entrypoint riêng của desktop user. `notification` sở hữu IPC/notify-send. `session` resolve phiên để attribution và truyền đúng đích. |
| `frontend` | [main.tsx](../frontend/src/main.tsx), [App.tsx](../frontend/src/App.tsx), [services/api.ts](../frontend/src/services/api.ts) | React router/layout, auth, API client, mapping DTO và các trang. |
| `packaging` | [Agent service](../packaging/systemd/usbshield-agent.service), [postinst](../packaging/agent-deb/DEBIAN/postinst), [Nginx mẫu](../packaging/nginx/usbshield-https-acl.conf.example) | Tài khoản/quyền service, daemon ACL, notifier autostart, TLS và IP ACL. |

## Truy vết một thay đổi

**Policy.** Bắt đầu ở component [EndpointWhitelistPanel](../frontend/src/components/endpoint/EndpointWhitelistPanel.tsx), đi qua HTTP client tới [AdminEndpointWhitelistController](../admin-server/src/main/java/com/group/usbshield/server/policy/AdminEndpointWhitelistController.java) và [EndpointPolicyService](../admin-server/src/main/java/com/group/usbshield/server/policy/EndpointPolicyService.java). Service khóa endpoint trong transaction và quản lý desired revision. Bên Agent, [HeartbeatCoordinator](../client-agent/src/main/java/com/group/usbshield/agent/sync/HeartbeatCoordinator.java) gọi [PolicySyncService](../client-agent/src/main/java/com/group/usbshield/agent/sync/PolicySyncService.java) để validate snapshot trước khi đụng USBGuard. [UsbGuardPolicySynchronizer](../client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardPolicySynchronizer.java) sở hữu reconcile, kiểm tra daemon rules và rollback theo khả năng adapter. Giữ thay đổi HTTP/auth trong adapter tương ứng và đặt invariant version ở service. Khi mở rộng, test duplicate, invalid snapshot, reconcile/persist failure và ACK retry.

**USB event.** Theo [UsbGuardEventListener](../client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardEventListener.java) tìm watch/recovery, active session và quyết định. [EventSenderService](../client-agent/src/main/java/com/group/usbshield/agent/event/EventSenderService.java) đóng payload vào [OutboxSpoolService](../client-agent/src/main/java/com/group/usbshield/agent/outbox/OutboxSpoolService.java). [AgentEventController](../admin-server/src/main/java/com/group/usbshield/server/agentapi/AgentEventController.java) xác thực association và xử lý idempotency. Nếu thêm trường sự kiện đã được duyệt, cập nhật payload/DTO ở cả hai phía, migration nếu cần và API read/mapping. Kiểm tra giữ nguyên `eventId` khi retry và semantics `UNKNOWN` khi thiếu session.

## Build và test từ repo root

- Agent unit: `mvn -f client-agent/pom.xml test`. Gateway/notification test double hữu ích cho regression. Kiểm tra USBGuard thật và Unix IPC cần Ubuntu.
- Backend fixture unit: `mvn -f admin-server/pom.xml "-Dtest=AgentEventContractTest" test`.
- Backend integration: chuẩn bị database `usbshield_test` và role test `usbshield_test` có quyền tạo schema. Tạo `.env` local từ [.env.example](../.env.example). Trong một phiên PowerShell dành riêng cho test, chạy `& ./scripts/with-env.ps1 -RequireTestDb -Command { mvn -f admin-server/pom.xml test }`. Helper đặt env process và kết thúc phiên với exit code của test.
- Frontend fixture unit: `node --experimental-strip-types --test frontend/tests/m5_event_api.test.mjs`. Chạy `npm --prefix frontend ci` trước các build/test cần dependencies. Browser live cần packaged Server, browser và DB test đã cấu hình theo [Windows verifier](../packaging/tests/verify-windows.ps1).
- Binaries: [IT setup](IT_SETUP_GUIDE.md) mô tả JAR, .deb, cấu hình và chẩn đoán. Không dùng DB vận hành cho test và không dùng thiết bị chứa dữ liệu thật để nghiệm thu thao tác có thể gián đoạn I/O.

Giữ dependency direction theo use case. Tách thêm port/module khi nó cô lập một boundary thực tế. Mọi thay đổi hành vi cập nhật [API](API.md), [kiến trúc](ARCHITECTURE.md) và guide liên quan trong cùng slice. [Blueprint](../BLUEPRINT.md) mô tả hướng refactor và các gate sau.

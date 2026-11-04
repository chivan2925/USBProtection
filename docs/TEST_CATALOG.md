# USBShield — Functional Test Catalog (03/11/2026)

> **Updated 04/11/2026 (`vinhkhang` `db125d28861acdb6d07aaed96c53cddfcba07408`):** Admin Server migrated from H2 to PostgreSQL, with new PostgreSQL persistence and isolated test-database coverage. Original refactor snapshot 03/11 counted 303 `@Test` before/after **at that historical commit**; do not treat that count as current execution results. Test verification after database migration must target PostgreSQL.

## Nguyên tắc

- Test file đặt tên theo **component, behavior hoặc API contract**, không dùng `Week`, `Copilot` hay số PR.
- **Gộp** nếu cùng class/logic được test và fixture tương thích; giữ nguyên từng assertion và case.
- **Giữ riêng** những integration suite có Spring context/DB/setup khác, hoặc boundary khác (USBGuard live watch vs policy reconciler, filesystem outbox vs HTTP dispatch).
- Các tài liệu trong `docs/qa/` và `docs/*/week*-evidence/` là **hồ sơ lịch sử**, có thể dùng tên test cũ để truy vết; không sửa thành tên mới như thể từng chạy ở thời điểm đó.

## Admin Server

- `PostgreSqlPersistenceIntegrationTest`: CRUD/persistence integration against PostgreSQL; legacy H2 compatibility and upgrade test classes were removed because the test data do not need migration.
- `IsolatedPostgresDb`: shared test configuration for the dedicated `usbshield_test` database; this is a helper, not an executable test class.
- `ServerApiIntegrationTest`: các luồng Admin/API/endpoints/events/policy end-to-end qua Spring context; giữ một test suite hợp đồng tích hợp, không chia khiến context setup trùng.
- `ProductionProfileSecurityTest`: profile prod, cookie, OpenAPI security (không còn H2 console runtime).
- `ServerSecurityAndStatusTest`: request auth/security + policy status và SPA route.
- `EndpointMetadataIntegrationTest`: metadata từ Agent → Admin API/whitelist.
- Các test theo chức năng đã có sẵn giữ nguyên: `EventSearchIntegrationTest`, `AgentHttpIntegrationTest`, `AgentEventContractTest`, `ConcurrentUsbCatalogIntegrationTest`, `EnrollmentRetryContractTest`, v.v. Các test nâng cấp schema H2 (`UsbCatalogUpgrade*`) đã bị xóa cùng H2 preflight.

## Client Agent — các file được gộp

- `SystemInfoProviderRegressionTest` + `SystemInfoProviderRoutingTest` → **`SystemInfoProviderTest`**: IP local và OS metadata.
- `BlockNotificationPolicyTest` + `Week3NotificationPolicyTest` + **phần policy** của `Week4NotificationCorrectnessTest` → **`BlockNotificationPolicyTest`**: phân loại BLOCK, dedup/cooldown, remove/reinsert, active GUI user, bound.
- **Phần publisher/service** của `Week4NotificationCorrectnessTest` → `DesktopNotificationServiceTest`: notifier unavailable/disabled không ảnh hưởng enforcement.
- `Week3ActiveSessionTransitionTest` + `Week4ActiveSessionSafetyTest` → **`ActiveSessionSelectionTest`**: login/logout, user switching, ambiguous/remote-only/TTY và invalid session.

## Client Agent — các test đổi tên nhưng vẫn giữ fixture riêng

- `UsbGuardDurableAuditRetryTest`, `UsbGuardPendingPolicyLifecycleTest`, `UsbGuardWatchLossPolicyRecoveryTest`, `UsbGuardIpcRecoverySynchronizationTest`: các ranh giới khác nhau của pending audits, watch loss và IPC recovery.
- `UsbGuardAuditLifecycleTest`, `UsbGuardEffectiveAllowTransitionTest`, `UsbGuardFinalDecisionIntegrationTest`: audit lifecycle và chuyển quyết định cuối.
- `UsbGuardPolicySafetyValidationTest`, `UsbGuardFingerprintValidationTest`, `UsbGuardPolicyReconciliationTest`, `UsbGuardRuleMutationSafetyTest`, `UsbGuardManagedPolicyIntegrityTest`: USBGuard policy, identity/rule ordering/rollback.
- `UsbGuardWatchRecoveryTest`, `UsbGuardWatchResilienceTest`, `UsbGuardNotificationDecisionTargetTest`: watch replay/bounds/final target và notification.
- `NotifierIpcSecurityTest`: gộp kiểm tra peer authorization + hợp đồng IPC/Unix socket.

## PostgreSQL test isolation (sau migration)

- Maven Admin tests dùng PostgreSQL `usbshield_test` với `USBSHIELD_TEST_DB_PASSWORD`; Hibernate có thể tạo/xóa bảng tại đây. **Không bao giờ trỏ Maven integration suite vào `usbshield` của ứng dụng.**
- `npm run test:live` và `npm run test:browser:live` dùng role `usbshield_test` trên database `usbshield_browser_test`, schema riêng theo lần chạy; browser tests không cấp quyền cho role test trong `usbshield` ứng dụng. Các schema `usbshield_e2e_*` cũ cần được dọn có kiểm soát.
- Đây là test integration trên Windows với event HTTP tổng hợp; không chứng minh USBGuard trên Ubuntu. Ghi PASS sau khi chạy thực tế, không suy từ code/harness.

## Xác nhận sau refactor

Từ repository root:

```powershell
mvn -f admin-server/pom.xml clean test
mvn -f client-agent/pom.xml clean test

# Phát hiện class test legacy hoặc tên test không còn đúng:
Get-ChildItem admin-server/src/test,client-agent/src/test -Recurse -Filter *.java | Where-Object { $_.Name -match '(Week\d|Copilot|PR\d+)' }

git diff --check
```

**Không chạy `git rm` cũ trước khi đã giải nén các file mới**, vì khi đó các bộ test sẽ tạm thời thiếu. Không coi Maven PASS từ trước refactor là PASS cho source mới. Ghi nhận source HEAD và số lượng test thực chạy từ Surefire để đối chiếu; `303` ở trên là **số annotation**, không phải số case thực thi đã xác minh.

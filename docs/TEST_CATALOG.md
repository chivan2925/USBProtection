# USBShield — Functional Test Catalog (03/11/2026)

> Source baseline: `main` `c41f2c73c8f80cc2c2d9ea945094daa47435a93f`. **Tổ chức lại test, không thay đổi logic sản phẩm.** Tổng `@Test` trước/sau trên source: **303/303**. Đây là kiểm tra tĩnh; full Maven phải chạy lại trên Windows sau khi áp dụng.

## Nguyên tắc

- Test file đặt tên theo **component, behavior hoặc API contract**, không dùng `Week`, `Copilot` hay số PR.
- **Gộp** nếu cùng class/logic được test và fixture tương thích; giữ nguyên từng assertion và case.
- **Giữ riêng** những integration suite có Spring context/DB/setup khác, hoặc boundary khác (USBGuard live watch vs policy reconciler, filesystem outbox vs HTTP dispatch).
- Các tài liệu trong `docs/qa/` và `docs/*/week*-evidence/` là **hồ sơ lịch sử**, có thể dùng tên test cũ để truy vết; không sửa thành tên mới như thể từng chạy ở thời điểm đó.

## Admin Server

- `LegacyH2SchemaCompatibilityTest`: tương thích database H2 cũ.
- `ServerApiIntegrationTest`: các luồng Admin/API/endpoints/events/policy end-to-end qua Spring context; giữ một test suite hợp đồng tích hợp, không chia khiến context setup trùng.
- `ProductionProfileSecurityTest`: profile prod, cookie, H2 console, OpenAPI.
- `ServerSecurityAndStatusTest`: request auth/security + policy status và SPA route.
- `EndpointMetadataIntegrationTest`: metadata từ Agent → Admin API/whitelist.
- Các test theo chức năng đã có sẵn giữ nguyên: `EventSearchIntegrationTest`, `AgentHttpIntegrationTest`, `AgentEventContractTest`, `UsbCatalogUpgradeTest`, `UsbCatalogUpgradeStartupIntegrationTest`, `EnrollmentRetryContractTest`, v.v.

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

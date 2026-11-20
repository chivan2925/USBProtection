# USBShield — Functional Test Catalog (current entrypoints + historical refactor)

> **Source/tree inspected:** `main` `721f164f63533461b6c18b62e1607baa4ab1b731` (20/11/2026). This is an **inventory of tests and scripts**, not a fresh test result. Test counts and renames from 03–09/11 are historical snapshots; they do not prove this HEAD passed. The older refactor snapshot counted **303 `@Test` annotations** at its own commit, not current executed cases. See [Admin Server Release](ADMIN_SERVER_RELEASE.md) and [Run Guide](RUN_GUIDE.md).

## 1. Canonical Windows verification (currently present)

From the repository root, in **Windows PowerShell 7.6+** with PostgreSQL test databases and untracked `.env` test credentials configured:

```powershell
# Admin Maven + frontend unit/lint/build + packaged JAR + browser/HTTP-live
.\packaging\tests\verify-windows.ps1 -AdminOnly

# Above plus Client Agent Maven tests and Windows-built .deb checks
.\packaging\tests\verify-windows.ps1
```

`verify-windows.ps1` parses the current release/verifier script syntax, validates dedicated test DB role, executes the selected test gates, rebuilds the JAR **after** Maven `clean test`, checks packaged React/Flyway and launches real Spring/PostgreSQL-backed browser/HTTP-live suites. Full mode additionally builds/checks the Agent `.deb`. `-SkipTests`, `-SkipBrowserLive`, `-SkipDebBuild` reduce coverage; record skips explicitly and do **not** claim full PASS.

- `admin-server/verify-week1.ps1`, `verify-week2.ps1`, `verify-week3.ps1` **do not exist in this HEAD**. Their previous purposes were Week 1–3 historical milestone validation, **not** a mandatory runtime check. Do not reintroduce `ddl-auto=create` or destructive test-DB resets into the Windows gate.
- `admin-server/build-release.ps1` **builds and verifies a distributable JAR** with optional Maven tests and SHA-256/manifest; it is a release builder, not the comprehensive Windows test entrypoint.
- `scripts/with-env.ps1` loads `.env` for individual test commands or `.env.e2e.local` for the owner's runtime E2E setup. Spring Boot itself does not read those files automatically.

## 2. Database isolation and non-destructive boundary

- `.env` supplies **test-only** PostgreSQL connection settings; use role `usbshield_test`, database `usbshield_test` for Admin Maven integration and `usbshield_browser_test` for browser/HTTP-live. Never use `usbshield` application DB for testing, dropping schemas or resetting tables.
- Admin tests use class-scoped schema names such as `usbshield_it_*`; browser harnesses use disposable schemas `usbshield_e2e_*`. Schema cleanup is expected under normal completion, but a hard kill can leave an orphan requiring ownership/target verification before manual removal.
- Flyway migrates test schemas, Hibernate uses `validate`; do not add `ddl-auto=create`/`create-drop`, and do not run one-time application Flyway baseline on test DBs.
- HTTP browser test data are synthetic; they are **not** actual USBGuard enforcement results. Successful Windows tests cannot substitute for separate HTTPS/ACL/USB native acceptance.

## 3. Admin Server test inventory

The following names were found under `admin-server/src/test` at the inspected HEAD:

| Test / helper | Coverage boundary |
|---|---|
| `PostgreSqlPersistenceIntegrationTest` | PostgreSQL CRUD/persistence |
| `IsolatedPostgresDb` | Shared isolated schema helper; **not itself a test class** |
| `ServerApiIntegrationTest` | API integration across Admin and Agent contracts |
| `ProductionProfileSecurityTest` | `prod` security/cookie/OpenAPI assertions |
| `ServerSecurityAndStatusTest` | Auth/security/status and SPA routes |
| `EndpointMetadataIntegrationTest` | Agent metadata → endpoint/Admin API |
| `EventSearchIntegrationTest`, `AgentHttpIntegrationTest`, `AgentEventContractTest` | Event search and authenticated Agent API contracts |
| `AdminPassword*Test`, `AdminSessionVersionFilterTest` | Password change/reset and session invalidation |
| `UsbHashIdentityTest`, `UsbGuardHealthProjectionTest` | HASH validation and reported health/staleness |
| `FlywaySchemaIntegrationTest`, `TestPostgresSchemaSafetyTest` | Migration and test DB safety |
| `EndpointAndWhitelistPaginationIntegrationTest` | Backend filtering, total counts and bounded pagination |

Use test-class names only as inventory; do not infer exact current number of passed test methods without a fresh Surefire report.

## 4. Client Agent test inventory and historical refactor mapping

The historical Week 3/4 cleanup grouped `SystemInfoProviderRegressionTest` + `SystemInfoProviderRoutingTest` into `SystemInfoProviderTest`, split `Week4NotificationCorrectnessTest` into policy tests (`BlockNotificationPolicyTest`) and publisher tests (`DesktopNotificationServiceTest`), and consolidated active-session tests into `ActiveSessionSelectionTest`. These mappings remain useful for searching old QA evidence, but should not be used as current class/file names without confirming the source tree.

Active Agent suites include:

- `UsbGuardDurableAuditRetryTest`, `UsbGuardPendingPolicyLifecycleTest`, `UsbGuardWatchLossPolicyRecoveryTest`, `UsbGuardIpcRecoverySynchronizationTest`: durability, replay, watch and IPC boundaries.
- `UsbGuardAuditLifecycleTest`, `UsbGuardEffectiveAllowTransitionTest`, `UsbGuardFinalDecisionIntegrationTest`: end-to-end decision/audit models.
- `UsbGuardPolicySafetyValidationTest`, `UsbGuardFingerprintValidationTest`, `UsbGuardPolicyReconciliationTest`, `UsbGuardRuleMutationSafetyTest`, `UsbGuardManagedPolicyIntegrityTest`: policy identity and mutation safety.
- `UsbGuardWatchRecoveryTest`, `UsbGuardWatchResilienceTest`, `UsbGuardNotificationDecisionTargetTest`: watch recovery and notifications.
- `NotifierIpcSecurityTest`, `AdminServerClientTlsTest`, `UsbGuardHealthProbeTest`, `PolicySyncServiceTest`: IPC authorization, transport validation, health and sync.
- `NotifierStaleSocketRecoveryTest`: Linux-only cases may be skipped on Windows. Such SKIP is **not** proof of Linux PASS.

## 5. Frontend test inventory

- Unit/contract tests include `phase3_endpoint_whitelist_pagination.test.mjs` and `usbguard-health-contract.test.mjs`.
- `frontend/tests/browser-live.mjs` validates the **packaged React UI against real Spring/PostgreSQL** using synthetic HTTP events, including server-side pagination/search/date filtering and login/session.
- `npm --prefix frontend run test:browser:live` and `npm --prefix frontend run test:live` are separate HTTP/browser integration suites; both are invoked by the normal Windows verifier (unless skipped).
- Frontend Event Logs uses **10 rows/page**; an event filter with 14 results must show **1–10 on page 1 and 11–14 on page 2**, not 1–14 on the first page. A stale UI assertion must be fixed in the test; don't change correct pagination code merely to make a test pass.

## 6. Independent native deployment evidence still required

Windows unit/integration tests do not independently verify Nginx CA/host name, real two-source-IP route ACL, Ubuntu `.deb` install/upgrade/remove, reboot resilience, physical USBGuard rule application, per-endpoint hardware isolation, desktop popup, offline spool recovery or Linux-only notifier IPC. The owner-reported minimal Ubuntu P0/C1–C5 PASS is documented on the **09/11 baseline**, with extended scenarios still deferred; see [owner acceptance](qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md).

For a new release candidate, capture `git rev-parse HEAD`, `git status --short`, actual build flags, Surefire/frontend results, JAR/`.deb` hashes, skipped gates and separate Ubuntu/HTTPS test evidence. `git diff --check` validates patch whitespace only; it is not a runtime test.

# USBProtection Admin Server Developer Guide

> **Vận hành và cài đặt:** xem [RUN_GUIDE.md](RUN_GUIDE.md). **Phát hành Admin JAR và Windows verification tại HEAD 20/11/2026:** xem [ADMIN_SERVER_RELEASE.md](ADMIN_SERVER_RELEASE.md). Tài liệu này giữ kiến trúc, API, build workflow và ghi chú developer; không thay thế runbook triển khai chung.

Owner: M1. Runtime database update: 2026-11-04 (`vinhkhang` `035b8ed5a4f5b111dace06cf839cf5f1c33f1142`). Earlier Week 4 evidence remains historical. API contract: frozen v0.2. Current source baseline: `main` `c7381fc7b9ba34aa561083431ce7c68b9a514a51` (20/11/2026); scripts originally introduced earlier. Owner-reported Windows PASS is scoped to executed gates; the committed `releases/` JAR is a dirty-tree build and is not fully release-qualified.

This guide covers the Spring Boot Admin Server, its same-origin React bundle,
local development, production configuration and Windows release checks. It does
not claim Ubuntu USBGuard, systemd, desktop notification or `.deb` runtime tests.

## Components and data flow

- `admin-server/` owns Admin sessions, Agent enrollment and credential checks,
  endpoint heartbeat state, USB event persistence, and endpoint-scoped policy.
- `frontend/` is built as static assets and copied into the Spring Boot JAR by
  the `with-frontend` Maven profile. In deployment, the UI and `/api/**` use one
  origin; no CORS wildcard is required.
- PostgreSQL stores endpoints, BCrypt Agent credential hashes, Admin password hashes,
  USB devices, events, whitelist entries and policy versions. It is a separate
  service/database; credentials are injected at runtime and never committed.
- The Client Agent owns USBGuard enforcement. Heartbeat now also transports **Agent-probed USBGuard health** (reported status/checkedAt); Server projects HEALTHY/DEGRADED/UNAVAILABLE/UNKNOWN with staleness, connectivity and desired/applied ACK checks. This is **not independent hardware attestation**; `appliedPolicyVersion` remains an authenticated Agent ACK.

## Requirements and local development

Use JDK 21; the historical Week 4 Windows release build was verified with JDK 22.0.2, Maven
3.9.9, Node.js 22.19.0 and npm 10.9.3. The Maven compiler targets Java 21. Validate
other JDK versions against the pinned Lombok compiler integration. JDK 24 is known
to fail with the pinned Lombok 1.18.36 (`Fatal error compiling ... TypeTag :: UNKNOWN`);
if the default `java` is newer than 22, point `JAVA_HOME` at a compatible JDK 21/22 for the
build instead of changing the frozen dependency. Install frontend
dependencies from the lockfile and build the backend tests:

```powershell
# Only needed when the default JDK is unsupported (example path):
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd frontend
npm ci
npm run build
cd ..
# Manual tests require test PostgreSQL settings loaded in the current process.
.\scripts\with-env.ps1 -RequireTestDb -Command { mvn -B -f admin-server/pom.xml clean test }
```

For a local Admin login and Agent enrollment, set test-only secrets in the shell
before starting the default profile:

```powershell
$env:USBSHIELD_ADMIN_USERNAME = 'admin'
$env:USBSHIELD_ADMIN_PASSWORD = '<local-only password>'
$env:USBSHIELD_ENROLLMENT_TOKEN = '<local-only enrollment token>'
cd admin-server
mvn spring-boot:run
```

The default profile binds to `127.0.0.1:8080` and connects to PostgreSQL
(default `127.0.0.1:5432/usbshield` with role `usbshield`), and disables enrollment
if its bootstrap token is absent. Set `USBSHIELD_DB_PASSWORD` in the same shell;
IntelliJ Database credentials are not inherited by Maven/Java. No Admin account is created until an initial password is set.
Local Swagger/OpenAPI endpoints are `/swagger-ui.html` and `/api-docs`.

Frontend development runs from `frontend/` with `npm run dev`; use the configured
Vite proxy for `/api` so browser requests remain same-origin from the UI's point
of view. Do not add wildcard CORS to the server.

## API and security contract

The frozen baseline is [API v0.2](API.md); the current [Phase 1–3 API/transport addendum](API_PHASE1_TO_PHASE3_ADDENDUM.md) extends it. The Week 4 freeze summary is in
[Agent–Server Protocol](AGENT_SERVER_PROTOCOL.md). The important boundaries are:

- Admin mutations require an Admin session and the session's current CSRF token.
- Enrollment requires the bootstrap token. Every other Agent route requires both
  `X-Endpoint-Id` and `X-Agent-Token`; the token hash lookup determines the Agent
  identity, and a mismatched claimed endpoint is rejected.
- Admin and Agent roles are separate. Agent credentials do not authorize Admin
  routes, and an Admin session alone does not authorize Agent routes.
- Allow/Revoke modifies only the endpoint in the route. `desiredPolicyVersion` is
  server intent; only a valid, non-future, non-regressive ACK updates the reported
  `appliedPolicyVersion`.
- Endpoint/whitelist list views use `/api/admin/endpoints/page` and `/api/admin/whitelist/page`; both are bounded, DB-filtered and paginated. Legacy endpoint list has a compatibility cap of 100. Endpoint detail and scoped Allow/Revoke are retained.
- Fingerprint/HASH policy validation must agree Server ↔ Agent; malformed policy is rejected before USBGuard and is not ACKed. Agent `live` fails closed when HTTPS/cert/hostname or durable storage is unavailable.
- **No Settings UI and no Admin password-change HTTP API** at HEAD `c7381fc7` (`POST /api/admin/account/password` was removed). Login/logout, CSRF/session and `AdminBootstrap` remain. Emergency Admin password recovery still uses `AdminPasswordResetRunner` under the **no-web** `admin-reset` profile, with `USBSHIELD_ADMIN_RESET_USERNAME`/`USBSHIELD_ADMIN_RESET_PASSWORD`; a successful reset increments credential version and invalidates existing sessions. `USBSHIELD_ADMIN_PASSWORD` remains **initialization-only**.
- Enrollment never silently rotates an existing machine's credential. A duplicate
  machine ID returns 409 and needs a controlled administrator recovery process.
- Agent tokens are returned only at enrollment and persisted as BCrypt hashes.
  Event logs keep endpoint/event IDs only; they do not copy username, UID, session,
  USB serial or device hash into INFO logs.

Expected errors: 400 for invalid input, 401 for absent/invalid credentials, 403
for role/endpoint/CSRF failures, 404 for unknown resources and 409 for duplicate,
stale or future state. Do not include secrets or personal event data in test logs,
screenshots or release evidence.

## PostgreSQL profiles and security boundary

See the single canonical [RUN_GUIDE.md](RUN_GUIDE.md) for SQL role/database setup,
build/run commands, Ubuntu Agent installation, LAN/HTTPS and backup procedure.
This developer document records the relevant application properties:

| Property / environment | Behavior |
|---|---|
| `spring.datasource.url` / `USBSHIELD_DB_URL` | `prod` needs explicit `jdbc:postgresql://host:5432/usbshield` URL; not a local H2 path |
| `USBSHIELD_DB_USERNAME` | Role for Admin Server (default `usbshield`) |
| `USBSHIELD_DB_PASSWORD` | Database login secret; required in `prod`, never commit |
| `USBSHIELD_DB_HOST`, `USBSHIELD_DB_PORT`, `USBSHIELD_DB_NAME` | Used in default `application.yml` URL; do not replace `USBSHIELD_DB_URL` in `prod` |
| `USBSHIELD_TEST_DB_USERNAME`, `USBSHIELD_TEST_DB_PASSWORD` | Dedicated `usbshield_test` role for integration/browser tests; not app credentials |
| `USBSHIELD_TEST_DB_HOST`, `USBSHIELD_TEST_DB_PORT` | Optional test PostgreSQL address; browser harness fixes database to `usbshield_browser_test` |
| `USBSHIELD_ADMIN_USERNAME`, `USBSHIELD_ADMIN_PASSWORD` | Initial Admin bootstrap only; not a password-reset mechanism |
| `USBSHIELD_ENROLLMENT_TOKEN` | Agent enrollment secret, distinct from Admin browser credentials |

`application.yml` and `application-prod.yml` select PostgreSQL; **Flyway V1/V2** manages table/column migrations with `baseline-on-migrate=false`, `clean-disabled=true`; Hibernate runs `ddl-auto=validate`. See [Flyway baseline](../admin-server/ops/POSTGRES_FLYWAY_BASELINE.md): existing unversioned `usbshield` requires backup, schema preflight and **one-time** V1 baseline. Fresh database needs no baseline. Test databases are never baselined or pointed at application `usbshield`.

`prod` binds to loopback by default, sets Secure/HttpOnly/SameSite Strict session
cookies, disables Swagger/OpenAPI, and does not trust forwarded headers. Terminate
HTTPS at a trusted reverse proxy to provide browser access from other machines.
PostgreSQL TCP port 5432 is separate from the HTTP API and should not be exposed to
Client endpoints. Protect role secrets, database storage and backups; use PostgreSQL
backup/restore tooling rather than copying live database files.

## Test isolation and secure live transport (Phase 1–3)

- Test-only PostgreSQL role: `usbshield_test`. Maven integration test classes use distinct `usbshield_it_*` schemas in `usbshield_test`, cleaned after the class. Browser-live uses `usbshield_e2e_*` schemas in `usbshield_browser_test`, cleaned on normal completion; abnormal termination may leave schemas needing reviewed manual cleanup. Both use Flyway in test schema; never run destructive tests against `usbshield`.
- `scripts/with-env.ps1 -RequireTestDb -Command { ... }` loads root `.env` (untracked/secret); Maven and npm do **not** read it automatically. The role must have schema CREATE only on dedicated test DBs. No baseline step for tests.
- **Runtime E2E file is separate:** `.env.e2e.local` is not test `.env`. Running `java -jar ... --spring.profiles.active=prod` does not read that file; use `scripts/with-env.ps1 -EnvFile .env.e2e.local -Command { ... }` or set the variables in the OS/shell. Build-time `.env` values are not embedded in the JAR. See [release guide](ADMIN_SERVER_RELEASE.md).
- Agent `live` requires `USBSHIELD_SERVER_URL=https://<matching-SAN>` and optional `USBSHIELD_TLS_CA_CERTIFICATE` absolute PEM path for private CA. Do not turn off hostname or trust validation. Debian `agent.env` is root-owned mode 0600 and should exist **before** `.deb` postinst starts/restarts Agent.
- Nginx sample `packaging/nginx/usbshield-https-acl.conf.example` uses source-IP ACL; Admin allowlist **cannot include Client IP**. Spring `prod` binds loopback, TLS terminates at Nginx. No rate limiting is implemented (accepted owner decision; brute-force risk remains for allowlisted Admin sources).
- Test-only synthetic HTTP events/ACK do not validate Ubuntu USBGuard or Linux notifier stale socket recovery (`@EnabledOnOs(LINUX)` can skip three tests on Windows). Run the Linux acceptance checklist separately.

## Build, verify and provenance (source HEAD 20/11/2026)

Use the following **current** entrypoints at repository root:

```powershell
# Comprehensive Windows gate, including Agent and .deb validation:
.\packaging\tests\verify-windows.ps1

# Scope limited to Admin Maven/frontend/JAR/live suites (no Agent/.deb):
.\packaging\tests\verify-windows.ps1 -AdminOnly

# Distributable Admin JAR + .manifest.json + .sha256; requires clean Git tree:
.\admin-server\build-release.ps1

# Alternative external release location:
.\admin-server\build-release.ps1 -OutputDirectory 'C:\USBShieldReleases'
```

`build-release.ps1` runs `npm ci`, React production build and (by default) Maven's `-Pwith-frontend clean package` with **test-only PostgreSQL `.env`**. It does not package secrets. Default release directory is `admin-server/target/release/`, while the build JAR remains at `admin-server/target/usbshield-admin-server.jar`. A versioned `usbshield-admin-server_<commit>_<UTC>.jar`, manifest and `.sha256` record source and artifact provenance. `-AllowDirtyTree` or `-SkipTests` weakens release qualification and must be disclosed. JAR inspection uses `jar.exe` from the selected JDK, preventing mixed-JDK PATH errors.

`verify-windows.ps1` requires Windows PowerShell **7.6+**, loads only test DB variables, runs Maven/frontend tests and verifies JAR content before the live browser/HTTP suites. These use `usbshield_browser_test` with random per-run schemas, Flyway and Hibernate `validate`; no `ddl-auto=create` legacy procedure is imported. Default mode additionally tests the Agent and checks Windows `.deb` packaging. `-SkipTests`, `-SkipBrowserLive` or `-SkipDebBuild` makes the run partial; **do not claim full Windows PASS**. See the [release guide](ADMIN_SERVER_RELEASE.md) for invocation/operational separation.

Historical M1 Week 4 manifests and acceptance reports stay untouched; they refer to **previous** builds and tests. The removed `admin-server/verify-week1.ps1`, `verify-week2.ps1` and `verify-week3.ps1` are historical and **not** current verification entrypoints. No fresh Windows, HTTPS/LAN, physical USB or Ubuntu runtime PASS is claimed merely because the scripts are present in HEAD.

## Operations and troubleshooting

- `401` from Agent APIs: check the endpoint ID and token pair; a token is bound to
  one endpoint and is not recoverable from its BCrypt hash.
- `409` during enrollment: the machine ID is already registered. Do not delete or
  rotate credentials silently; use the agreed administrator recovery workflow.
- Endpoint shown OFFLINE: the last authenticated heartbeat is older than
  `USBSHIELD_OFFLINE_AFTER_SECONDS` (90 seconds by default). Connection status is separate from **Agent-reported USBGuard health**; stale/unknown health is not a healthy-enforcement assertion.
- Admin login fails after changing the environment password: bootstrap only creates an initial account. There is **no authenticated password-change Web flow** after Settings removal; use the offline no-web `admin-reset` maintenance path for authorized recovery, not an `.env` edit and restart.
- PostgreSQL login failure: check `USBSHIELD_DB_URL`, host/port, role, password,
  service status, firewall and `pg_hba.conf` authentication. IntelliJ may have a
  separate saved password from the terminal running Maven/JAR.
- `permission denied for schema public` or `must be owner of table`: check that
  the **correct database** and table/schema owner are being used. Never grant
  browser-test role destructive access to the application database or drop app schema.
- `/swagger-ui.html` and `/api-docs` return 404 under `prod` by design. Use the
  local profile for interactive API exploration.
- `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${USBSHIELD_DB_URL}`: the `prod` JDBC URL was not resolved in the Java process; load **runtime** settings (not test `.env`) before diagnosing database connectivity.

## Scope still deferred

Windows Maven/HTTP checks do not prove Ubuntu service installation, USBGuard
enforcement, actual USB behavior, desktop notification, reboot persistence or `.deb`
install/upgrade/remove behavior. Those remain `DEFERRED / NOT EXECUTED` until the
group runs the planned Ubuntu acceptance gate.

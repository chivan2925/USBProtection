# USBShield Admin Server Developer Guide

> **Vận hành và cài đặt:** xem [RUN_GUIDE.md](RUN_GUIDE.md). Tài liệu này giữ kiến trúc, API, build workflow và ghi chú developer; không thay thế runbook triển khai chung.


Owner: M1. Runtime database update: 2026-11-04 (`vinhkhang` `db125d28861acdb6d07aaed96c53cddfcba07408`). Earlier Week 4 evidence remains historical. API contract: frozen v0.2.

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
- The Client Agent owns USBGuard enforcement. The server cannot independently
  prove that the local policy was applied: heartbeat reports connectivity, while
  `appliedPolicyVersion` is an authenticated Agent ACK.

## Requirements and local development

Use JDK 21; the Week 4 Windows release build was verified with JDK 22.0.2, Maven
3.9.9, Node.js 22.19.0 and npm 10.9.3. The Maven compiler targets Java 21. Validate
other JDK versions against the pinned Lombok compiler integration. JDK 24 is known
to fail with the pinned Lombok 1.18.36 (`Fatal error compiling ... TypeTag :: UNKNOWN`);
if the default `java` is newer than 22, point `JAVA_HOME` at JDK 21/22 for the
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
mvn -B -f admin-server/pom.xml clean test
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

The canonical contract is [API v0.2](API.md); its Week 4 freeze summary is in
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

`application.yml` and `application-prod.yml` both select the PostgreSQL driver;
Hibernate is configured for `ddl-auto=update` for this seminar demo. No H2 schema
upgrade/migration logic is retained and historical test data are intentionally discarded.
Do not claim application-grade database migration guarantees from automatic schema updates.

`prod` binds to loopback by default, sets Secure/HttpOnly/SameSite Strict session
cookies, disables Swagger/OpenAPI, and does not trust forwarded headers. Terminate
HTTPS at a trusted reverse proxy to provide browser access from other machines.
PostgreSQL TCP port 5432 is separate from the HTTP API and should not be exposed to
Client endpoints. Protect role secrets, database storage and backups; use PostgreSQL
backup/restore tooling rather than copying live database files.

## Build, verify and provenance

`admin-server/build-release.ps1` builds the React production assets, runs Maven's
clean package lifecycle with `-Pwith-frontend`, and creates a SHA-256 provenance
manifest at `docs/member1/week4-evidence/server-release-manifest.json`. The script
requires a clean Git tree so the recorded source commit/tree identifies the build.
It records Java/Maven/Node/npm versions, target Java release, JAR size and digest.

After the build, run the isolated production-profile HTTP check:

```powershell
./admin-server/verify-week3.ps1 `
  -JavaHome 'C:\Path\To\JDK' `
  -EvidencePath 'docs/member1/week4-evidence/http'
```

The check starts its own server process and uses the disposable PostgreSQL
`usbshield_browser_test` database (role `usbshield_test`) with synthetic credentials/events;
it **requires `USBSHIELD_TEST_DB_PASSWORD` and may create/drop tables in that test DB**.
It restores selected shell variables. Never point this script at the `usbshield` app DB.
Historical Week 4 pass logs refer to an earlier H2 runtime and are not proof of a
PostgreSQL run. It checks the
packaged index/assets, route redirect, 404 behavior, disabled production API docs,
Admin session/CSRF, Agent authentication, event query, per-endpoint policy changes
and ACK validation. It is a Windows HTTP check, not a physical USB or Ubuntu E2E
test. Browser/UI regression remains separately owned by M4/M5.

## Operations and troubleshooting

- `401` from Agent APIs: check the endpoint ID and token pair; a token is bound to
  one endpoint and is not recoverable from its BCrypt hash.
- `409` during enrollment: the machine ID is already registered. Do not delete or
  rotate credentials silently; use the agreed administrator recovery workflow.
- Endpoint shown OFFLINE: the last authenticated heartbeat is older than
  `USBSHIELD_OFFLINE_AFTER_SECONDS` (90 seconds by default). This does not measure
  USBGuard health or the physical connection.
- Admin login fails after changing the environment password: bootstrap only creates
  a first account; it does not reset a stored password.
- PostgreSQL login failure: check `USBSHIELD_DB_URL`, host/port, role, password,
  service status, firewall and `pg_hba.conf` authentication. IntelliJ may have a
  separate saved password from the terminal running Maven/JAR.
- `permission denied for schema public` or `must be owner of table`: check that
  the **correct database** and table/schema owner are being used. Never grant
  browser-test role destructive access to the application database or drop app schema.
- `/swagger-ui.html` and `/api-docs` return 404 under `prod` by design. Use the
  local profile for interactive API exploration.

## Scope still deferred

Windows Maven/HTTP checks do not prove Ubuntu service installation, USBGuard
enforcement, actual USB behavior, desktop notification, reboot persistence or `.deb`
install/upgrade/remove behavior. Those remain `DEFERRED / NOT EXECUTED` until the
group runs the planned Ubuntu acceptance gate.

# USBShield Admin Server Developer Guide

Owner: M1. Updated 2026-11-01 (Asia/Saigon). API contract: frozen v0.2.

This guide covers the Spring Boot Admin Server, its same-origin React bundle,
local development, production configuration and Windows release checks. It does
not claim Ubuntu USBGuard, systemd, desktop notification or `.deb` runtime tests.

## Components and data flow

- `admin-server/` owns Admin sessions, Agent enrollment and credential checks,
  endpoint heartbeat state, USB event persistence, and endpoint-scoped policy.
- `frontend/` is built as static assets and copied into the Spring Boot JAR by
  the `with-frontend` Maven profile. In deployment, the UI and `/api/**` use one
  origin; no CORS wildcard is required.
- H2 stores endpoints, BCrypt Agent credential hashes, Admin password hashes,
  USB devices, events, whitelist entries and policy versions. In production, its
  path and password come from environment configuration and the data file must be
  outside Git in a private persistent directory.
- The Client Agent owns USBGuard enforcement. The server cannot independently
  prove that the local policy was applied: heartbeat reports connectivity, while
  `appliedPolicyVersion` is an authenticated Agent ACK.

## Requirements and local development

Use JDK 21 or newer, Maven 3.9+, Node.js and npm. The Maven compiler targets Java
21. Install frontend dependencies from the lockfile and build the backend tests:

```powershell
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

The default profile binds to `127.0.0.1:8080`, writes its H2 file under the
ignored `admin-server/data/` directory, and disables enrollment if its bootstrap
token is absent. No Admin account is created until an initial password is set.
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

## Production profile

Set all secret values through a secret manager or the service environment, not in
source files or command history. Required values are:

| Variable | Purpose |
|---|---|
| `USBSHIELD_DB_URL` | Persistent H2 file URL, for example `jdbc:h2:file:/var/lib/usbshield/usbshield;DB_CLOSE_ON_EXIT=FALSE` |
| `USBSHIELD_DB_PASSWORD` | Password for the H2 file; keep it stable when reopening that database |
| `USBSHIELD_ENROLLMENT_TOKEN` | Bootstrap credential for new Agent enrollment |
| `USBSHIELD_ADMIN_PASSWORD` | Initial Admin password; it does not reset an existing account |
| `USBSHIELD_ADMIN_USERNAME` | Initial Admin username; defaults to `admin` |

Optional values: `USBSHIELD_DB_USERNAME` (default `sa`), `USBSHIELD_BIND_ADDRESS`
(default loopback), `USBSHIELD_COOKIE_SECURE` (default `true`), `PORT` (default
8080) and `USBSHIELD_OFFLINE_AFTER_SECONDS` (default 90). Production requires
`USBSHIELD_DB_URL`, DB password and enrollment token to be present.

Build and run the JAR with the `prod` profile:

```powershell
./admin-server/build-release.ps1 -JavaHome 'C:\Path\To\JDK'
java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod
```

Production binds to loopback, sets Secure/HttpOnly/SameSite Strict session
cookies, disables H2 console and Swagger/OpenAPI, and does not trust forwarded
headers. For remote Admin access, terminate HTTPS at a trusted reverse proxy and
proxy to the loopback listener. Direct plain HTTP cannot carry the Secure session
cookie. Never disable Secure cookies or bind the app to a public interface without
a reviewed TLS and access-control boundary.

The service account needs read/write access to the H2 parent directory and read
access to the JAR. Restrict DB and backup files to that account. Stop the service
before making a file-level H2 backup. Do not copy a live production database into
the repository or a demo artifact. This deployment is a single server with local
H2 storage; it is not a clustered or high-availability design.

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

The check starts only its own server process, uses a new target-local H2 file and
synthetic credentials/events, then restores the shell environment. It checks the
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
- H2 will not open after a DB password change: restore the matching secret or use a
  verified backup. Do not reset the production database as a troubleshooting step.
- `/swagger-ui.html` and `/api-docs` return 404 under `prod` by design. Use the
  local profile for interactive API exploration.

## Scope still deferred

Windows Maven/HTTP checks do not prove Ubuntu service installation, USBGuard
enforcement, actual USB behavior, desktop notification, reboot persistence or `.deb`
install/upgrade/remove behavior. Those remain `DEFERRED / NOT EXECUTED` until the
group runs the planned Ubuntu acceptance gate.

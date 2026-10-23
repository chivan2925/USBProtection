# M1 — Week 3 Admin Server hardening

Date: 2026-10-23 (Asia/Saigon). Reviewer/implementer: Codex.
Baseline revision: `0a42640cf217360e9c86fe61377ddaff2c8f6a9a`; results apply to this
working tree plus the Week 3 changes, not a committed release tag.
Environment: Windows 11, Maven 3.9.9, JDK 22.0.2 targeting Java release 21,
installed Node/npm and frontend lockfile dependencies.

## Changes and gates

| M1 requirement | Implementation / verification |
|---|---|
| Admin/Agent security separation | Week 2 session/CSRF/credential tests retained; new negative role-boundary tests |
| Invalid/corrupt Agent credential | Reject absent, oversized or malformed credentials and stored BCrypt hashes with unsupported costs as 401 |
| Enrollment/bootstrap | Existing bootstrap secrets, unique machine enrollment and no automatic credential reissue retained; regression tested |
| ONLINE/OFFLINE | Fresh endpoint has null lastSeen and OFFLINE until authenticated heartbeat; expired timestamps become OFFLINE in API projection |
| Desired/applied reconciliation | Existing negative/future/stale/duplicate ACK and A/B tests retained; added delayed ACK while desired is newer |
| Production profile | application-prod.yml: external DB/secret config, loopback bind, secure cookies, 30m session, INFO logging, H2 console disabled |
| Single server JAR + React | with-frontend Maven profile copies frontend/dist into generated target/classes/static; build-release.ps1 builds React before clean package |
| SPA navigation | Known plain paths redirect into current hash routes; assets/API never fall back to index.html |
| Docs/handoff | API/protocol/architecture addenda; v0.2 wire contract unchanged |

Status is a response projection from lastSeen, not an ONLINE value persisted forever.
Old rows retain their timestamps and naturally expire; no existing database is reset.
The API fields/routes and policy JSON remain unchanged. New enrollment now correctly
returns an endpoint that stays OFFLINE until the first heartbeat, which M3/M4 should
expect. Applied version remains an authenticated Agent report, not hardware proof.

## Commands and evidence

| Command / check | Result | Evidence |
|---|---|---|
| npm run build (frontend) | PASS | [React build](week3-evidence/02-frontend-build.log) |
| mvn -B -f admin-server/pom.xml -Pwith-frontend clean package | PASS: 19 tests, 0 failures/errors/skips; JAR built | [package log](week3-evidence/03-package-tests.log), [test summary](week3-evidence/test-summary.txt) |
| Week 2 contract/security/policy/H2 regression | PASS | Same Maven run, including actual M3 consumer DTO source test |
| Prod defaults test | PASS | Week3ProductionProfileTest: secure/HttpOnly cookies, loopback, no console/open-in-view |
| Status/auth/ACK/navigation tests | PASS | Week3SecurityAndStatusTest (5 tests) |
| Standalone prod-profile HTTP | PASS: index/assets 200, known navigation redirect, missing asset 404 | [server log](week3-evidence/server-runtime.log), [static serving](week3-evidence/07-static-http.txt) |
| Login/session/CSRF, Agent auth, A/B policy, query | PASS (Windows HTTP) | week3-evidence/02 through 06 HTTP summaries |

The first sandbox `clean test` could not open localhost sockets; it was interrupted.
[Initial log](week3-evidence/01-maven-test.log) is diagnostic, not a passing run.
The final permitted `clean package` executes the clean test lifecycle and passes.
Unit tests use isolated H2; standalone smoke uses a new target-local file database.
The smoke harness submits synthetic HTTP input based on the unchanged M2 sample;
this is not a physical USB event or browser UI interaction test.

## Build / deployment handoff

Install frontend dependencies with npm ci first if absent. From repository root:

```powershell
./admin-server/build-release.ps1 -JavaHome 'C:\Program Files\Java\jdk-22'
./admin-server/verify-week3.ps1 -JavaHome 'C:\Program Files\Java\jdk-22'
```

Artifact: `admin-server/target/usbshield-admin-server.jar`, generated, not committed.
The backend-only Maven build is still supported. Use a clean build when switching
profiles so stale bundled assets do not remain from an earlier package. Direct use
of `-Pwith-frontend` assumes frontend/dist is already built; the release build script
checks for index.html and aborts if the React build fails.

Production configuration (external environment, never commit these values):

- USBSHIELD_DB_URL: H2 file JDBC URL pointing to a persistent writable location.
- USBSHIELD_DB_PASSWORD: required external DB password; preserve credentials when
  reusing an existing file. Back up and validate copies before changing DB config.
- USBSHIELD_ENROLLMENT_TOKEN: enrollment bootstrap credential.
- USBSHIELD_ADMIN_PASSWORD: first-account bootstrap only; optional username defaults
  admin. Existing accounts are never silently reset.
- USBSHIELD_BIND_ADDRESS: loopback default; explicitly select LAN binding if needed.
- PORT defaults 8080; USBSHIELD_OFFLINE_AFTER_SECONDS defaults 90.

Start with `--spring.profiles.active=prod`. Secure cookies default true, so real
Admin use needs HTTPS termination or direct HTTPS. Forwarded headers are not trusted
by default. The isolated Windows HTTP smoke explicitly overrides cookie secure=false;
that exception is not the production default. No CORS wildcard was introduced.

The script starts and stops only its own server process, restores environment values,
and does not write raw passwords/Agent tokens into evidence. PowerShell 7 is required.
Backend API calls and static assets are same-origin. Current frontend is packaged
as supplied; its live API/auth UI completion remains M4/M5 ownership.

## Deferred and scope conclusion

M1 backend Windows scope is implemented and verified by automated tests plus the
standalone HTTP evidence. No group-wide Week 3 or final Release Gate is asserted.
Ubuntu USBGuard enforcement, physical USB/no-serial devices, active local GUI users,
native notification, systemd/IPC, .deb clean install, reboot and fail-closed on real
Ubuntu are **DEFERRED UNTIL AFTER WEEK 4** under the provided Week 3 plan.
M4/M5 browser regression and full React live API integration are separate evidence;
serving the React bundle does not substitute for those tests.
Historical Week 1/2 evidence, frozen M2 sample, frontend/client/packaging source and
M5 QA files are left unchanged.

# USBShield REST API v0.2 — Week 2

Owner: M1. Updated 2026-10-16 (Asia/Saigon).
Canonical Admin routes are `/api/admin/**`, not `/api/endpoints` or `/api/events`.
[Week 1 draft archive](member1/API_V0_1_ARCHIVE.md) is historical; no Week 1 Gate is reopened.
OpenAPI: `/api-docs`; Swagger: `/swagger-ui.html`.

## Authentication and deployment

Same-origin Admin session (`JSESSIONID`, HttpOnly, SameSite=Strict). No CORS wildcard.
Frontend dev should proxy `/api` to server (M4); deployment over HTTPS should set
`SERVER_SERVLET_SESSION_COOKIE_SECURE=true`.

1. `GET /api/auth/csrf`: returns `{token, headerName}` and session cookie.
2. `POST /api/auth/login` with JSON `{username,password}`, same cookie, header
   `X-CSRF-TOKEN: <token>`. Returns `{username}`; bad credentials 401.
3. After login, GET CSRF again (session ID and token rotate).
4. Preserve cookie for reads; send current CSRF header for Admin mutations/logout.
5. `GET /api/auth/me` → `{username}`; `POST /api/auth/logout` → `{success:true}`.

Configure `USBSHIELD_ADMIN_PASSWORD` and optionally `USBSHIELD_ADMIN_USERNAME`.
A BCrypt account is created only if absent; changing the environment does not
reset an existing password. Empty password creates no account, no public default.
Configure `USBSHIELD_ENROLLMENT_TOKEN`; empty disables enrollment (503).
Passwords/tokens must not be stored in source, screenshots or evidence logs.

## Agent API

| Method/path | Request | Response / authorization |
|---|---|---|
| POST `/api/agent/enroll` | `{hostname,machineId,ipAddress?,enrollmentToken}` | `{endpointId,agentToken,currentPolicyVersion}`; bootstrap credential required |
| POST `/api/agent/heartbeat` | `{endpointId,hostname?,agentVersion?,currentPolicyVersion?}` | `{accepted,serverTime,desiredPolicyVersion,serverPolicyVersion,lastAppliedPolicyVersion,status}` |
| POST `/api/agent/events` | [M2 sample](agent-contract/sample-usb-event.json), using enrolled endpointId | `{success,eventId,receivedAt}` |
| GET `/api/agent/policy` | no body | `{policyVersion,whitelistedDevices:[...]}` |
| POST `/api/agent/policy/ack` | `{appliedPolicyVersion,appliedAt?,ruleCount?}` | `{acknowledged,appliedPolicyVersion,desiredPolicyVersion}` |

All Agent routes except enrollment require BOTH `X-Endpoint-Id` and `X-Agent-Token`.
Agent credentials do not grant Admin access. Admin session alone does not grant
Agent access. CSRF is exempted only on Agent routes, which use explicit credentials.
Tokens are returned once and stored as BCrypt hashes. Re-enrollment of an existing
machineId returns 409 (no silent rotation); retain its credential. Legacy endpoints
without a hash are rejected until an administrator performs controlled recovery.
Concurrent duplicate machine enrollments are protected by a unique machineId constraint.

Event endpointId must match authenticated identity (403 otherwise). `eventType`:
CONNECTED/DISCONNECTED; `decision`: ALLOWED/BLOCKED. `device` and four-hex-digit
VID/PID are required. Missing/blank username becomes UNKNOWN; UID/session become
null for UNKNOWN. Offset timestamps normalize to UTC. Sample mouse stays session
21 / ALLOWED. In live transport, replace only the temporary endpoint reference
with the enrolled identity; never rewrite the frozen M2 evidence file.

Heartbeat updates lastSeen, not applied version. `currentPolicyVersion` is telemetry.
Only ACK updates applied version. Negative/malformed ACK → 400; future (> desired)
or regressive (< applied) → 409. Duplicate ACK is idempotent. Older than desired
but >= applied is accepted (a valid previous apply can arrive during a new update).
ACK is an authenticated Agent report, not independent proof of hardware enforcement.

Policy **canonical serialized field is `whitelistedDevices`**, matching existing M3
`PolicyResponse`; `allowedDevices` from v0.1 is no longer emitted. Device entries:
`vendorId,productId,serialNumber,deviceHash,deviceName,fingerprintValue`.
Policy omits `fingerprintType` to match M3 DTO; HASH is the only supported type,
and `deviceHash` / `fingerprintValue` both carry that hash. Admin entry DTO retains the type.
Week 2 accepts HASH identity only. No VID/PID-only or name-based grants; missing hash
returns 409 and needs identity review with M2/M3. Legacy non-HASH rules are rejected
on policy fetch rather than silently broadened.

## Admin APIs (session required)

| Method/path | Request | Result |
|---|---|---|
| GET `/api/admin/endpoints` | none | array of EndpointResponse |
| GET `/api/admin/endpoints/{endpointId}` | none | EndpointResponse, 404 if absent |
| GET `/api/admin/endpoints/{endpointId}/whitelist` | none | enabled entries array, empty `[]` |
| POST same whitelist URL | `{usbDeviceId}` | entry; duplicate enabled add returns same entry without version bump |
| DELETE `/api/admin/endpoints/{endpointId}/whitelist/{entryId}` | none | 204; repeated revoke of same known entry is idempotent |
| GET `/api/admin/events` | filters below | EventPage |
| GET `/api/admin/events/{id}` | none | EventResponse |

EndpointResponse: `endpointId,hostname,ipAddress,status,lastSeen,policyVersion,appliedPolicyVersion`.
Status ONLINE if lastSeen within configured `usbshield.offline-after-seconds` (default 90),
otherwise OFFLINE. It represents connectivity, not independently measured USBGuard health.
No token hashes/passwords in responses. No endpoint-delete API implemented in Week 2.

Whitelist entry: `id,endpointId,usbDeviceId,fingerprintType,fingerprintValue,enabled`.
Endpoint is taken from URL, never body. Select `usbDeviceId` from event response.
Create/re-enable/revoke changes only that endpoint's desired version; serialized
endpoint row locking prevents concurrent version loss. Revoke disables entry and
excludes it from policy. Unknown/cross-endpoint entry returns 404.

Event filters (exact match): `endpointId,linuxUsername,deviceId,decision,eventType`;
`from,to` are inclusive ISO-8601 instants with timezone. Range applies to occurredAt.
`page` zero-based default 0; `size` default 20, range 1..100. Stable sort:
occurredAt DESC, id DESC. Invalid range/enum/size → 400. Empty page remains real empty data.

EventPage: `{content: EventResponse[],page,size,totalElements,totalPages}`.
EventResponse: `id,endpointId,hostname,linuxUsername,linuxUid,sessionId,sessionType,seat,
usbDeviceId,device,eventType,decision,occurredAt,receivedAt`. Device uses Agent DTO
fields `vendorId,productId,serial,name,hash,interfaceClass`. Hostname is captured on
ingestion; legacy rows fall back to registry hostname. FE maps enums to its view
model at the adapter boundary; no backend contract change or mock fallback.

## Errors

JSON body: `{status:400,message:"Invalid request fields",timestamp:"...Z"}`.
400 invalid JSON/validation/query; 401 missing/invalid credential; 403 forbidden
identity/role or missing CSRF; 404 missing resource; 409 duplicate/conflicting data
or policy version/identity; 503 enrollment disabled. Network failure has no HTTP
response; retry is owned by Agent. Ingestion success means persisted, not USB allowed.

## Evidence and handoff

[Week 2 server report](member1/WEEK2_SERVER_INTEGRATION.md).
M3 must review policy field and ACK semantics; M4/M5 consume explicit DTO/paging and
CSRF lifecycle. Cross-member sign-off and physical Ubuntu E2E must be recorded by
those members; this document is not their approval.


## Week 3 deployment/status addendum (2026-10-23)

Wire routes and DTOs **unchanged from v0.2**. New endpoints start OFFLINE with
`lastSeen=null` until an authenticated heartbeat. No synthetic enrollment timestamp.
Existing rows retain their lastSeen and age out by the configured timeout.
Malformed stored Agent hash, unsupported BCrypt cost, missing/oversized credentials
fail with 401. Admin session and Agent credential boundaries remain separate.

Build with `admin-server/build-release.ps1` (JDK 21/22, Maven, Node/npm with installed
frontend dependencies), or run `npm run build` in frontend then
`mvn -B -f admin-server/pom.xml -Pwith-frontend clean package` from root.
The Maven profile copies dist into target/classes/static, not source-controlled static.
The resulting JAR serves React and API at the same origin. The current frontend uses
hash routes (`/#/events`); known plain navigation paths redirect to those routes.
API paths and missing asset paths never fall back to index.html.

Use `--spring.profiles.active=prod` with `USBSHIELD_DB_URL` (H2 file JDBC URL),
`USBSHIELD_DB_PASSWORD`, `USBSHIELD_ENROLLMENT_TOKEN`, and first-account
`USBSHIELD_ADMIN_PASSWORD` configured outside source. Optional username defaults admin.
Bind defaults 127.0.0.1, PORT defaults 8080; change USBSHIELD_BIND_ADDRESS explicitly
for LAN. Secure cookie defaults true; use HTTPS directly or via trusted local reverse
proxy. Forwarded headers are not trusted by default. Only isolated HTTP tests should
use `--server.servlet.session.cookie.secure=false`. No default production password
is created. Existing account passwords are not overwritten by bootstrap.

See [Week 3 M1 report](member1/WEEK3_SERVER_HARDENING.md) for Windows evidence and
Ubuntu tests deferred until after Week 4. Bundling current frontend does not claim
its live API adapter/auth UI integration is completed by M4/M5.

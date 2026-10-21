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
Week 3 name-search extension (2026-10-21): optional `endpoint` matches a
case-insensitive literal substring of endpoint ID or the hostname displayed in
EventResponse. Captured event hostname takes precedence; only legacy rows with
a null hostname fall back to the registered endpoint name. Optional `device`
matches a case-insensitive literal substring of `device.name`, or the exact
positive numeric `usbDeviceId` (including historical rows missing device details).
Leading/trailing whitespace is ignored; blank search fields add no predicate.
`%`, `_` and backslash are literal characters, not user-provided SQL wildcards.
Name searches combine with all other filters using AND and execute before
counting/pagination. Existing exact `endpointId`/`deviceId` semantics are unchanged.
The live Linux User filter sends optional `username`: a case-insensitive literal
substring of the stored Linux username, with trimmed whitespace and escaped
wildcard characters. It is filtered/counted before pagination and combines with
other criteria using AND. Existing `linuxUsername` remains an exact-match
parameter for callers that need it; neither parameter searches numeric Linux UID.
`page` zero-based default 0; `size` default 20, range 1..100. Stable sort:
occurredAt DESC, id DESC. Invalid range/enum/size → 400. Empty page remains real empty data.

EventPage: `{content: EventResponse[],page,size,totalElements,totalPages}`.
EventResponse: `id,endpointId,hostname,linuxUsername,linuxUid,sessionId,sessionType,seat,
usbDeviceId,device,eventType,decision,occurredAt,receivedAt`. Device uses Agent DTO
fields `vendorId,productId,serial,name,hash,interfaceClass`. Hostname is captured on
ingestion; legacy rows fall back to registry hostname. FE maps enums to its view
model at the adapter boundary; no backend contract change or mock fallback.

## Errors

### 6.1. Gửi Heartbeat thử nghiệm:
```bash
curl -X POST http://localhost:8080/api/agent/heartbeat \
  -H "Content-Type: application/json" \
  -d '{
    "endpointId": "POC-UNENROLLED-LAB-PC-01",
    "hostname": "usbshield-lab",
    "agentVersion": "1.0.0-SNAPSHOT",
    "currentPolicyVersion": 1
  }'
```

### 6.2. Gửi sự kiện USB kèm Active User:
```bash
curl -X POST http://localhost:8080/api/agent/events \
  -H "Content-Type: application/json" \
  -d '{
    "endpointId": "POC-UNENROLLED-LAB-PC-01",
    "hostname": "usbshield-lab",
    "linuxUsername": "usbdev",
    "linuxUid": 1000,
    "sessionId": "21",
    "sessionType": "wayland",
    "seat": "seat0",
    "device": {
      "vendorId": "093a",
      "productId": "2510",
      "serial": "",
      "name": "USB Optical Mouse",
      "hash": "L46bZEAKg59EA+puzjHMh8D4jLliJqK4sYxKSd/DRz8=",
      "interface": "03:01:02"
    },
    "eventType": "CONNECTED",
    "decision": "ALLOWED",
    "occurredAt": "2026-09-28T14:10:16+07:00"
  }'
```

Ví dụ event trên phải khớp `docs/agent-contract/sample-usb-event.json` của M2: USB Optical Mouse (class `03:01:02`) được `ALLOWED`, session `21`. Đây là sample peripheral, không phải evidence chặn Flash Disk. Để gửi nguyên sample từ repo root, dùng:

```bash
curl -X POST http://localhost:8080/api/agent/events \
  -H "Content-Type: application/json" \
  --data-binary @docs/agent-contract/sample-usb-event.json
```

### 6.3. Kéo chính sách Whitelist của Endpoint:
```bash
curl -X GET http://localhost:8080/api/agent/policy \
  -H "X-Endpoint-Id: POC-UNENROLLED-LAB-PC-01"
```

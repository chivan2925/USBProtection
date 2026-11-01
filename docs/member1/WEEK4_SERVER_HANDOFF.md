# M1 — Week 4 Admin Server handoff

Date: 2026-11-01 (Asia/Saigon). Branch: `van`.
Scope: Admin Server, canonical backend documentation, packaged server JAR and M1
integration review. This report separates M1 evidence from group/Ubuntu gates.

## Handoff status

| Work ID | Status | Evidence / disposition |
|---|---|---|
| W4-M1-01 API/DTO freeze | DONE | API remains v0.2 with no breaking wire changes. Canonical route/auth/state rules and validated field limits are summarized in [API](../API.md) and [protocol](../AGENT_SERVER_PROTOCOL.md). Final source SHA is recorded after build. |
| W4-M1-02 security/code review | REVIEWED | Admin/Agent separation, session/CSRF, endpoint-bound token, bootstrap, production configuration and log handling reviewed. Hardened UTF-8 token byte limit, request sizes, production Swagger/OpenAPI exposure and event log minimization. Backend regression passes. Deployment credential rotation remains external/open. |
| W4-M1-03 regression | PARTIAL | Maven backend/integration suite: PASS, 28 tests, 0 failures/errors/skips on JDK 22.0.2/Maven 3.9.9. Packaged-JAR HTTP check remains pending. Browser regression is M4/M5 evidence. |
| W4-M1-04 documentation | DONE | README, Architecture, API and Agent protocol aligned; [Developer Guide](../DEVELOPER_GUIDE.md) added. |
| W4-M1-05 server JAR | NOT BUILT | Release script now requires a clean tree and emits a provenance manifest. Build and same-origin HTTP verification are pending. |
| W4-M1-06 integration owner | OPEN | M1 contract review is recorded here. Do not create a release-candidate tag until all owners sign off and the QA blocker below is closed or formally dispositioned. |
| W4-M1-07 credential rotation | OPEN | A credential appeared in public Git history. The deployment secret is not available in this workspace; the environment administrator must rotate it and re-enroll affected Agents. No success claim is made. |

## Review findings and changes

The current M1 implementation keeps these frozen semantics: Admin session plus CSRF
for mutations; separate Agent token authentication bound to one endpoint; endpoint
identity taken from the authenticated token; endpoint-specific desired policy;
heartbeat as liveness telemetry; ACK as the only source of `appliedPolicyVersion`;
and status derived from heartbeat timeout rather than persisted as permanent ONLINE.

Security review found event INFO logs contained Linux username/UID/session and USB
serial/hash. Those payload fields are no longer copied into logs; successful event
logs retain only event and endpoint IDs. Agent token size is checked in UTF-8 bytes
before BCrypt so oversized input fails with 401. Enrollment and event limits now
match persistence bounds. The production profile disables Swagger/OpenAPI in
addition to the H2 console, binds to loopback by default and requires external DB
and enrollment settings. Regression evidence is pending.

## Integration dependency

The latest available QA handoff records **M5-W3-07 OPEN (M3)**: policy sync may send
an ACK after local applied-policy persistence fails. M1 does not own the Agent sync
implementation and will not mark the release gate complete until M3 closes this or
the group formally dispositions it. Ubuntu USBGuard/systemd/GUI/installer/reboot
tests remain deferred under the Week 4 plan.

## Build and verification evidence

This section must be completed from the generated manifest and command results. Do
not infer PASS from a prior Week 3 run.

| Check | Result | Provenance |
|---|---|---|
| React production build | PASS | `npm run build`, Node 22.19.0, npm 10.9.3; will be repeated by release script |
| Maven backend/integration tests | PASS: 28 tests, 0 failures/errors/skips | `mvn -B -f admin-server/pom.xml clean test`, JDK 22.0.2, Maven 3.9.9 |
| Maven clean package with React profile | PENDING | `admin-server/build-release.ps1` |
| Server JAR SHA-256 and source tree | PENDING | [Release manifest](week4-evidence/server-release-manifest.json) |
| Production-profile JAR HTTP (React + `/api`, synthetic data) | PENDING | `week4-evidence/http/` |
| API docs hidden in production | PENDING | Maven and HTTP checks |
| Browser live regression | M4/M5-owned | Not attributed to M1 |

## Final gate

M1 will report its Windows preparation status after build and HTTP evidence exist.
Group-wide Week 4 readiness still requires M2–M5 owner reviews, resolution or
disposition of M5-W3-07, the release report, and credential rotation confirmation.
No Ubuntu E2E or full release PASS is asserted by this document.

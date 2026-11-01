# M1 — Week 4 Admin Server handoff

Date: 2026-11-01 (Asia/Saigon). Branch: `van`.
Scope: Admin Server, canonical backend documentation, packaged server JAR and M1
integration review. This report separates M1 evidence from group/Ubuntu gates.

## Handoff status

| Work ID | Status | Evidence / disposition |
|---|---|---|
| W4-M1-01 API/DTO freeze | DONE | API remains v0.2 with no breaking wire changes. Canonical route/auth/state rules and validated field limits are summarized in [API](../API.md) and [protocol](../AGENT_SERVER_PROTOCOL.md). Final source SHA is recorded after build. |
| W4-M1-02 security/code review | REVIEWED | Admin/Agent separation, session/CSRF, endpoint-bound token, bootstrap, production configuration and log handling reviewed. Hardened UTF-8 token byte limit, request sizes, production Swagger/OpenAPI exposure and event log minimization. No remaining M1 P0 code finding identified; deployment credential rotation remains external/open. |
| W4-M1-03 regression | PENDING RE-RUN | Re-run Maven backend/integration suite and production-profile packaged-JAR HTTP smoke against the rebased source; browser regression remains M4/M5 evidence and is not attributed to M1. |
| W4-M1-04 documentation | DONE | README, Architecture, API and Agent protocol aligned; [Developer Guide](../DEVELOPER_GUIDE.md) added. |
| W4-M1-05 server JAR | PENDING REBUILD | Rebuild React production bundle + Spring JAR from the rebased source; refresh the manifest and HTTP evidence before proposing the artifact. |
| W4-M1-06 integration owner | OPEN — candidate decision pending | M1 can propose a source/artifact candidate after rerun. Group acceptance remains open for M2/M3 dispositions/sign-offs and the credential rotation follow-up; do not create a release-candidate tag or claim group-wide PASS. |
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

The latest QA handoff records M5-W3-07 as resolved. Remaining cross-owner items are
M3's per-UID socket ACL/ownership/startup disposition (shared directory and GUI-owned
socket access have not been demonstrated), plus M2 external PR review and contract,
native, and cross-owner confirmations. M4's shared frontend handoff has been received
and technically acknowledged. These are owner decisions; M1 does not sign for them.
Ubuntu USBGuard/systemd/GUI/installer/reboot tests remain deferred under the Week 4 plan.

## Build and verification evidence

The prior artifact and its evidence were built from pre-rebase source `d0769a3` and
are retained as historical evidence only. A fresh build will record the current source
revision/tree, toolchain, artifact checksum, and HTTP smoke results after this report
is committed on the rebased `van` branch.

| Check | Result | Provenance |
|---|---|---|
| React production build | PENDING RE-RUN | Included in `admin-server/build-release.ps1`; refreshed log will record toolchain |
| Maven backend/integration tests | PENDING RE-RUN | `mvn -B -f admin-server/pom.xml clean test`; refreshed build log will record test counts |
| Maven clean package with React profile | PENDING RE-RUN | `admin-server/build-release.ps1`; refreshed build log |
| Server JAR SHA-256 and source tree | PENDING REBUILD | Refreshed [release manifest](week4-evidence/server-release-manifest.json) |
| Production-profile JAR HTTP (React + `/api`, synthetic data) | PENDING RE-RUN | Refreshed HTTP summary and detailed synthetic auth/event/policy evidence in `week4-evidence/http/` |
| API docs hidden in production | PENDING RE-RUN | Maven MockMvc and HTTP smoke should confirm 404 for `/api-docs` and `/swagger-ui.html` |
| Browser live regression | M4/M5-owned | Not attributed to M1 |

## Final gate

M1's Windows preparation is rebased on the current `origin/main`; artifact checks
must be refreshed before this handoff is complete. Group-wide Week 4 readiness still
requires M2/M3 dispositions and owner sign-offs, plus credential rotation confirmation.
No Ubuntu E2E or full release PASS is asserted by this document.

# M1 — Week 4 Admin Server handoff

Date: 2026-11-01 (Asia/Saigon). Branch: `van`.
Scope: Admin Server, canonical backend documentation, packaged server JAR and M1
integration review. This report separates M1 evidence from group/Ubuntu gates.

## Handoff status

| Work ID | Status | Evidence / disposition |
|---|---|---|
| W4-M1-01 API/DTO freeze | DONE | API remains v0.2 with no breaking wire changes. Canonical route/auth/state rules and validated field limits are summarized in [API](../API.md) and [protocol](../AGENT_SERVER_PROTOCOL.md). Final source SHA is recorded after build. |
| W4-M1-02 security/code review | REVIEWED | Admin/Agent separation, session/CSRF, endpoint-bound token, bootstrap, production configuration and log handling reviewed. Hardened UTF-8 token byte limit, request sizes, production Swagger/OpenAPI exposure and event log minimization. No remaining M1 P0 code finding identified; deployment credential rotation remains external/open. |
| W4-M1-03 regression | PASS — M1 Windows scope | Rebased source: Maven backend/integration suite 28 tests, 0 failures/errors/skips; production-profile packaged-JAR HTTP smoke PASS with isolated file H2 and synthetic credentials/events. Browser regression remains M4/M5 evidence and is not attributed to M1. |
| W4-M1-04 documentation | DONE | README, Architecture, API and Agent protocol aligned; [Developer Guide](../DEVELOPER_GUIDE.md) added. |
| W4-M1-05 server JAR | PASS — Windows artifact | React production bundle + Spring JAR built from source commit `d78189be42a5fa00a07e46446019c55fc547a592`; SHA-256, source tree and toolchain are in the [release manifest](week4-evidence/server-release-manifest.json). Packaged JAR served React and `/api` over same-origin HTTP. |
| W4-M1-06 integration owner | M1 REVIEW COMPLETE — GROUP SIGN-OFF PENDING | M1 reviewed the current M2/M3/M4/M5 handoffs and selected the manifest artifact as the M1 Windows preparation candidate. Group release acceptance remains NO-GO pending M2/M3 dispositions and owner sign-offs; no RC tag or group-wide PASS is asserted. |
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
and enrollment settings. Refreshed regression evidence is summarized below.

## Integration dependency

M1 reviewed the latest available owner/QA handoffs and selected the Windows candidate
recorded in the manifest: source `d78189be42a5fa00a07e46446019c55fc547a592`, JAR
SHA-256 `43269264cf7cd3af5c785a12b15507ee89ce3ce3c6b42ff8e92ceb601604240d`. M5-W3-07
is resolved. M2 Part 3 has technical Windows ACK/evidence, while external PR review,
contract/native and cross-owner confirmations remain open. M3's per-UID socket
ACL/ownership/startup design is not proven; `.deb` remains NOT BUILT per the documented
environment deferral. M4's shared frontend handoff is received and technically
acknowledged. M5's S3 report records Windows evidence PASS but group release NO-GO;
M1 now records its candidate decision, without changing or signing for other owners.
No RC tag or group-wide PASS is appropriate until the required owner dispositions and
sign-offs are recorded. Credential rotation/re-enrollment remains external and open.
Ubuntu USBGuard/systemd/GUI/installer/reboot tests remain deferred under the Week 4 plan.

## Build and verification evidence

The prior artifact and its evidence were built from pre-rebase source `d0769a3` and
are retained as historical evidence only. The refreshed artifact is built from source
commit `d78189be42a5fa00a07e46446019c55fc547a592`, Git tree
`3d8ea28977ae653323bd28dca442f777d5104924`; it is the M1 Windows candidate proposed
for cross-owner review, not an accepted group release candidate. Build environment:
Windows 10 (build 26200), PowerShell 7.6.5, JDK 22.0.2 targeting Java 21, Maven
3.9.9, Node 22.19.0 and npm 10.9.3. The build started from a clean `van` worktree.
The JAR is `admin-server/target/usbshield-admin-server.jar` (57,314,825 bytes).

| Check | Result | Provenance |
|---|---|---|
| React production build | PASS | Included in `admin-server/build-release.ps1`; Node 22.19.0, npm 10.9.3; see [build log](week4-evidence/server-build.log) |
| Maven backend/integration tests | PASS: 28 tests, 0 failures/errors/skips | `mvn -B -Pwith-frontend clean package`, JDK 22.0.2, Maven 3.9.9; [build log](week4-evidence/server-build.log) |
| Maven clean package with React profile | PASS | `admin-server/build-release.ps1`; [build log](week4-evidence/server-build.log) |
| Server JAR SHA-256 and source tree | PASS | [Release manifest](week4-evidence/server-release-manifest.json), SHA-256 `43269264cf7cd3af5c785a12b15507ee89ce3ce3c6b42ff8e92ceb601604240d` |
| Production-profile JAR HTTP (React + `/api`, synthetic data) | PASS | [HTTP summary](week4-evidence/http/00-summary.txt), detailed synthetic auth/event/policy evidence in `week4-evidence/http/` |
| API docs hidden in production | PASS | Maven MockMvc and HTTP smoke both confirm 404 for `/api-docs` and `/swagger-ui.html` |
| Browser live regression | M4/M5-owned | Not attributed to M1 |

## Final gate

M1's Windows preparation and server artifact checks are complete on the source
revision in the manifest. The artifact is proposed for review as the M1 Windows
candidate. Group-wide Week 4 readiness still requires M2/M3 dispositions and owner
sign-offs, plus credential rotation confirmation.
No Ubuntu E2E or full release PASS is asserted by this document.

## Completion against the Week 4 M1 checklist

Using a strict count where a Work ID is complete only when its full acceptance
condition is met, W4-M1-01 through W4-M1-05 are complete. W4-M1-06 has its M1
handoff review and candidate decision recorded, but remains incomplete until the
required cross-owner dispositions/sign-offs exist. W4-M1-07 remains open pending
environment-owner confirmation of credential rotation and Agent re-enrollment.
That is **5/6 P0 IDs = 83%** and **5/7 IDs overall = 71%**. This is M1 task progress;
it does not represent group release readiness.

# Member 1 — Week 1 Server Verification

Verified: 2026-10-12, Asia/Saigon. Environment: Windows 11, Maven 3.9.9,
JDK 22.0.2 compiling with Java release 21, Spring Boot 3.3.4.
Java 21 runtime / Ubuntu hardware verification remains separate.

## Findings and fixes

- Protocol example incorrectly marked M2's USB Optical Mouse as BLOCKED.
  Corrected to ALLOWED; sessionId is 21, matching the unchanged M2 sample.
- Contract test had a handwritten payload (session 2, BLOCKED). Its separate
  file-based test could silently pass when the sample was absent. Replaced with
  mandatory sample-file loading and assertions for all sample fields, including
  session 21, ALLOWED, interface 03:01:02 and the UTC-normalized timestamp.
- Added an HTTP integration test using a real embedded Tomcat on a random port.
  It verifies an H2 JDBC connection, heartbeat acceptance and endpoint persistence,
  event acceptance and persisted endpoint/user/session/decision/device data.
- Context-load test uses isolated in-memory H2 instead of the default application DB.
- Added `admin-server/verify-week1.ps1` for standalone server/Agent verification.
  Runtime files are confined to module target directories. Only processes started
  by the script are stopped in its finally block.

## Results

| Check | Result | Evidence |
|---|---|---|
| Server Maven package | PASS | [verified build log](evidence/server-verified-build.log) |
| Tests: 4 run, 0 failures, 0 errors, 0 skipped | PASS | [test summary](evidence/test-summary.txt) |
| Contract: actual M2 sample + UNKNOWN fallback | PASS | AgentEventContractTest |
| Real HTTP + H2 persisted data assertions | PASS | AgentHttpIntegrationTest |
| Standalone server boot, port 18080 | PASS | [server runtime log](evidence/server-runtime.log) |
| Standalone file H2 connection | PASS | Hikari connection to jdbc:h2:file:./target/week1-runtime/server-db |
| HTTP heartbeat and unchanged M2 event | PASS | [responses](evidence/poc-responses.json), persisted event #1 |
| Actual Agent JAR enrollment and heartbeat | PASS | [Agent runtime log](evidence/agent-runtime.log), server EP-0437347F |
| Actual Agent JAR simulated event delivery | PASS | Agent send-success + server persisted event #2 |
| Physical USB BLOCK / local user resolution on Ubuntu | NOT EXECUTED | Windows Agent uses simulation/mock user |

Initial sandbox execution could not open localhost sockets (`Permission denied`)
and was interrupted. The subsequent run outside the sandbox completed successfully.
`server-build.log` records that unsuccessful attempt; use `server-verified-build.log`
for the final result. Generated development security passwords in build evidence
are redacted.

## Reproduce from repository root

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
mvn -B -f admin-server/pom.xml package
mvn -B -f client-agent/pom.xml -DskipTests package
./admin-server/verify-week1.ps1 -JavaHome $env:JAVA_HOME
```

Use a compatible installed JDK (project targets Java 21). Agent packaging here
skips Agent tests; this report does not claim its full test suite passes.
HTTP checks require permission to bind/connect localhost. The script overwrites
runtime logs on each run and retains the isolated test database and Agent state
under target; later event IDs / endpoint IDs may differ.

## Gate assessment and remaining scope

The reported M1 evidence gaps are addressed: server boot, H2, real HTTP ingestion,
unchanged M2 contract and Agent-to-server PoC have reproducible evidence.
This supports M1's Week 1 skeleton/transport checks, not the entire project's
hardware Gate or production readiness.

Current SecurityConfig still permits `/api/agent/**`; issued Agent tokens are not
validated. Enrollment authentication and production Admin authentication remain
Week 2+ work. No claim of Agent authentication PASS is made.
Actual Agent heartbeat omits optional hostname/agentVersion; server logs them as
null and retains the enrolled hostname. The PoC verifies transport and persistence,
not policy enforcement, replay protection or service permissions on Ubuntu.

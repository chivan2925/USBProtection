# M5 Week 4 — M1 integration regression, 2026-11-01

Source **S4 = `c6cc1df21283588d70a792a37d42870bdea49497`**. Timezone: Asia/Bangkok. M1's freeze/security/docs/artifact and Windows candidate decision are received. This independently checks the updated backend and dependent HTTP/browser integration. M1's proposed artifact is not an accepted group RC/tag. S3 QA was committed `bf067ef` and merged `c07cb2f`; these S4 QA changes remain separate working-tree deliverables.

## Actual execution

| Capture | Command/scope | Actual result |
|---|---|---|
| [01-frontend-build](01-frontend-build.txt) / [metadata](01-frontend-build.json) | `npm run build` in `frontend` | **PASS**, TypeScript and React production assets |
| [02-server-package](02-server-package.txt) / [metadata](02-server-package.json) | `mvn -o -B -f admin-server/pom.xml -Pwith-frontend clean package` | **28/28 PASS**, 0 failures/errors/skips; executable Spring JAR with React built |
| [03-policy-http](03-policy-http.txt) / [metadata](03-policy-http.json) | `node --test tests/m4_policy_live.test.mjs` in `frontend` | **1/1 PASS**, real Spring/H2, duplicate Revoke/future ACK/A-B isolation |
| [04-browser-management](04-browser-management.txt) / [metadata](04-browser-management.json) | `node tests/browser.mjs --live` in `frontend` | **PASS, 9 screenshots**; auth/session, A/B Allow/Revoke, desired/applied, CSRF, JVM restart/H2 persistence and mobile |
| [05-browser-events](05-browser-events.txt) / [metadata](05-browser-events.json) | `node tests/browser-live.mjs` in `frontend` | **PASS, 8 screenshots**; 28 persisted synthetic events, filtering/paging/date, UNKNOWN/no-serial/user-switch/A-B and error recovery |
| [06-browser-packaged](06-browser-packaged.txt) / [metadata](06-browser-packaged.json) | Profiled `rehearse-week4.mjs --no-recording` | **PASS, 8 screenshots**; same JAR serves UI/API, all **31 original assertion lines** preserved, cleanup confirmed |
| [07-agent-server-http](07-agent-server-http.txt) / [metadata](07-agent-server-http.json) | Initial actual production Agent client → Spring attempt | **FAIL retained**, 27 assertions before heartbeat expectation stopped execution; ambient DEBUG also invalidated intended INFO log baseline |
| [08-agent-server-http](08-agent-server-http.txt) / [metadata](08-agent-server-http.json) | Corrected QA assertion/environment, same JARs/source | **PASS: 215 assertions, 64 scenarios / harness-initiated HTTP operations**, strict log minimization PASS |

All captures retain revision S4 and stable source-tree SHA-256 **`08dca0efb919680e7d5791c9de380dff275bf3d90ea0f2fb39edd08808d1bfe6`**. Exact executable/arguments/cwd, source/log hashes, start/end timestamps and exits are adjacent to each log. Backend clean package ran **20:55:17–20:56:10 +07:00** on 1 November 2026, using JDK 22.0.2 targeting Java 21. JDK 24 is not used because the pinned Lombok integration is unsupported there.

Capture 02's public log additionally redacts **26 key=value fixture credential values**, beyond the collector's JSON redaction. Its metadata explicitly retains `postCaptureRedaction`, the original output hash, sanitized output hash, replacement count and [redaction tool](../../../scripts/redact-week4-capture.py) hash. The original unredacted output is not backed up. Execution command, source, JUnit and verdict are unchanged; `outputSHA256` now identifies the sanitized log that is actually included.

Fresh M5 server JAR: **57,314,805 bytes**, SHA-256 **`0772ae2af64d855bc6f4c6c6ba08e311df6fa1792ecb6d0eb7944ee5a71d8ba5`**. The consumed S3 Agent JAR remains **20,186,115 bytes / `63255be52082dd8883b102633d8ec962b2d3e864a4204861b6b69cd8f532dd24`**. It was not rebuilt or substituted by a mocked HTTP client.

## Agent–Server and privacy scope

[Successful integration summary](agent-server-summary-02.json) records the exact built Agent and Server JARs, QA source hashes, sanitized launch commands, scenario/status records, source stability, log checks and server/port termination. The [Java harness](../../../scripts/AgentServerIntegrationSmoke.java) calls **`new AdminServerClient(AgentProperties)`** using the Agent's built classes/libraries against the **real packaged Spring Server in `prod` profile**. JDK HttpClient performs Admin operations and explicit status/boundary probes; the summary labels the two transports separately. Random bootstrap/Admin/DB credentials enter only via process environment. H2 and raw runtime logs remain in a fresh ignored `admin-server/target/qa-integration-*` directory; they are not copied into evidence.

Executed cases include actual Agent enroll/heartbeat/event/policy/ACK, Admin session/CSRF, token binding and spoof rejection, A/B Allow/Revoke, idempotency, desired/applied distinction, future/stale/negative ACK, event persistence/filter/detail/pagination and source-time precision. Boundary probes confirm UTF-8 token over 72 bytes → **401**, bootstrap 257 chars → **401**, enrollment IP 51 chars → **400**, username 100 accepted/101 rejected, UID zero/null accepted/negative rejected, and UNKNOWN/blank/absent username normalization. Rejected events do not become history rows. Production `/api-docs` and `/swagger-ui.html` return **404**.

The successful baseline removes inherited DEBUG/TRACE and explicitly sets them false. Server INFO acceptance records are present; unique synthetic username/session/serial/hash markers and synthetic secrets are absent from both INFO and other captured server log levels. Only counts and log hashes are backed up. Secure cookies are disabled for this isolated loopback HTTP exercise, so it does **not** prove production TLS/Secure-cookie browser behavior. Agent logging is independently disabled in this QA process; this log assertion concerns the Server. Full Agent scheduling, durable reconcile/save/ACK, outbox, USBGuard, native IPC/GUI and physical Ubuntu behavior are outside this test.

## Retained initial failure

[Attempt 07 summary](agent-server-summary.json) is preserved exactly. The first harness expected heartbeat response `status=ONLINE`; the frozen wire response is **`OK`**, while endpoint registry liveness is **`ONLINE`/`OFFLINE`**. The corrected test checks these separately. Attempt 07 also inherited the workstation's `DEBUG` setting: Spring debug request/response logging exposed synthetic test secrets in the **private ignored** runtime log and failed strict privacy checks. The intended production INFO baseline had not been isolated correctly. Attempt 08 removes that ambient setting, keeps all marker/secret assertions, and passes them. No app code, JAR or privacy assertion was weakened between attempts. This is not evidence that arbitrary DEBUG configuration is safe; final privacy proof is limited to the recorded INFO baseline.

## Handoff, source and browser provenance

[M5 M1 ACK](../../../evidence/M5_WEEK4_M1_ACK_20261101.md) records receipt of [M1 handoff](../../../../member1/WEEK4_SERVER_HANDOFF.md), freeze/security/docs and the owner-selected artifact. M1's manifest source **`d78189be42a5fa00a07e46446019c55fc547a592`** has identical Server/React source trees to S4. The owner's artifact **57,314,825 bytes / `43269264…`** and its 28-test/HTTP evidence retain their original identity. The fresh M5 build above is separately identified; differing build hashes do not establish a regression or prove byte identity with M1's submitted JAR.

Frontend, Agent and packaging Git trees match [S3](../m2-part3-regression/README.md). Historical **174 Agent tests**, staging **9/9**, M2/M3/M4 ACKs and controlled JDK-server smoke **255 assertions** remain dated evidence, not newly executed suites here. The backend tree changed, which is why current dependent browser/HTTP checks were rerun. Earlier [S0](../../2026-10-28/windows-regression/README.md), [S1](../../2026-10-30/post-pull-regression/README.md), [S2](../../2026-10-31/integrated-agent-packaging/README.md) and S3 raw evidence are unchanged.

There are **25 new screenshots**, scoped to captures 04/05/06. Two representative populated screens were visually inspected; they show synthetic QA identities and no credentials. [Packaged rehearsal provenance](recording/provenance.json) records the tracked harness, temporary ignored variant, wrapper/JAR hashes, reversible substitutions and assertion preservation. [Cleanup](recording/cleanup.json) confirms owned child termination; a taskkill race against an already exiting Chrome is retained with its confirmed exit. **Recording was disabled**: no new CDP frames, encoded video, audio or narrated walkthrough is claimed. The directory name `recording` is inherited from the rehearsal tool and contains provenance/cleanup only for this run.

## Evidence finalization

**Final evidence validation PASS**: seven successful captures, 28 server tests with no failures/errors/skips, 215 Agent–Server assertions, 25 screenshot hashes/dimensions and **5 React assets byte-equal inside the JAR**. The [run specification](run-spec.json) also requires the retained FAIL 07, reviewed source delta, owner-source comparison, tool identity and unchanged historical evidence. Outputs: [validation](validation.json), [JUnit summary](junit-summary.json), [build/assets manifest](build-manifest.json), [scope equivalence](scope-equivalence.json), [screenshots](screenshots.json), [environment](environment.json), [QA documents/tools](qa-documents.json) and [checksums](checksums.json). The [server integration validator](../../../scripts/validate-week4-server-integration.py) marks unsuccessful refreshes FAIL so a prior PASS cannot remain valid after an assertion/write error. Refresh final hashes after document edits:

```powershell
python docs/qa/scripts/validate-week4-server-integration.py --run-dir docs/qa/week4-evidence/2026-11-01/m1-integration-regression --baseline c6cc1df21283588d70a792a37d42870bdea49497 --profile docs/qa/week4-evidence/2026-11-01/m1-integration-regression/run-spec.json
```

Remaining dispositions: M1/group owner acceptance and deployment credential rotation confirmation; M2 external-review/contract/native confirmations; M3 P1 socket ACL/ownership/startup design, historical provenance and real `.deb` build. Canonical API wording that generally says oversized requests return 400 still needs M1 to distinguish oversized bootstrap/token auth rejection **401**. Ubuntu USB/USBGuard/GUI/IPC/systemd/reboot/install remain **NOT EXECUTED / DEFERRED**.

[Release report](../../../WEEK4_RELEASE_VALIDATION.md), [matrix](../../../WEEK4_TEST_INVENTORY.md), [Event History review](../../../WEEK4_EVENT_HISTORY_REVIEW.md), [demo](../../../../DEMO_SCRIPT.md), [Ubuntu acceptance](../../../WEEK4_UBUNTU_ACCEPTANCE.md).

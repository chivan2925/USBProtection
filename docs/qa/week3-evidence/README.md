# Week 3 evidence index — M5

## Current status — 2026-10-28

Branch `huy`; tested revision `39561907ef92f97234f449140bc394ddfe4f30e8`. [Inventory](../WEEK3_TEST_INVENTORY.md), [QA summary](../WEEK3_QA_SUMMARY.md), [M1 handoff](../../member1/WEEK3_SERVER_HARDENING.md), [M3 persistence closure](../WEEK3_M5_M3_PERSISTENCE_CLOSURE_20261028.md), [previous M3 follow-up](../WEEK3_M5_M3_FOLLOWUP_20261027.md).

- [M3 persistence/ACK closure](2026-10-28/m3-persistence-closure/README.md): Agent **127/127**, static verifier with fresh JAR PASS. M5-W3-07 is resolved for Windows unit/mock scope; [validation](2026-10-28/m3-persistence-closure/VALIDATION.md).
- [M3 follow-up evidence](2026-10-27/m3-followup/README.md): Agent **122/122**, staging verifier **3/3**, static verifier with JAR and four shell parsers PASS. M5-W3-06 is resolved; the later persistence closure supersedes its then-open M5-W3-07 status.
- [M1 handoff](../../member1/WEEK3_SERVER_HARDENING.md): H01 is acknowledged for Windows scope. It adds no executable source delta from the tested revision, so no test rerun is required; deployment secret rotation/re-enrollment remains external and **NOT VERIFIED**.
- [Integrated regression commands](2026-10-24/integrated-regression/README.md): previous frontend/server/browser baseline, still applicable because those sources did not change in the M3 pull. Frontend **96**, server **26**, Agent then **117**, separate policy HTTP **1**.
- [Environment](2026-10-24/integrated-regression/environment.json), [source manifest](2026-10-24/integrated-regression/sources/117de4465bd08ad8604fe940475e1699ba8cb539c060f6bfb8316f0c3b7b6fd6.json), [JUnit summary](2026-10-24/integrated-regression/junit-summary.json), [validation/artifacts](2026-10-24/integrated-regression/validation.json). Source SHA-256 `117de4465bd08ad8604fe940475e1699ba8cb539c060f6bfb8316f0c3b7b6fd6`.
- [Historical staging negative probe](2026-10-24/integrated-regression/staging-probe.json): recorded the defect that M3 resolved and M5 retested on 27/10.
- [Packaged browser provenance](2026-10-24/integrated-regression/packaged-browser-variant.json), [screenshot hashes/command attribution](2026-10-24/integrated-regression/screenshots.json), [desktop](2026-10-24/integrated-regression/screenshots/events-real-http-desktop.png), [partial username](2026-10-24/integrated-regression/screenshots/events-username-substring.png), [mobile](2026-10-24/integrated-regression/screenshots/events-real-http-mobile.png), [UNKNOWN/no-serial](2026-10-24/integrated-regression/screenshots/events-week3-unknown-no-serial.png), [session expiry](2026-10-24/integrated-regression/screenshots/m4-live-expired-session.png).

The 24/10 command captures each have a UTF-8 text log and adjacent JSON with argv/cwd/revision/start/end +07:00/scope/source manifest/exit; the 27/10 M3 follow-up has its scoped command/result index and validation record. Windows 11 amd64, JDK 22.0.2, Maven 3.9.11, Node 22.20.0, npm 10.9.3, Vite 8.3.1, Chrome. Synthetic HTTP/ACK, unit/mock/static and default-profile packaged browser results do not prove Ubuntu/USB/GUI/production HTTPS behavior. QA H2/runtime are isolated; application DB is not used.

**M5 Windows QA work complete; H01 M1 acknowledged for Windows scope; M5-W3-06 and M5-W3-07 resolved; H02 M4 DONE (owner acknowledged 2026-10-28); M3 package provenance and H05 group gate OPEN; U01–U09 NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4.** No `.deb` artifact/staging was found: NOT BUILT / NOT VERIFIED.

## Previous captures retained

[23/10 post-pull audit](2026-10-23/post-pull-audit/README.md), [23/10 documentation validation](2026-10-23/qa-closeout/validation.json), [23/10 handoff snapshot](../WEEK3_M5_HANDOFF_AND_STATUS_20261023.md) retain the earlier revision and Agent 61 result. All 21/10 raw logs, dated reports and Week 1/2 evidence remain unchanged.

---

## Historical snapshot — 2026-10-21

The following snapshot retains its original revision, counts and scope. Use the current section above for the integrated 2026-10-23 status.

Captured: 2026-10-21 (Asia/Bangkok). [Plan](../WEEK3_INTEGRATION_TEST_PLAN.md), [inventory](../WEEK3_TEST_INVENTORY.md), [QA summary](../WEEK3_QA_SUMMARY.md), [demo draft](../../DEMO_SCRIPT.md).

Later same-day follow-up: [Event filter/table name-search capture](2026-10-21/event-name-search/README.md), with 88 frontend tests, 18 server tests and new browser/build evidence. The initial capture below remains unchanged and identifies its earlier source snapshot.

Windows 11 amd64; Node 22.20.0 / npm 10.9.3; JDK 22.0.2; Maven 3.9.11; local Chrome. Branch `huy`, base `dee3cf7753521c653a79e24cc848df384bf7b639` plus working-tree changes. [Environment](2026-10-21/environment.json) records exact tools, initial source state and application DB hash. [Artifact manifest](2026-10-21/artifacts.json) records final source, built JARs/frontend, copied screenshots and unchanged application database.

Each command has a UTF-8 `.txt` log and same-stem `.json` metadata with argv/cwd, source manifest/hash, base revision, start/end in +07:00, scope and exit code. [Source manifests](2026-10-21/sources/) enumerate SHA-256 of tracked application/test/config inputs at command start. The final source tree hash is `85d505a3b6a358c2e0960976a5f16f9edb95c037e67895399f4c2cc22dd8cdfe`; the earlier frontend build has the same application/build inputs and earlier test-only source. [Final validation](2026-10-21/validation.json) records local-link checks, document hashes, source/build consistency and `git diff --check`. No new commit or owner signature is implied.

Latest same-day correction: [partial Linux User search](2026-10-21/username-substring/README.md). This has a separate source manifest and execution logs; previous captures remain historical.

### Executions

All dates below are 2026-10-21. Exact times are in the raw metadata. npm commands run from `frontend/`; Maven/package/shell commands run from repository root.

| Log | Actual command / scope | Exit / interpretation |
|---|---|---|
| [00](2026-10-21/00-final-page-reproduction.txt) | Targeted Node M5 tests inside sandbox | 1; three test-file launches blocked by spawn EPERM; not a functional test result |
| [01](2026-10-21/01-final-page-reproduction.txt) | Targeted M5 mapper/component/live-adapter tests before fix | 1; 34/36 PASS; two regression assertions reproduce excess final-page acceptance |
| [02](2026-10-21/02-npm-ci.txt) | `npm ci --offline --no-audit --no-fund` | 0; dependencies installed from lock/cache |
| [03](2026-10-21/03-frontend-lint.txt) | First `npm run lint` | 0; later final run is 11 |
| [04](2026-10-21/04-frontend-tests.txt) | First `npm test` after mapper/M5 additions | 0; 83/83; later final run is 12 |
| [05](2026-10-21/05-server-maven.txt) | `mvn -o -B -f admin-server/pom.xml clean package` | 1; missing cached Surefire JUnit provider, no executed server test result |
| [06](2026-10-21/06-agent-maven.txt) | `mvn -o -B -f client-agent/pom.xml clean package` | 1; same missing provider, no executed Agent test result |
| [07](2026-10-21/07-frontend-build.txt) | `npm run build` | **0**; TypeScript/Vite PASS; collector console preview then hit cp1252, fixed before later captures |
| [08](2026-10-21/08-server-maven.txt) | `mvn -B -f admin-server/pom.xml package` | 0; missing provider downloaded; **13 tests PASS**, fresh executable server JAR |
| [09](2026-10-21/09-browser-fixture.txt) | Initial `npm run test:browser` | 1; actual CRLF readiness migration failure before Chrome, resolved later |
| [10](2026-10-21/10-crlf-reproduction.txt) | `node --test tests/browser-readiness.test.mjs` before helper fix | 1; two CRLF regressions fail, four existing tests pass |
| [11](2026-10-21/11-frontend-lint.txt) | Final `npm run lint` | 0; PASS |
| [12](2026-10-21/12-frontend-tests.txt) | Final `npm test` | 0; **85/85 PASS**, no skipped/cancelled cases |
| [13](2026-10-21/13-agent-maven.txt) | `mvn -o -B -f client-agent/pom.xml package` | 0; **61 tests PASS**, fresh executable Agent JAR |
| [14](2026-10-21/14-browser-fixture.txt) | `npm run test:browser` after CRLF correction | 0; fixture/browser, auth, A/B, errors, demo/mobile PASS |
| [15](2026-10-21/15-shell-syntax.txt) | `bash -n build-deb.sh ...` | 0; bash parses **the first script only**; subsequent args are not extra script checks; remaining scripts checked individually in 17–19 |
| [16](2026-10-21/16-browser-management.txt) | `npm run test:live` | 0; real Spring Boot/H2, auth/CSRF, A/B Allow/Revoke, controlled ACK, actual server restart/session expiry PASS |
| [17](2026-10-21/17-shell-postinst.txt) | `bash -n packaging/agent-deb/DEBIAN/postinst` | 0; parsing only |
| [18](2026-10-21/18-shell-prerm.txt) | `bash -n packaging/agent-deb/DEBIAN/prerm` | 0; parsing only |
| [19](2026-10-21/19-shell-launcher.txt) | `bash -n packaging/desktop/usbshield-notifier-launcher.sh` | 0; parsing only |
| [20](2026-10-21/20-packaging-static.txt) | Existing Windows verifier with `-CheckBuiltJar` | 0; source/JAR static checks PASS |
| [21](2026-10-21/21-browser-events.txt) | `npm run test:browser:live` | 0; 25 baseline + 3 Week 3 synthetic HTTP events, no-serial/UNKNOWN/source time/A–B isolation/errors/recovery PASS |

### Screenshots from this run

Only captures whose timestamps fall in their successful browser run are copied into the new Week 3 directory. Historical `.artifacts` captures were not reused.

| Capture | Meaning / source |
|---|---|
| [Desktop Events](2026-10-21/screenshots/events-real-http-desktop.png) | Real server/H2, 25 initial synthetic HTTP records |
| [Date boundary](2026-10-21/screenshots/events-real-http-date-filter.png) | Stored day-end microsecond included; next midnight excluded |
| [Mobile Events](2026-10-21/screenshots/events-real-http-mobile.png) | Live Events at 375px |
| [UNKNOWN / no serial](2026-10-21/screenshots/events-week3-unknown-no-serial.png) | Persisted synthetic UNKNOWN event and original fractional source time |
| [Endpoint B](2026-10-21/screenshots/events-week3-endpoint-b.png) | Exact B history with B user |
| [Endpoint A](2026-10-21/screenshots/events-week3-endpoint-a.png) | Shared no-serial device filtered to A/new user and A/UNKNOWN |
| [Allow A](2026-10-21/screenshots/m4-live-allow-a.png), [Revoke A](2026-10-21/screenshots/m4-live-revoke-a.png) | Real policy mutations with distinct desired/applied versions |
| [Expired session](2026-10-21/screenshots/m4-live-expired-session.png) | Genuine backend restart expires previous session; protected rows absent |
| [Readonly demo](2026-10-21/screenshots/demo-events-mobile.png) | Explicit fixture/demo browser check at mobile size |

Browser runs use isolated temporary H2 and randomized test credentials. HTTP input/ACK and browser fault injection are explicitly synthetic. Existing application DB hash before/after matches. `.deb` binary is **NOT BUILT/NOT VERIFIED**; physical Ubuntu/USBGuard/session/IPC/GUI/systemd/install/reboot cases are **NOT EXECUTED / DEFERRED_UBUNTU until after Week 4**. M1–M4 review and the group integrated gate remain open.

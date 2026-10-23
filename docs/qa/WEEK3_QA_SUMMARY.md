# Week 3 QA summary — M5

## Current status — 2026-10-24

Branch `huy`; tested revision `a0ad421901d5cb67fbe1b27ea9a74c05dfe2c3c1`. [Inventory](WEEK3_TEST_INVENTORY.md), [M5 completion / owner handoff](WEEK3_M5_COMPLETION_20261024.md), [execution index](week3-evidence/2026-10-24/integrated-regression/README.md), [demo draft](../DEMO_SCRIPT.md).

**M5 Windows testing and QA documentation complete. Events/application regressions PASS; M3 staging verifier has one reproduced FAIL (M5-W3-06). Group Week 3 gate OPEN. Ubuntu U01–U09 NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4.** No new human owner acceptance or final release decision is recorded.

### Fresh results on the integrated revision

| Scope | Actual result | Evidence |
|---|---|---|
| Locked install, lint, TypeScript/Vite build | PASS; Vite 8.3.1 | [01 install](week3-evidence/2026-10-24/integrated-regression/01-npm-ci.txt), [12 lint](week3-evidence/2026-10-24/integrated-regression/12-frontend-lint.txt), [13 build](week3-evidence/2026-10-24/integrated-regression/13-frontend-build.txt) |
| Frontend M4/M5 | **96/96 PASS**, no failures/skips/cancellations | [11](week3-evidence/2026-10-24/integrated-regression/11-frontend-tests.txt) |
| Agent M2/M3 and executable JAR | **117 PASS**, 0 failures/errors/skips | [02](week3-evidence/2026-10-24/integrated-regression/02-agent-tests-package.txt), [JUnit cases/counts](week3-evidence/2026-10-24/integrated-regression/junit-summary.json) |
| Server + bundled React JAR | **26 PASS**, 0 failures/errors/skips | [14](week3-evidence/2026-10-24/integrated-regression/14-server-with-frontend.txt) |
| Fixture / management / Events browser | All PASS; real Spring Boot/H2, 28 HTTP-seeded Events, A/B, auth/error/recovery/mobile, real JVM restart | [16](week3-evidence/2026-10-24/integrated-regression/16-browser-fixture.txt), [18](week3-evidence/2026-10-24/integrated-regression/18-browser-management.txt), [20](week3-evidence/2026-10-24/integrated-regression/20-browser-events.txt) |
| Standalone policy HTTP | **1/1 PASS**, separate from the 96 unit tests | [19](week3-evidence/2026-10-24/integrated-regression/19-policy-http-contract.txt) |
| Browser using packaged JAR origin | PASS; unchanged M5 assertions, no Vite; HTTP/default runtime, not production HTTPS | [21](week3-evidence/2026-10-24/integrated-regression/21-browser-packaged-jar.txt), [variant](week3-evidence/2026-10-24/integrated-regression/packaged-browser-variant.json) |
| Week 3 static verifier / four shell syntax checks | PASS for existing source/archive assertions and four individually parsed scripts | [15](week3-evidence/2026-10-24/integrated-regression/15-packaging-static.txt), [07–10 index](week3-evidence/2026-10-24/integrated-regression/README.md) |
| Negative staging probe (W3-P03) | **FAIL — M5-W3-06**, despite process exit 0 | [17 log](week3-evidence/2026-10-24/integrated-regression/17-staging-negative-probe.txt), [absent-staging precondition/result](week3-evidence/2026-10-24/integrated-regression/staging-probe.json) |

21 command attempts were captured: 16 successful positive runs, four Git Bash launch failures at the Windows sandbox boundary resolved by permitted retries, and one negative probe revealing a verifier defect. Failed launch logs 03–06 are retained; retries 07–10 are the actual shell-parse PASS evidence. Exit 0 of probe 17 does not establish valid staging.

Windows 11 amd64; JDK 22.0.2; Maven 3.9.11; Node 22.20.0; npm 10.9.3; Chrome. [Environment](week3-evidence/2026-10-24/integrated-regression/environment.json), [source manifest](week3-evidence/2026-10-24/integrated-regression/sources/117de4465bd08ad8604fe940475e1699ba8cb539c060f6bfb8316f0c3b7b6fd6.json), [artifact/source/link validation](week3-evidence/2026-10-24/integrated-regression/validation.json). Source SHA-256 `117de4465bd08ad8604fe940475e1699ba8cb539c060f6bfb8316f0c3b7b6fd6`. Runtime/H2 were isolated; the operator application DB was not read/reset/used. Actual USBGuard, physical USB, Linux sessions, native GUI, systemd and install/reboot were not executed.

### M5 completion and remaining owner work

M5 Events source/tests and server/frontend remain unchanged by the latest pull; M2 core/session/notification changes and M3 tests/report/verifier were integrated. This run verifies them independently on `a0ad421` without editing application/test/packaging source. Current QA docs, evidence index, demo draft and owner report are updated. [23/10 snapshot](WEEK3_M5_HANDOFF_AND_STATUS_20261023.md) retains its original revision and 61-test Agent result; new results do not overwrite it.

| Item | Current status | Next owner action |
|---|---|---|
| M5 Event UI/contracts/regression/evidence/docs/demo draft | **DONE for feasible Windows scope** | Retest affected areas when an owner changes source; Week 4 final guides/rehearsal remain scheduled separately. |
| H01 M1 / H02 M4 | Technical tests/review PASS; owner acknowledgment not newly recorded | Owners confirm integrated contract/shared UI and handoff. |
| H03 M2 core/session | **DONE for M5 review/execution**; report available and six new suites/48 tests PASS in real Maven | No longer waiting for source/report; hardware acceptance stays deferred. |
| H04 M3 retry/packaging | Report received; existing Agent/static assertions PASS; **OPEN** issues below | Fix/triage M5-W3-06, resolve coverage/lifecycle/document/build-provenance questions. |
| H05 M1 + group | **OPEN** | Record integrated revision, disposition of Windows P0/issues and group decision. |
| U01–U09 | **NOT EXECUTED / DEFERRED_UBUNTU** | Actual execution after Week 4. |

### Open finding and review questions

**M5-W3-06 — OPEN, implementation owner M3.** Requested `-CheckStaging` with no `packaging/staging` directory; the script exited 0 and printed PASS. A missing-file branch also only warns. M3 must return failure or explicit unverified status for requested incomplete staging. M5 retest criteria: absent directory and missing required file reject; a valid staging passes. This is a Windows verifier defect, not a reproduced Ubuntu installer failure.

M3 review also remains open for unasserted outbox FIFO order, disk-write failure/restart/network-flap coverage, ignored service-start failures and upgrade semantics in unchanged maintainer scripts, report file mode 750 versus builder mode 640, and `.deb`/staging provenance. Reports/code are present; do not keep these items labeled missing handoff. Detailed assertions, source links and closure criteria are in the [completion report](WEEK3_M5_COMPLETION_20261024.md). No `.deb` artifact/staging exists in the reviewed workspace: **NOT BUILT / NOT VERIFIED**.

A packaging script/report PASS label does not constitute group acceptance. The allowed eventual group label remains `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`, after owner decisions; FULL PASS is not claimed.

---

## Historical snapshot — 2026-10-21

The following snapshot retains its original revision, counts and scope. Use the current section above for the integrated 2026-10-23 status.

Updated: 2026-10-21 (Asia/Bangkok). Branch `huy`, working tree based on `dee3cf7753521c653a79e24cc848df384bf7b639`. [Plan](WEEK3_INTEGRATION_TEST_PLAN.md), [inventory](WEEK3_TEST_INVENTORY.md), [evidence/provenance](week3-evidence/README.md), [demo draft](../DEMO_SCRIPT.md).

**M5 independent Week 3 work: implementation ready and Windows regression PASS. Group Week 3 acceptance: OPEN, pending owners/integrated revision. Ubuntu E2E: NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4.** This report is a fresh Windows run, not a reuse of Week 2 results or a final release declaration.

**Later follow-up, 2026-10-21:** [Event filter/table alignment](WEEK3_EVENT_NAME_SEARCH_FIX.md) adds endpoint/USB name-or-ID search with server-side pagination and aligned labels. New results: **88 frontend tests**, **18 server tests**, lint/build and separate browser captures. The initial 85/13 results below remain historical for their original source. This follow-up includes an additive M1-owned Event API change requiring integration review.

The subsequent [Linux User substring correction](week3-evidence/2026-10-21/username-substring/README.md) changes live user search to case-insensitive partial matching via `username`; legacy exact `linuxUsername` is preserved. Its separate evidence records the new source, 20 server tests and 88 frontend tests. Earlier 18/13 server results retain their historical scopes.

### Changes and reviewed behavior

- Reviewed existing Event History UI, source/type mapping, filters, shared resource/auth integration and loading/empty/error behavior. Existing behavior was retained where tests already demonstrated the requirement.
- Fixed M5 `mapEventPage`: an excess final-page row now rejects the entire response. Before the fix, page 1 / size 2 / total 3 could accept two rows despite only one record remaining. Valid partial/out-of-range empty pages retain server metadata/order.
- Added seven M5 unit/component/contract regressions for the above failure, no-serial hash/identity without fabricated serial, historical UNKNOWN/user/endpoint attribution, escaped display/encoded endpoint/source precision, final server page display and query pagination boundaries. Baseline 76 frontend tests became 83.
- Fixed the shared readiness migration's Windows CRLF handling after the actual browser command failed before Chrome startup. Already migrated CRLF input is recognized without rewriting it; migration preserves the input line endings. Two additional regressions bring the final suite to **85 tests**. Shared M4/M5 test infrastructure requires M4 review at handoff; no production auth/management code changed.
- Extended M5 real browser Events regression with three synthetic no-serial HTTP events: A/UNKNOWN, A/new user and B/other user. On top of 25 baseline records, 28 persisted records verify exact user/device/endpoint filters and A/B isolation. This is not a real Linux user switch or USBGuard run.
- Created Week 3 plan, inventory, QA report, evidence index and demo draft; appended the Week 3 scope to `TEST_PLAN.md`. Week 1/2 reports/logs remain unchanged.

### Fresh Windows results

| Command / scope | Result | Evidence |
|---|---|---|
| `npm ci --offline --no-audit --no-fund` | PASS, exit 0; locked dependencies installed | [Install](week3-evidence/2026-10-21/02-npm-ci.txt) |
| `npm run lint` after final harness correction | PASS, exit 0 | [Lint](week3-evidence/2026-10-21/11-frontend-lint.txt) |
| `npm test` | PASS, **85/85**, no failures/skips/cancellations | [Tests](week3-evidence/2026-10-21/12-frontend-tests.txt) |
| `npm run build` | PASS, exit 0; TypeScript + Vite production output | [Build](week3-evidence/2026-10-21/07-frontend-build.txt) |
| `npm run test:browser` | PASS, fixture/browser; protected routes, auth, A/B, errors, demo/mobile | [Fixture browser](week3-evidence/2026-10-21/14-browser-fixture.txt) |
| `npm run test:live` | PASS, real isolated Spring Boot/H2; session/CSRF, A/B desired/applied, controlled ACK and actual JVM restart | [Management browser](week3-evidence/2026-10-21/16-browser-management.txt) |
| `npm run test:browser:live` | PASS, real isolated Spring Boot/H2; 28 HTTP-seeded events, filters/pages/dates, UNKNOWN/no-serial, A/B, injected failures/recovery/mobile | [Events browser](week3-evidence/2026-10-21/21-browser-events.txt) |
| Server Maven `package` after clean/offline attempt | PASS, **13 tests**, 0 failures/errors/skips; executable JAR built | [Server](week3-evidence/2026-10-21/08-server-maven.txt) |
| Agent Maven `package` after clean/offline attempt | PASS, **61 tests**, 0 failures/errors/skips; executable JAR built | [Agent](week3-evidence/2026-10-21/13-agent-maven.txt) |
| Existing Windows package verifier with `-CheckBuiltJar` | PASS, static service/live config/helper loader/class entries | [Packaging](week3-evidence/2026-10-21/20-packaging-static.txt) |
| Four individual Git Bash syntax checks | PASS, shell parsing only | [Builder](week3-evidence/2026-10-21/15-shell-syntax.txt), [postinst](week3-evidence/2026-10-21/17-shell-postinst.txt), [prerm](week3-evidence/2026-10-21/18-shell-prerm.txt), [launcher](week3-evidence/2026-10-21/19-shell-launcher.txt) |

Server and Agent Java source were not edited in the initial execution above; the later name-search follow-up narrowly extends the server Event API as documented separately. These suites were freshly executed by M5/Codex on Windows, without attributing new results to another human owner. Browser suites ran sequentially on isolated runtime data. The original application H2 database SHA-256 was unchanged during the initial capture; the later capture does not read or hash the user's actively locked database. Source/build/screenshot provenance is in the evidence index. The initial frontend build happened before the final test-only readiness correction; application/build source remained identical, so rebuilding that unchanged bundle was unnecessary. The subsequent UI/name-query change has its own fresh build.

### Failed attempts and resolved issues

| ID | State | Finding / disposition |
|---|---|---|
| M5-W3-01 | FIXED, regression PASS | Final-page content could exceed remaining total; [before-fix run](week3-evidence/2026-10-21/01-final-page-reproduction.txt) has 34/36 PASS and two expected missing-rejection failures; final 85-test run passes |
| M5-W3-02 | FIXED, unit and browser PASS | CRLF checkout caused the npm pre-browser readiness migration to reject already fixed source; [actual browser failure](week3-evidence/2026-10-21/09-browser-fixture.txt), [two failing regressions](week3-evidence/2026-10-21/10-crlf-reproduction.txt); final fixture/management browsers and six readiness tests pass |
| M5-W3-03 | RESOLVED, environment | Initial sandbox test runner could not spawn child processes (`EPERM`); [attempt](week3-evidence/2026-10-21/00-final-page-reproduction.txt) is not a product regression. The required process-spawning checks were rerun with execution permission |
| M5-W3-04 | RESOLVED, dependency cache | Offline server/Agent Maven attempts could not resolve JUnit Surefire provider 3.2.5; [server attempt](week3-evidence/2026-10-21/05-server-maven.txt), [Agent attempt](week3-evidence/2026-10-21/06-agent-maven.txt). Online server retry downloaded it; both real test suites/builds then passed |
| M5-W3-05 | RESOLVED, capture-only | The build command exited 0 and its full UTF-8 output/metadata were saved; the collector console preview subsequently hit cp1252 on the checkmark. Collector output was set to UTF-8 before subsequent commands; build success is established by the raw command result |

No unresolved functional failure remains in the independently executed M5 Windows scope. This statement does not imply that all owners' Week 3 requirements are implemented or accepted.

### Owner handoff and remaining group work

| Owner | Outstanding review/input | Why group acceptance remains open |
|---|---|---|
| M1 | Review the additive Event name-search contract; confirm other canonical contracts unchanged or provide delta; accept Week 3 security/status/policy hardening, deployment profile and React embedded in server JAR | Initial 13-test regression, subsequent 18-test name-search regression and Vite-proxied browser PASS do not prove all new M1 gates or embedded-SPA serving |
| M2 | Review Week 3 no-serial/parser/reconnect/session fixtures and hardening coverage; identify missing edge cases | Existing mocked core/session suite passes; additional Week 3 implementation acceptance is not inferred |
| M3 | Review restart/network-flap/disk-failure/pending ACK coverage; complete `.deb` metadata/lifecycle/staging/build provenance | Existing 61-test suite/static checks pass; `.deb` binary is **NOT BUILT/NOT VERIFIED** in this capture |
| M4 | Review CRLF readiness helper/tests in shared test infrastructure; confirm shared auth/management UI acceptance on the integrated revision | Technical regression passed; no human M4 acceptance is fabricated, including the historical Week 2 acknowledgment |
| M1 + all owners | Select merged/integrated revision, rerun checks affected by subsequent changes and review remaining Windows P0 blockers | M5's working-tree completion is not group acceptance or a merge |
| M2/M3/M5 with M1/M4 | Execute W3-U01–U09 after Week 4 and correlate physical/runtime evidence | Ubuntu/USBGuard/physical USB/native GUI/install/reboot execution is deferred |

No messages were sent to other members. These are concrete handoff items for review, not requests for permission to begin M5 work. When all Windows P0 criteria are proven, M1 and the team may record `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`; `WEEK 3 — FULL PASS` remains unsupported.

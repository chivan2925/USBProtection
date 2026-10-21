# Week 3 QA summary — M5

Updated: 2026-10-21 (Asia/Bangkok). Branch `huy`, working tree based on `dee3cf7753521c653a79e24cc848df384bf7b639`. [Plan](WEEK3_INTEGRATION_TEST_PLAN.md), [inventory](WEEK3_TEST_INVENTORY.md), [evidence/provenance](week3-evidence/README.md), [demo draft](../DEMO_SCRIPT.md).

**M5 independent Week 3 work: implementation ready and Windows regression PASS. Group Week 3 acceptance: OPEN, pending owners/integrated revision. Ubuntu E2E: NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4.** This report is a fresh Windows run, not a reuse of Week 2 results or a final release declaration.

**Later follow-up, 2026-10-21:** [Event filter/table alignment](WEEK3_EVENT_NAME_SEARCH_FIX.md) adds endpoint/USB name-or-ID search with server-side pagination and aligned labels. New results: **88 frontend tests**, **18 server tests**, lint/build and separate browser captures. The initial 85/13 results below remain historical for their original source. This follow-up includes an additive M1-owned Event API change requiring integration review.

The subsequent [Linux User substring correction](week3-evidence/2026-10-21/username-substring/README.md) changes live user search to case-insensitive partial matching via `username`; legacy exact `linuxUsername` is preserved. Its separate evidence records the new source, 20 server tests and 88 frontend tests. Earlier 18/13 server results retain their historical scopes.

## Changes and reviewed behavior

- Reviewed existing Event History UI, source/type mapping, filters, shared resource/auth integration and loading/empty/error behavior. Existing behavior was retained where tests already demonstrated the requirement.
- Fixed M5 `mapEventPage`: an excess final-page row now rejects the entire response. Before the fix, page 1 / size 2 / total 3 could accept two rows despite only one record remaining. Valid partial/out-of-range empty pages retain server metadata/order.
- Added seven M5 unit/component/contract regressions for the above failure, no-serial hash/identity without fabricated serial, historical UNKNOWN/user/endpoint attribution, escaped display/encoded endpoint/source precision, final server page display and query pagination boundaries. Baseline 76 frontend tests became 83.
- Fixed the shared readiness migration's Windows CRLF handling after the actual browser command failed before Chrome startup. Already migrated CRLF input is recognized without rewriting it; migration preserves the input line endings. Two additional regressions bring the final suite to **85 tests**. Shared M4/M5 test infrastructure requires M4 review at handoff; no production auth/management code changed.
- Extended M5 real browser Events regression with three synthetic no-serial HTTP events: A/UNKNOWN, A/new user and B/other user. On top of 25 baseline records, 28 persisted records verify exact user/device/endpoint filters and A/B isolation. This is not a real Linux user switch or USBGuard run.
- Created Week 3 plan, inventory, QA report, evidence index and demo draft; appended the Week 3 scope to `TEST_PLAN.md`. Week 1/2 reports/logs remain unchanged.

## Fresh Windows results

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

## Failed attempts and resolved issues

| ID | State | Finding / disposition |
|---|---|---|
| M5-W3-01 | FIXED, regression PASS | Final-page content could exceed remaining total; [before-fix run](week3-evidence/2026-10-21/01-final-page-reproduction.txt) has 34/36 PASS and two expected missing-rejection failures; final 85-test run passes |
| M5-W3-02 | FIXED, unit and browser PASS | CRLF checkout caused the npm pre-browser readiness migration to reject already fixed source; [actual browser failure](week3-evidence/2026-10-21/09-browser-fixture.txt), [two failing regressions](week3-evidence/2026-10-21/10-crlf-reproduction.txt); final fixture/management browsers and six readiness tests pass |
| M5-W3-03 | RESOLVED, environment | Initial sandbox test runner could not spawn child processes (`EPERM`); [attempt](week3-evidence/2026-10-21/00-final-page-reproduction.txt) is not a product regression. The required process-spawning checks were rerun with execution permission |
| M5-W3-04 | RESOLVED, dependency cache | Offline server/Agent Maven attempts could not resolve JUnit Surefire provider 3.2.5; [server attempt](week3-evidence/2026-10-21/05-server-maven.txt), [Agent attempt](week3-evidence/2026-10-21/06-agent-maven.txt). Online server retry downloaded it; both real test suites/builds then passed |
| M5-W3-05 | RESOLVED, capture-only | The build command exited 0 and its full UTF-8 output/metadata were saved; the collector console preview subsequently hit cp1252 on the checkmark. Collector output was set to UTF-8 before subsequent commands; build success is established by the raw command result |

No unresolved functional failure remains in the independently executed M5 Windows scope. This statement does not imply that all owners' Week 3 requirements are implemented or accepted.

## Owner handoff and remaining group work

| Owner | Outstanding review/input | Why group acceptance remains open |
|---|---|---|
| M1 | Review the additive Event name-search contract; confirm other canonical contracts unchanged or provide delta; accept Week 3 security/status/policy hardening, deployment profile and React embedded in server JAR | Initial 13-test regression, subsequent 18-test name-search regression and Vite-proxied browser PASS do not prove all new M1 gates or embedded-SPA serving |
| M2 | Review Week 3 no-serial/parser/reconnect/session fixtures and hardening coverage; identify missing edge cases | Existing mocked core/session suite passes; additional Week 3 implementation acceptance is not inferred |
| M3 | Review restart/network-flap/disk-failure/pending ACK coverage; complete `.deb` metadata/lifecycle/staging/build provenance | Existing 61-test suite/static checks pass; `.deb` binary is **NOT BUILT/NOT VERIFIED** in this capture |
| M4 | Review CRLF readiness helper/tests in shared test infrastructure; confirm shared auth/management UI acceptance on the integrated revision | Technical regression passed; no human M4 acceptance is fabricated, including the historical Week 2 acknowledgment |
| M1 + all owners | Select merged/integrated revision, rerun checks affected by subsequent changes and review remaining Windows P0 blockers | M5's working-tree completion is not group acceptance or a merge |
| M2/M3/M5 with M1/M4 | Execute W3-U01–U09 after Week 4 and correlate physical/runtime evidence | Ubuntu/USBGuard/physical USB/native GUI/install/reboot execution is deferred |

No messages were sent to other members. These are concrete handoff items for review, not requests for permission to begin M5 work. When all Windows P0 criteria are proven, M1 and the team may record `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`; `WEEK 3 — FULL PASS` remains unsupported.

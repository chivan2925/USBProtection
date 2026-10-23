# Week 3 QA summary — M5

## Current status — 2026-10-23

Branch `huy`; tested revision `8c222a47142d7a80f75e56b8d77e5bf732797e16`. [Current inventory](WEEK3_TEST_INVENTORY.md), [completed work / handoff / remaining tasks](WEEK3_M5_HANDOFF_AND_STATUS_20261023.md), [evidence index](week3-evidence/README.md), [demo draft](../DEMO_SCRIPT.md).

**M5 implementation ready; current Windows regression PASS; QA documentation aligned. Group Week 3 gate: OPEN. Ubuntu E2E: NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4.** Owner review and the group decision have not been recorded. The historical section below retains the 21/10 revision/results and is superseded here for current status.

### Verified integrated results

The same-day [post-pull audit](week3-evidence/2026-10-23/post-pull-audit/README.md) executed **16 commands, all exit 0**, against the revision above. Windows 11 amd64; JDK 22.0.2; Maven 3.9.11; Node 22.20.0; npm 10.9.3; locked Vite 8.3.1; Chrome. [Environment](week3-evidence/2026-10-23/post-pull-audit/environment.json), [source manifest](week3-evidence/2026-10-23/post-pull-audit/sources/e16a567bf1cd754d48d43c9e69c7c58a793695c91273e751d585fd30f631de4d.json). Source tree SHA-256: `e16a567bf1cd754d48d43c9e69c7c58a793695c91273e751d585fd30f631de4d`.

| Scope | Actual result | Evidence |
|---|---|---|
| Locked dependency install, lint and frontend build | PASS | [Install](week3-evidence/2026-10-23/post-pull-audit/01-npm-ci.txt), [lint](week3-evidence/2026-10-23/post-pull-audit/03-frontend-lint.txt), [build](week3-evidence/2026-10-23/post-pull-audit/04-frontend-build.txt) |
| Frontend regression | **96/96 PASS**, no failures/skips/cancellations | [Unit/component/API tests](week3-evidence/2026-10-23/post-pull-audit/02-frontend-tests.txt) |
| Server tests and React-in-JAR packaging | **26 PASS**, 0 failures/errors/skips; combined JAR built | [Maven with-frontend](week3-evidence/2026-10-23/post-pull-audit/06-server-with-frontend.txt) |
| Agent tests and packaging | **61 PASS**, 0 failures/errors/skips; Agent JAR built | [Maven](week3-evidence/2026-10-23/post-pull-audit/05-agent-tests-package.txt) |
| Fixture, management and Events browser | All PASS; Events uses 28 synthetic HTTP-seeded records; genuine server restart/session recovery covered | [Fixture](week3-evidence/2026-10-23/post-pull-audit/07-browser-fixture.txt), [management](week3-evidence/2026-10-23/post-pull-audit/08-browser-management.txt), [Events](week3-evidence/2026-10-23/post-pull-audit/09-browser-events.txt) |
| Standalone policy HTTP contract | **1/1 PASS**, separate from npm test; duplicate Revoke/future ACK/B isolation | [Policy](week3-evidence/2026-10-23/post-pull-audit/10-policy-http-contract.txt) |
| Bundled-JAR browser | PASS; all original M5 Events assertions with UI/API at the JAR origin | [Browser](week3-evidence/2026-10-23/post-pull-audit/12-browser-packaged-jar.txt), [variant provenance](week3-evidence/2026-10-23/post-pull-audit/packaged-browser-variant.json) |
| Existing packaging static verifier | PASS with built JAR; source/archive scope | [Static](week3-evidence/2026-10-23/post-pull-audit/11-packaging-static.txt) |
| Individual shell syntax checks | Four PASS; parsing only | [Builder](week3-evidence/2026-10-23/post-pull-audit/13-shell-builder.txt), [postinst](week3-evidence/2026-10-23/post-pull-audit/14-shell-postinst.txt), [prerm](week3-evidence/2026-10-23/post-pull-audit/15-shell-prerm.txt), [launcher](week3-evidence/2026-10-23/post-pull-audit/16-shell-launcher.txt) |

The bundled-JAR browser uses HTTP/default runtime settings; production defaults are tested separately by the server suite and described in [M1's report](../member1/WEEK3_SERVER_HARDENING.md). It does not demonstrate production HTTPS. Audit [validation](week3-evidence/2026-10-23/post-pull-audit/validation.json) matches all five bundled assets to the frontend build and records source/artifact/screenshot hashes. No `.deb` binary/build provenance was found; clean install is unexecuted. Existing Agent tests use mocks/fixtures; HTTP ACKs are synthetic input and do not prove USBGuard apply.

### Work completed and remaining owner dependencies

M5's Events implementation/tests remain unchanged since the final 21/10 capture. Partial endpoint/USB names or IDs and case-insensitive partial usernames are verified on the pulled revision; legacy exact API parameters remain available. M1 production-profile/static-SPA implementation and M4 UI/auth/mapping hardening are now present and passed current integrated checks. Their older reports retain their own revisions/counts; this capture records 26/96 for the current source.

This documentation update synchronized the primary inventory, QA summary and evidence index, updated plan/demo wording, prepared M1/M4 review packets and reviewed M2/M3 coverage plus package source. It changes no application/test code and does not rerun application suites. [Documentation validation](week3-evidence/2026-10-23/qa-closeout/validation.json) checks source consistency, historical evidence preservation and local links. The [earlier post-pull review](WEEK3_M5_POST_PULL_REVIEW_20261023.md) is preserved as the snapshot that identified these documentation gaps.

| Owner / item | Current assessment | Remaining action |
|---|---|---|
| M5 independent implementation/Windows QA/docs | READY / tested scopes PASS | Await owner inputs; update affected results if source changes. |
| M1 server/contract/JAR (H01) | Implementation/report available; current tests/build/same-origin browser PASS | Confirm Event query/legacy compatibility and integrated server/profile handoff. |
| M4 shared UI/auth/browser (H02) | Implementation/report available; current frontend/browser/policy checks PASS | Confirm shared integration review; no new owner acknowledgment recorded. |
| M2 core/session (H03) | Existing mock/unit assertions PASS; no dedicated Week 3 handoff found | Map no-serial storage/reconnect/session-switch/remote-only requirements to evidence or additional owner work. |
| M3 retry/outbox/packaging (H04) | Existing assertions/JAR/static/syntax PASS; pending-ACK reload already covered | Confirm FIFO/fault/restart coverage, IPC/lifecycle/source-review questions and package build status/provenance. |
| M1 + owners group gate (H05) | **OPEN** | Identify integrated `main` revision, close/review Windows P0 coverage and record group decision. |
| Ubuntu U01–U09 | **NOT EXECUTED / DEFERRED_UBUNTU** | Run actual USBGuard/USB/session/GUI/service/install/reboot cases after Week 4. |

No dedicated Week 3 M2/M3 report is evidence of an incomplete handoff, not proof every associated implementation is missing. The [handoff matrix and prepared gate worksheet](WEEK3_M5_HANDOFF_AND_STATUS_20261023.md) specify the reviewed assertions, exact open questions, owners and closure criteria. No newly reproduced application failure is claimed by this documentation review; coverage/owner questions remain open. The group gate cannot be inferred from passing existing suites alone.

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

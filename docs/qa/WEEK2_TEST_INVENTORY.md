# Week 2 execution inventory

Owner: M5. Updated: 2026-10-16 (Asia/Bangkok). [Plan/steps](WEEK2_INTEGRATION_TEST_PLAN.md). [QA summary](WEEK2_QA_SUMMARY.md).

Current Windows live Events run: `huy` working tree based on `cd81060b8f5f32e4ffa42e9120e2827d92219204`; [source manifest](week2-evidence/15-live-events-source-state.txt) and [verification/logs](week2-evidence/M5_LIVE_EVENTS_VERIFICATION.md). Tests (60/60), lint, build, browser regression and actual Spring Boot/H2/Chromium integration PASS. Data source is synthetic authenticated HTTP in an isolated DB. Ubuntu testing remains deferred/NOT EXECUTED. The [earlier 52-test mapper-only run](week2-evidence/M5_EVENT_CONTRACT_VERIFICATION.md) retains its own scope and manifest.

Historical 2026-10-13 runs used the `huy` worktree based on `8b644eafc7aa3651b92e7a6746f4f22a4f1ba65e`, identified by the [original manifest](week2-evidence/05-source-state.txt). Exact timestamps/exit codes remain in the original logs. Canonical rows below track complete scenarios; reviewed M1-only evidence does not imply Agent/browser/Ubuntu execution by M5.

| Test ID | Owner | Environment | Git revision | Date/time | Commands/steps | Expected | Actual | Status | Evidence | Known limitation | Follow-up owner |
|---|---|---|---|---|---|---|---|---|---|---|---|
| W2-T01 | M1/M3 | Ubuntu/HTTP required | — | — | Plan W2-T01 | Authenticated enrollment/heartbeat | Actual Agent not run; M1 HTTP evidence available | NOT EXECUTED | M1 refs below | Actual Agent runtime pending; Ubuntu deferred | M1/M3 |
| W2-T02 | M2/M3/M1 | Ubuntu/physical USB required | — | — | Plan W2-T02 | Real BLOCK and correct persisted attribution | Not run | NOT EXECUTED | — | Physical integration environment pending | M2/M3/M1 |
| W2-T03 | M5/M1/M4 | Physical source + API/browser required | — | — | Plan W2-T03 | Physical source event rendered accurately | Windows persisted HTTP → Web portion PASS; physical source deferred | NOT EXECUTED | [Windows sub-run](week2-evidence/13-live-events-browser-http.txt) | Physical USB source/correlation not executed; live adapter now integrated | M2/M3/M5 |
| W2-T04 | M4/M1 | Windows/Java/H2/Chromium | `cd81060` + working tree | 2026-10-16; +07:00 log | Real invalid/valid login, reload/session, logout then protected read | Real auth/session/unauthorized behavior | Actual browser session/CSRF lifecycle PASS | PASS | [Real HTTP/browser](week2-evidence/13-live-events-browser-http.txt) | Isolated QA account/DB; no physical USB claim | M4/M5 |
| W2-T05 | M4/M1 | HTTP/DB/browser required | — | — | Plan W2-T05 | Allow changes A only | Browser not run; M1 A/B HTTP evidence available | NOT EXECUTED | M1 refs below | M4 Allow UI/API integration pending | M4/M5 |
| W2-T06 | M3/M2 | Ubuntu/A–B required | — | — | Plan W2-T06 | Actual policy ALLOW/apply/ACK | Not run | NOT EXECUTED | — | Policy integration and hardware pending | M1/M3/M2 |
| W2-T07 | M4/M1/M3/M2 | Ubuntu/A–B/browser required | — | — | Plan W2-T07 | Revoke blocks A, B unchanged | Not run | NOT EXECUTED | — | Full policy chain pending | M1–M4 |
| W2-T08 | M1/M3/M5 | HTTP/DB/A–B required | — | — | Plan W2-T08 | Cross-endpoint credential misuse rejected | Full scenario not run; M1 auth/isolation evidence available | NOT EXECUTED | M1 refs below | Actual Agent and B rules evidence pending; Ubuntu deferred | M1/M3/M5 |
| W2-T09 | M3/M2 | Ubuntu/offline required | — | — | Plan W2-T09 | Offline unknown USB remains BLOCKED/queued | Not run | NOT EXECUTED | — | Physical offline runtime pending | M2/M3 |
| W2-T10 | M3/M1 | Ubuntu/HTTP required | — | — | Plan W2-T10 | Reconnect persists queued event correctly | Not run | NOT EXECUTED | — | Real outbox/reconnect scenario pending | M1/M3 |
| W2-T11 | M2/M3/M5 | Ubuntu GUI/physical USB required | — | — | Plan W2-T11 | One popup on correct GUI user | Not run | NOT EXECUTED | — | GUI/hardware/helper evidence pending | M2/M3/M5 |
| W2-T12 | M2/M5 | Ubuntu GUI required | — | — | Plan W2-T12 | Excluded triggers never notify | Not run | NOT EXECUTED | — | Notification negative runtime pending | M2/M5 |
| W2-T13 | M2/M3/M5 | Ubuntu session/failure required | — | — | Plan W2-T13 | No wrong-user popup, events continue | Not run | NOT EXECUTED | — | Session/helper failure runtime pending | M2/M3/M5 |
| W2-T14 | M4/M5 | Windows actual HTTP + browser fault injection | `cd81060` + working tree | 2026-10-16; +07:00 log | Actual 200 empty/401; intercepted 403/500/network then recovery | Empty/error/auth truthful with no mock fallback | PASS; no rows during faults; recovery reads actual API | PASS | [HTTP/browser](week2-evidence/13-live-events-browser-http.txt), [adapter tests](week2-evidence/10-live-events-tests.txt) | 403/500/network explicitly injected; no production outage claim | M4/M5 |
| W2-T15 | M5 | Windows fixture + actual HTTP/browser | `cd81060` + working tree | 2026-10-16; +07:00 log | Exact endpoint/user/Device ID, type/decision/date, reset, page 2 | Published live API semantics; full selected local days | PASS; global filters/pages and microsecond day-end vs next midnight | PASS | [HTTP/browser](week2-evidence/13-live-events-browser-http.txt), [query tests](week2-evidence/10-live-events-tests.txt) | Device name/VID/PID/hash substring search stays demo-only; actual source is HTTP QA | M4/M5 |
| W2-T16 | M1/M3 | Java/HTTP + Ubuntu required | — | — | Plan W2-T16 | Apply/ACK failure never claims applied success | Full scenario not run; M1 ACK validation evidence available | NOT EXECUTED | M1 refs below | M3 apply/recovery evidence pending; Ubuntu deferred | M1/M3 |
| W2-T17 | M4/M5 | Windows/Node/Java/H2/local Chromium | `cd81060` + working tree | 2026-10-16; +07:00 logs | test/lint/build/test:browser/test:browser:live | All commands exit 0 | PASS; 60 tests, build/lint and both browser runs | PASS | [Current report](week2-evidence/M5_LIVE_EVENTS_VERIFICATION.md) | HTTP QA source, not physical USB; prior 2026-10-13 run remains historical | M4/M5 |
| W2-T18 | M1/M3/M2 | Java/Maven/Ubuntu required | — | — | Plan W2-T18 | Java tests and service/IPC smoke pass | Full scenario not run; M1 Maven evidence available | NOT EXECUTED | M1 refs below | Client fresh Maven evidence and deferred Linux service/IPC smoke remain | M1/M3/M2 |

## Executed frontend sub-runs (not canonical live PASS)

| ID | Owner / environment | Revision / date | Steps and expected | Actual / status | Evidence | Limitation / follow-up |
|---|---|---|---|---|---|---|
| W2-T14-FE | M5; Windows SSR/Chromium | Base + M5 worktree; 2026-10-13 +07:00 | Render loading/unavailable/error/empty; no mock rows | PASS; distinct states and no fallback verified | [Unit log](week2-evidence/03-frontend-tests.txt), [browser log](week2-evidence/04-browser-smoke.txt), [live unavailable screenshot](week2-evidence/events-live-unavailable.png) | No real 401/403/500/network HTTP run; M1/M4 |
| W2-T15-FE | M5; Windows unit/explicit demo | Same revision/date | Combined filters/reset; UNKNOWN; local inclusive dates, UTC/offset and DST | PASS; Bangkok/UTC/DST boundaries and demo interaction verified | [Unit log](week2-evidence/03-frontend-tests.txt), [browser log](week2-evidence/04-browser-smoke.txt), [demo screenshot](week2-evidence/events-demo-desktop.png) | No global server filters/page integration; M1/M4/M5 |
| M5-W2-MAPPER | M5; Windows unit/draft DTO fixture | Same revision/date | Preserve unchanged M2 Mouse sample plus test ID; invalid payload fails; empty stays empty | PASS; five historical mapper tests | [Original unit log](week2-evidence/03-frontend-tests.txt) | Historical draft run; superseded by v0.2 sub-run below |
| M5-W2-MAPPER-V02 | M5; Windows unit/Admin API v0.2 fixture | `cd81060` + M5 worktree; 2026-10-16 +07:00 | Canonical interfaceClass, usbDeviceId, separate timestamps; preserve page/order/totals; reject malformed items/metadata | PASS; 12 fixture contract tests | [Tests](../../frontend/tests/m5_event_api.test.mjs), [test log](week2-evidence/06-event-contract-tests.txt), [manifest](week2-evidence/09-event-contract-source-state.txt) | Earlier fixture-only phase; later actual HTTP/browser evidence is recorded separately |
| W2-T17-WINDOWS-20261016 | M5; Windows Node/frontend | Same base/date, earlier mapper-only worktree | Run test/lint/build for mapper/model change | PASS; 52 tests, lint and build exit 0 | [Verification](week2-evidence/M5_EVENT_CONTRACT_VERIFICATION.md) | Earlier mapper-only phase; superseded by current 60-test/live integration run |
| W2-T03-WINDOWS-HTTP | M5; Windows Spring Boot/H2/Chromium | `cd81060` + working tree; 2026-10-16 | Seed isolated H2 through authenticated Agent HTTP; render through actual Admin API | PASS; 25 persisted QA rows, six columns, server totals and pagination | [Current verification](week2-evidence/M5_LIVE_EVENTS_VERIFICATION.md) | Synthetic event source; physical W2-T03 remains NOT EXECUTED |
| W2-T17-HISTORICAL-20261013 | M4/M5; Windows Node/Chromium | Original base + M5 worktree; 2026-10-13 | Build/lint/45 tests/browser smoke | PASS (historical) | [Original report](week2-evidence/M5_FRONTEND_VERIFICATION.md) | Original fixture/demo/unavailable scope; superseded by current 60-test/live run |

## Reviewed M1 evidence (owner execution; not rerun by M5)

These backend-only results are linked as partial coverage of canonical scenarios, not promoted to full Agent/browser/Ubuntu PASS. Execution details are owned by the [M1 report](../member1/WEEK2_SERVER_INTEGRATION.md), updated 2026-10-16.

| Related scenarios | Backend scope | Owner evidence |
|---|---|---|
| W2-T01/T04/T08 | Enrollment, credentials, Admin session, negative authorization | [Admin auth](../member1/week2-evidence/02-admin-auth-http.txt), [Agent auth](../member1/week2-evidence/03-agent-auth-http.txt) |
| W2-T05/T07/T08/T16 | A/B whitelist/version isolation, revoke and ACK validation | [A/B](../member1/week2-evidence/04-policy-A-B-isolation.txt), [ACK](../member1/week2-evidence/05-policy-version-ack.txt) |
| W2-T03/T15 | Persisted sample event, filtered/paged read; sample remains Mouse ALLOWED/session 21 | [Event query](../member1/week2-evidence/06-event-ingestion-query.txt) |
| W2-T18 | Backend Maven tests/package | [Maven log](../member1/week2-evidence/01-maven-test.txt), [test summary](../member1/week2-evidence/test-summary.txt) |

Week 1 evidence remains historical. No canonical physical, GUI or full E2E case is marked PASS from these frontend results.

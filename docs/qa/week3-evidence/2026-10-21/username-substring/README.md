# Linux User partial-search correction

Captured: 2026-10-21 (Asia/Bangkok). Case **W3-F12**. [Inventory](../../../WEEK3_TEST_INVENTORY.md), [QA summary](../../../WEEK3_QA_SUMMARY.md), [Event search report](../../../WEEK3_EVENT_NAME_SEARCH_FIX.md).

The Linux User field now accepts a case-insensitive literal part of the stored username. For example, `student` or `STUDENT` matches `student01` and `student02`; `UnKn` matches `UNKNOWN`. Whitespace is trimmed and `%`, `_` and backslash are literal characters. Filtering/counting happen on the server before pagination and combine with endpoint/device/date/decision/type filters.

The frontend sends optional `username` for this search. Existing API callers can still use exact `linuxUsername`; supplying both combines them with AND. The UI placeholder is `Tên hoặc một phần tên`. This supersedes the previous live UI's exact-user behavior without changing the Agent event DTO, source attribution or existing exact API parameter.

Branch `huy`; base `dee3cf7753521c653a79e24cc848df384bf7b639` plus working-tree changes. [Environment](environment.json), [source manifests](sources/) and [artifact/source/document validation](validation.json) identify the actual tested inputs. Each command has UTF-8 raw output and same-stem JSON containing argv/cwd, timestamps with +07:00, base revision, source manifest and exit code. Final source tree hash: `91459287cb91aedb9e5b68aec8365e72c77890a35f47f74bfe291b02d2f5e934`.

| Log | Command / scope | Result |
|---|---|---|
| [01](01-server-regression.txt) | `mvn -o -B -f admin-server/pom.xml package` | PASS: **20 server tests**, 0 failures/errors/skips; fresh executable JAR |
| [02](02-frontend-tests.txt) | `npm test` | PASS: **88 frontend tests**, no failures/skips/cancellations |
| [03](03-frontend-lint.txt) | `npm run lint` | PASS, exit 0 |
| [04](04-frontend-build.txt) | `npm run build` | PASS: TypeScript/Vite production bundle |
| [05](05-browser-events.txt) | `npm run test:browser:live` | PASS: partial mixed-case username, two matching A/B users, partial UNKNOWN, existing event/filter/paging/auth/error/mobile checks |
| [06](06-browser-fixture.txt) | `npm run test:browser` | PASS: fixture/browser regression with matching test-fixture semantics |

New server cases verify 25 matching events across two pages with nonmatching newer records; combined endpoint/exact-user criteria; case/whitespace/blank searches; partial UNKNOWN and literal wildcard characters. Updated frontend assertions verify the partial query is sent as `username`, with no exact `linuxUsername` parameter. The browser uses 28 persisted synthetic HTTP events and verifies `WEEK3-USER` matches both historical users while `UnKn` matches only UNKNOWN.

Fresh screenshot: [partial username results](screenshots/events-username-substring.png), copied only after timestamp verification against successful Events run 05. Standalone management browser was not rerun for this narrow correction; its [prior capture](../event-name-search/09-browser-management.txt) remains historical. Existing management contracts are covered by the current full server/frontend suites; no fresh Agent test is claimed.

Maven and browser tests used isolated in-memory/temporary H2. The active user runtime/database was not opened, reset, stopped or used. Restart the user backend with the newly built JAR and reload the updated frontend to activate the new optional query. Group/M1 contract review remains open; Ubuntu/physical USB/GUI/install/reboot execution remains deferred.

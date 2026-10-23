# Event name-search follow-up evidence

Captured: 2026-10-21 (Asia/Bangkok). [Fix report](../../../WEEK3_EVENT_NAME_SEARCH_FIX.md), [inventory](../../../WEEK3_TEST_INVENTORY.md), [QA summary](../../../WEEK3_QA_SUMMARY.md). Branch `huy`, base `dee3cf7753521c653a79e24cc848df384bf7b639` plus working-tree changes.

This capture follows the initial Week 3 run and has its own [environment](environment.json), [source manifests](sources/), [artifact/screenshot hashes and document validation](validation.json). Manifests include tracked and new untracked source/test files, including `EventSearchIntegrationTest.java`. Logs and same-stem JSON files record exact cwd/argv, timestamps in +07:00, base revision, source manifest/hash, scope and command exit code.

| Log | Command / scope | Result |
|---|---|---|
| [01](01-name-search-reproduction.txt) | `mvn -o -B -f admin-server/pom.xml -Dtest=EventSearchIntegrationTest test` against old implementation | Exit 1; 4/4 new integration cases reproduce ignored name queries |
| [02](02-server-regression.txt) | First `mvn -o -B -f admin-server/pom.xml package` | Exit 0; 17 server tests PASS; before adding the missing-device-details case |
| [03](03-frontend-tests.txt) | `npm test` | Exit 0; **88/88 frontend tests PASS** |
| [04](04-frontend-lint.txt) | `npm run lint` | Exit 0; PASS |
| [05](05-frontend-build.txt) | `npm run build` | Exit 0; TypeScript/Vite production bundle PASS |
| [06](06-server-regression.txt) | Final `mvn -o -B -f admin-server/pom.xml package` | Exit 0; **18 server tests PASS**, 0 failures/errors/skips; executable JAR rebuilt |
| [07](07-browser-fixture.txt) | `npm run test:browser` | Exit 0; fixture/browser PASS |
| [08](08-browser-events.txt) | `npm run test:browser:live` | Exit 0; names/IDs and exact username, server paging/date/A–B, UNKNOWN/no-serial, auth/errors/mobile PASS |
| [09](09-browser-management.txt) | `npm run test:live` | Exit 0; real Spring Boot/H2 management/auth/session/A–B/controlled ACK/restart PASS |

The final source tree hash for server/browser checks is `7b2d5816a95e6833d4a03958428dce092a549b0cd61a95f94d767df657d21e70`. Frontend test/build inputs are identical to the final tree for their respective scopes; the intervening change was backend-only (preserve historical events with a device ID but missing device details). The final validation checks these input hashes.

Fresh screenshots from Events run 08 only:

- [USB name search and aligned filter/table labels](screenshots/events-name-search.png).
- [Endpoint B searched by partial hostname](screenshots/events-week3-endpoint-b.png).
- [Mobile Events](screenshots/events-real-http-mobile.png).

All three browser suites ran sequentially. Maven used isolated in-memory/temporary H2 and browser suites used their own temporary H2/QA accounts. The user's existing runtime/database was already active and locked; it was not read, reset, stopped or used by this follow-up. No before/after database hash is claimed for this capture. Agent code and its earlier test evidence are unchanged. Ubuntu/physical USB/GUI/install/reboot remain NOT EXECUTED under the deferred schedule.

The API extension and UI must be integrated/deployed together. The running user backend was not restarted by QA; M1 review and group acceptance remain open.

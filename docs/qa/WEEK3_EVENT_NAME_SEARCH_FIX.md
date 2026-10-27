# Week 3 Event filter/table alignment

Date: 2026-10-21 (Asia/Bangkok). Owner: M5; backend contract review: M1. Branch `huy`, working tree based on `dee3cf7753521c653a79e24cc848df384bf7b639`. This follow-up supersedes the initial Week 3 UI's ID-only search behavior; the earlier 85-test/13-server-test evidence remains a historical snapshot of its tested source.

## Problem and resulting behavior

The live table showed hostname and USB name while the filter sent exact `endpointId` and numeric `deviceId`. Entering a visible name such as `DESKTOP-8STBDDD` or `Kingston DataTraveler 3.0` could therefore return nothing or fail client validation. Linux Username already matched the username string, not a UID.

The live filter now uses the same **Endpoint / Linux User / USB** labels as the table. Endpoint and USB accept names or IDs; text placeholders explain the accepted values and USB input no longer requests a numeric-only keyboard. The table keeps names primary and shows endpoint/device IDs underneath for identification. The subsequent Linux User correction supports a case-insensitive part of the login name, including `UNKNOWN`; the latest evidence is linked below.

| Input | Query / matching behavior |
|---|---|
| Endpoint name or ID | `endpoint`: case-insensitive literal substring of captured hostname or endpoint ID; registry hostname used only when the event has no captured hostname |
| USB name or ID | `device`: case-insensitive literal substring of device name, or exact positive numeric `usbDeviceId`; ID matching also retains historical events whose device details are missing |
| Linux User | `username`: case-insensitive literal substring; existing exact `linuxUsername` stays available for API callers; UID is not used |
| Other filters | Decision/type/date and paging unchanged; all predicates combine with AND |

The backend filters and counts before pagination. The frontend does not download all history, derive totals from one page, or filter a page locally. Existing API callers can keep exact `endpointId`, `deviceId` and `linuxUsername` parameters. Blank text is ignored; `%`, `_` and backslash are escaped as literals. No database migration is required.

## Implementation and contract handoff

- M5: `EventFilters.tsx`, `EventTable.tsx`, event CSS, `Events.tsx`, query types/mapper utility and related component/adapter/browser tests.
- M1-owned backend: a narrow additive change to `AdminEventController` plus `EventSearchIntegrationTest`. [API documentation](../API.md) and [view-model mapping](EVENT_VIEW_MODEL_MAPPING.md) describe the new optional query parameters. Auth, Agent protocol, policy and schema are unchanged.
- M4/M5 fixture: added the same name-query behavior to the existing test-only HTTP fixture. The shared fetch/auth implementation and management adapter were not changed.

M1 should review the additive Event API change during integration; this report does not claim a human owner approval. Deploy/restart the backend with the newly built JAR and serve the updated frontend build together. An already running old backend ignores the new optional query parameters and must be restarted to provide the new behavior. The user's existing application runtime/database was not stopped, reset or used by these tests.

## Verification

Latest correction: [Linux User substring capture](week3-evidence/2026-10-21/username-substring/README.md). Entering `student` or `STUDENT` matches `student01` and `student02`. The tests cover server-side filtered pagination, mixed case, whitespace, literal wildcards, UNKNOWN and combined endpoint filters. The initial name-search results below retain their earlier 18-server-test source and exact-user behavior; they are not relabeled as executions of the later username change.

Evidence: [name-search capture](week3-evidence/2026-10-21/event-name-search/README.md). Each command has timestamps, base revision, source hashes and exit code. All browser inputs are synthetic HTTP data in temporary isolated H2; Maven tests use separate in-memory/temporary databases. The user's existing application H2 was locked by its active runtime and was not opened or hashed in this follow-up.

| Check | Result | Evidence |
|---|---|---|
| New API cases against old backend source | Expected reproduction: 4 tests fail because old API ignores name filters | [Before fix](week3-evidence/2026-10-21/event-name-search/01-name-search-reproduction.txt) |
| Full frontend unit/component/adapter suite | PASS: **88/88**, including labels, text query encoding and visible IDs | [Tests](week3-evidence/2026-10-21/event-name-search/03-frontend-tests.txt) |
| Lint / production frontend build | PASS, exit 0 | [Lint](week3-evidence/2026-10-21/event-name-search/04-frontend-lint.txt), [build](week3-evidence/2026-10-21/event-name-search/05-frontend-build.txt) |
| Full server suite + executable JAR | PASS: **18 tests**, 0 failures/errors/skips; includes 5 new name-search integration cases | [Server](week3-evidence/2026-10-21/event-name-search/06-server-regression.txt) |
| Fixture browser | PASS | [Fixture](week3-evidence/2026-10-21/event-name-search/07-browser-fixture.txt) |
| Real Events browser | PASS: name/ID searches, exact user, 28 HTTP-seeded records, paging/date/A–B/error/mobile regression | [Events](week3-evidence/2026-10-21/event-name-search/08-browser-events.txt) |
| Real management browser | PASS: auth/session/CSRF, A/B Allow/Revoke, controlled ACK, restart persistence and session expiry | [Management](week3-evidence/2026-10-21/event-name-search/09-browser-management.txt) |

The new server cases cover mixed case/partial names, exact IDs and legacy parameters, captured-hostname precedence/registry fallback, missing device details, global 25-result pagination with a nonmatching newer event, combined filters, whitespace and literal SQL wildcard characters. Source/build manifests and screenshots are indexed with the capture. Client-agent source and its previous 61-test result are unchanged; no fresh Agent run is claimed for this UI/API follow-up.

The group integration gate remains open. Ubuntu hardware, user switching, notification, `.deb` lifecycle and reboot remain `NOT EXECUTED / DEFERRED_UBUNTU` under the existing schedule.

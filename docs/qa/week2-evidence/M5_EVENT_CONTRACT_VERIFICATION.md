# M5 Admin Events v0.2 contract verification

Date: 2026-10-16 (Asia/Bangkok). Environment: Windows, existing local Node/npm dependencies. Base revision: `cd81060b8f5f32e4ffa42e9120e2827d92219204`, branch `huy`, plus the M5 working-tree changes identified by the [source manifest](09-event-contract-source-state.txt).

Scope: mapper, event view model, page envelope and frontend regression. Ubuntu tests are deferred at the user's request. Shared M4 fetching/authentication and live browser/server integration are outside this change.

| Command (`frontend/`) | Actual result | Raw evidence |
|---|---|---|
| `npm test` | PASS, 52 tests / 0 failed; includes 12 Admin Events fixture contract tests | [Test log](06-event-contract-tests.txt) |
| `npm run lint` | PASS, exit 0 | [Lint log](07-event-contract-lint.txt) |
| `npm run build` | PASS, TypeScript + Vite production build, exit 0 | [Build log](08-event-contract-build.txt) |

Each log records start/end with +07:00 offset, command, base revision and exit code. Node tests/build used the permitted local execution environment to allow child processes. No dependencies were installed. Browser smoke was not rerun for this mapper/model change; the [2026-10-13 browser report](M5_FRONTEND_VERIFICATION.md) remains historical evidence.

## Verified behavior

- The Admin read fixture uses `device.interfaceClass`, persisted `usbDeviceId` and separate `occurredAt`/`receivedAt`. Mouse attribution stays ALLOWED/session 21; the original M2 sample file is unchanged.
- Event/device IDs become strings without losing precision. Unsafe numeric IDs, malformed IDs, timestamps, sessions, device fields and unsupported event enums fail instead of manufacturing audit data.
- BLOCKED decision remains independent of UNKNOWN Linux user. Nullable legacy hostname/device data and defensive explicit UNKNOWN decision support retain their existing meaning.
- `mapEventPage()` retains server order, zero-based page, size and global totals. First/last pages, partial pages, empty results and empty out-of-range pages are covered.
- Invalid page metadata or one invalid item rejects the page. No rows are silently dropped or substituted; global totals are never calculated from the current page length.
- Existing Events states, six columns, endpoint links, local demo filters/date/DST handling and endpoint regression tests continue to pass.

## Evidence limits and handoff

The [fixture](../../../frontend/tests/fixtures/admin-events-page.json) is synthetic and matches the published M1 API v0.2 records; these are fixture contract tests, not HTTP tests or generated-server serialization checks. This run does not claim a live API/browser, physical USB, USBGuard policy or GUI notification PASS.

M4 can now consume `mapEventPage()` in the shared adapter. Server-driven Events controls and exact-match/device-ID filter semantics still need integration. The UI's exclusive date upper bound must be reconciled with the API's inclusive `to`, including server timestamp precision. See [mapping/handoff](../EVENT_VIEW_MODEL_MAPPING.md), [inventory](../WEEK2_TEST_INVENTORY.md) and [summary](../WEEK2_QA_SUMMARY.md).

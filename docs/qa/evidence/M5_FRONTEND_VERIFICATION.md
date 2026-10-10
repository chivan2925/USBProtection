# M5 frontend verification

Execution date: 2026-10-10 (Asia/Bangkok). Environment: Windows, repository frontend dependencies, locally installed Chromium. Commands ran from `frontend/`.

| Command | Result | Scope |
|---|---|---|
| `npm test` | PASS — 31 tests, 0 failures | Core utilities (6), shared/page rendering (7), event filters (2), M4 endpoints (8), M5 events (8). |
| `npm run build` | PASS | TypeScript project build and Vite production bundle. |
| `npm run lint` | PASS | oxlint. |
| `npm run test:browser` | PASS | Routes, 404, refresh toast, filters, login safety, mobile navigation, responsive overflow and browser errors. |

`npm test` explicitly includes `m4_endpoints.test.mjs` and `m5_events.test.mjs`. SSR test servers disable WebSocket serving (`ws: false`) so concurrently running suites do not compete for its WebSocket port.

## Event History implementation

- `src/types/event.ts` defines the frontend view model (`timestamp`, `type`, `username`, `deviceName`, lower-case decisions). Backend transport fields require mapping when the API adapter is implemented.
- `pages/Events.tsx` and M5 tests use the same `components/event/EventTable.tsx`; the parallel root-level EventTable/EventFilters implementations were removed.
- Shared table behavior preserves newest-first ordering, ten-row pagination, loading and empty/filtered states. Endpoint IDs link to endpoint details.
- Filters retain Endpoint, Linux Username, Device, Decision and browser-local Date, combined matching and reset.
- Tests cover both direct EventTable rendering and the Events page with ready data. A ready empty response stays empty; unavailable data uses explicitly labelled mock records.

Browser screenshots are generated locally in `frontend/.artifacts/` by `tests/browser.mjs`; they are not committed evidence. This report records the execution result and reproducible command, without depending on those local files.

## Limits

No real backend/Agent integration or Ubuntu hardware tests were run. These results verify frontend mock/fixture behavior only. The initial sandbox run could not spawn test workers (`EPERM`); the full commands were subsequently run successfully with execution escalation.

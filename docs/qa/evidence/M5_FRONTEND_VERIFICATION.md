# M5 Frontend Verification

Date: 2026-10-10 (Asia/Bangkok). Executed by Codex on Windows in `D:\USBProtection\frontend`, against the current working tree after merge-conflict resolution. This report records this run; it does not reconstruct missing historical evidence.

| Command | Result | Scope |
|---|---|---|
| `npm test` | PASS: 31 tests, 0 failures | Five test files: core 6, components 7, eventFilters 2, M4 endpoints 8, M5 events 8 |
| `npm run build` | PASS | TypeScript project build and Vite production bundle |
| `npm run lint` | PASS | oxlint |
| `npm run test:browser` | PASS | Routes, 404, refresh toast, event decision filter, empty result/reset, login, responsive layout and runtime errors |

Node/Vite tests and browser checks required subprocess access outside the restricted sandbox; the initial sandbox attempt returned `spawn EPERM`. The completed runs above exited 0.

`Events.tsx` and M5 tests import the same `components/event/EventTable.tsx`. EventFilters has one implementation in the same directory. Both use the frontend view model in `types/event.ts`; transport DTO fields require explicit mapping when API integration is added. Pagination, ready-empty responses without mock fallback, device/user display, decisions and endpoint links are covered by automated tests.

Browser screenshots are generated locally under `frontend/.artifacts/` by the smoke script; they are not committed evidence and this report does not link to them.

This verifies the frontend mock/fixture behavior. No Ubuntu hardware rerun, Admin Server boot, live Agent POST or full E2E verification was performed in this run. M2 source evidence was not changed.

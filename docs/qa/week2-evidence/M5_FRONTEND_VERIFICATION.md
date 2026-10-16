# M5 Week 2 frontend verification

Date: 2026-10-13 (Asia/Bangkok). Environment: Windows, Node v22.20.0, npm 10.9.3, existing frontend dependencies and local headless Chromium. Baseline commit `8b644eafc7aa3651b92e7a6746f4f22a4f1ba65e`, branch huy plus uncommitted M5 changes. [Source manifest](05-source-state.txt) records paths/hashes; there is no new PR/commit yet.

| Command (frontend/) | Result | Evidence |
|---|---|---|
| npm run build | PASS, final exit 0 | [Build log](01-frontend-build.txt) |
| npm run lint | PASS, exit 0 | [Lint log](02-frontend-lint.txt) |
| npm test | PASS, 45 tests, final exit 0 | [Unit/component/fixture log](03-frontend-tests.txt) |
| npm run test:browser | PASS, final exit 0 | [Browser log](04-browser-smoke.txt) |

Build and test logs retain initial EPERM attempts and allowed successful retries. Browser log retains the initial test script scope failure and successful retry after correction. Capture timestamps/exit codes are recorded per attempt.

## Verified scope

- Events loading/unavailable/error/ready-empty: no mock rows. Resource notices/error/retry and six columns remain. Live mode currently means an unavailable adapter; no invented HTTP request.
- Draft read fixture mapper: unchanged M2 Mouse sample stays ALLOWED/session 21; preserves identity/user/time/device/session. Missing/invalid identity/date/enums reject, explicit UNKNOWN remains distinct from absent demo decision, empty array stays empty. Five mapper tests, not HTTP contract tests.
- Filters: endpoint/user/device/type/decision compose, reset works, UNKNOWN differs from no decision, VID/PID/serial/hash searchable. Browser-local inclusive dates tested in Asia/Bangkok, UTC and America/New_York spring/fall DST; invalid/reversed dates and invalid timestamps rejected for filtering.
- Existing newest-first/ten-row pagination and M4 endpoint isolation regressions pass. Browser demo checks decision+type, empty result, date exclusion and inclusive same-day selection, reset, links and mobile overflow.

## Screenshots

| Capture | Meaning |
|---|---|
| [Live Events unavailable](events-live-unavailable.png) | Real current adapter unavailable; no fixture history shown |
| [Demo Events desktop](events-demo-desktop.png) | Explicit demo banner/badge, six columns, seven filters and reset |
| [Demo Events mobile](events-demo-mobile.png) | Explicit demo layout at 375px; table scroll is allowed, no document overflow |

These are copies of the final successful browser run's `.artifacts` screenshots. They do not prove a physical USB event, a real Admin API, persisted data or an Ubuntu GUI notification. No Maven/Ubuntu/hardware run was performed here. [Inventory](../WEEK2_TEST_INVENTORY.md) keeps these checks separate from canonical live scenarios.

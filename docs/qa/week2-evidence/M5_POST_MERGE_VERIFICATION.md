# M5 Week 2 post-merge Windows verification

Date: 2026-10-16 (Asia/Bangkok). Executor: Codex for the user. Tested frontend: `a9b1799e2920cba8b6d02fc1d7186fbc0bc59825` on `huy`, merging `main` (`1eb6be6`) and M5 Events (`c6b1b8f`). Application/test source remains committed at that revision; this follow-up writes QA documentation and evidence. [Environment](16-post-merge-environment.txt), [SHA-256 manifest](23-post-merge-source-state.txt), [technical M4/M5 handoff](../M4_M5_WEEK2_HANDOFF.md).

**PASS: build, lint, 72 tests and all three Windows browser suites.** Whole Week 2 hardware/GUI integration remains OPEN. Human M4 acknowledgment remains PENDING; the technical review and handoff package are complete.

## Commands and raw evidence

Each command log records the actual revision, command, working directory, start/end timestamp with +07:00 offset and exit code. Execution used existing dependencies, local Chromium and JDK 22.0.2. Browser commands ran sequentially after the production build.

| Command from `frontend/` | Result | Classification / evidence |
|---|---|---|
| `npm run build` | PASS, exit 0 | TypeScript and production Vite bundle; [raw build log](17-post-merge-build.txt) |
| `npm run lint` | PASS, exit 0 | Existing Oxlint rules; [raw lint log](18-post-merge-lint.txt) |
| `npm test` | PASS, 72/72; zero failed/skipped/cancelled; exit 0 | Unit/component/API fixture regression; [raw test log](19-post-merge-tests.txt) |
| `npm run test:browser` | PASS, exit 0 | HTTP fixture + browser; routes/auth/CSRF, A/B Allow/Revoke, errors/empty/network, demo and mobile; [raw fixture browser log](20-post-merge-browser-fixture.txt) |
| `npm run test:live` | PASS, exit 0 | Actual Spring Boot/Spring Security/file H2 + browser, synthetic HTTP input; [raw management browser log](21-post-merge-browser-management.txt) |
| `npm run test:browser:live` | PASS, exit 0 | Actual Spring Boot/H2 + browser, 25 synthetic authenticated Agent HTTP events; [raw Events browser log](22-post-merge-browser-events.txt) |

The first test-log collector invocation supplied extra positional `e s t` arguments to npm because a one-element PowerShell array was unwrapped. All 72 tests passed in that invocation. The collector was corrected and the exact `npm test` command reran successfully; both outputs and the explanation remain in the same log. Application/test source did not change. All final commands exited 0.

## Verified results and case mapping

| Scenario | Actual result | Week 2 cases |
|---|---|---|
| Admin session and CSRF | Invalid login rejected; valid login, `/me`/rotated token, reload, expired session after restart and logout/protected API 401 pass. Shared AuthProvider redirects unauthorized routes to Login; browser storage contains no credentials | W2-T04 |
| Allow, duplicate Allow and A/B isolation | A starts desired/applied 1/0; Allow adds the selected HASH and moves A to 2/0; duplicate does not increment; B versions/rules remain unchanged | W2-T05 PASS for Web/API/H2 |
| Controlled ACK, Revoke and persistence | Future ACK rejected; controlled HTTP ACK sets applied 2; Web Revoke removes A's entry and moves desired/applied to 3/2. B is unchanged. Restart retains desired 3, applied 2, empty whitelist and the persisted event | Windows portion of W2-T07/T08/T16; actual Agent/USBGuard remains unexecuted |
| Persisted event → Web | Real backend accepts 25 authenticated HTTP QA events; Web displays six columns and original user/device/time data with 20+5 server pages and global totals | Windows sub-run of W2-T03; physical source remains unexecuted |
| Global filters and local dates | Exact endpoint/user/Device ID, type/decision, empty results, reset and page transitions pass. Bangkok selected day includes stored `2026-10-16T16:59:59.999999Z`, excludes next midnight `17:00:00Z`; query upper bound ends `.999999999Z`. Unit regression also covers UTC/offset/DST | W2-T15 |
| Truthful UI under failures | Real empty/401, intercepted 403/500/network and recovery preserve distinct states; no stale/mock rows. Demo is readonly with no Admin HTTP traffic. Desktop and 375px mobile checks pass | W2-T14, W2-T17 |

Exact endpoint IDs and before/after values from the current management run are in its raw log. HTTP enrollment/events/ACK are generated QA inputs; hostnames containing `ubuntu` are labels while execution is on Windows.

## Selected screenshots from this capture

All files below were copied from the fresh `frontend/.artifacts/` outputs into `post-merge/`; the manifest records hashes. Older screenshot paths and logs retain their original contents.

| Screenshot | Source / meaning |
|---|---|
| [Admin login](post-merge/m4-live-login.png) | Actual backend rejects invalid credentials |
| [Dashboard](post-merge/m4-live-dashboard.png) | Actual H2-backed management snapshot from synthetic input |
| [Allow A](post-merge/m4-live-allow-a.png) | Desired 2 / applied 0 and selected HASH |
| [Controlled HTTP ACK](post-merge/m4-live-http-ack.png) | Server/UI applied version 2, supplied by the controlled HTTP client |
| [Revoke A](post-merge/m4-live-revoke-a.png) | Desired 3 / applied 2, whitelist removed |
| [Management Events](post-merge/m4-live-events.png) | Same shared auth/API renders the persisted management QA event |
| [Management mobile](post-merge/m4-live-mobile.png) | Live management/Event view at mobile width |
| [Events desktop](post-merge/events-real-http-desktop.png) | Isolated H2, 25 HTTP QA events, server page/totals |
| [Events date filter](post-merge/events-real-http-date-filter.png) | Stored final microsecond included; next local midnight excluded |
| [Events mobile](post-merge/events-real-http-mobile.png) | Live server-paginated table at 375px without page overflow |
| [Fixture network error](post-merge/m4-network.png) | Explicit injected network error; no mock fallback |
| [Demo mobile](post-merge/demo-events-mobile.png) | Explicit demo remains separate and issues no Admin API calls |

## Artifact and environment limits

- The existing backend JAR was executed and hashed, not rebuilt. Its SHA-256 is recorded with Node/npm/Java/browser versions in the environment and manifest. Owner Maven evidence is linked from the inventory; no new Maven test/package run is claimed here.
- Both real browser harnesses start separate H2 databases under `frontend/.artifacts/`; the management test restarts its isolated DB to test persistence. The user's application DB was already locally modified and locked by an existing process; it was excluded and not copied/reset/used by this capture. No byte-for-byte integrity comparison is claimed for that running DB.
- Unit and fixture results remain distinct from real backend/browser results. HTTP events do not establish physical USB input or active Linux session attribution; controlled ACK does not establish successful USBGuard apply. Browser fault injection does not establish an actual production outage.
- Ubuntu real Agent enrollment/heartbeat/outbox/recovery, physical Allow/Revoke, fail-closed USBGuard enforcement, correct graphical user notification and negative GUI cases remain NOT EXECUTED. These are the remaining execution gates in the plan, with owners M1/M2/M3/M5 as recorded in the inventory.
- This report and technical handoff were prepared by Codex. M4 human review/acceptance needs an explicit owner record and has not been inferred or sent on the user's behalf.

## Historical evidence

[M5 frontend 45-test](M5_FRONTEND_VERIFICATION.md), [mapper 52-test](M5_EVENT_CONTRACT_VERIFICATION.md), [Events 60-test](M5_LIVE_EVENTS_VERIFICATION.md), [M4 57-test report](../../admin-web/week2-evidence/README.md), their raw logs, original manifests and original screenshots retain their original dates/source scopes. Current summary/inventory/mapping now point here for the merged implementation.

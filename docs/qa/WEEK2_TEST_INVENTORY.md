# Week 2 execution inventory

Owner: M5. Updated: 2026-10-13 (Asia/Bangkok). [Plan/steps](WEEK2_INTEGRATION_TEST_PLAN.md). [QA summary](WEEK2_QA_SUMMARY.md).

Executed frontend revision: `huy` working tree based on `8b644eafc7aa3651b92e7a6746f4f22a4f1ba65e`, not yet committed. [Source manifest](week2-evidence/05-source-state.txt) identifies tested sources. Exact start/end times and exit codes are in command logs. Unexecuted rows have no execution date/revision/evidence.

| Test ID | Owner | Environment | Git revision | Date/time | Commands/steps | Expected | Actual | Status | Evidence | Known limitation | Follow-up owner |
|---|---|---|---|---|---|---|---|---|---|---|---|
| W2-T01 | M1/M3 | Ubuntu/HTTP required | — | — | Plan W2-T01 | Authenticated enrollment/heartbeat | Not run | NOT EXECUTED | — | Live credentials/runtime pending | M1/M3 |
| W2-T02 | M2/M3/M1 | Ubuntu/physical USB required | — | — | Plan W2-T02 | Real BLOCK and correct persisted attribution | Not run | NOT EXECUTED | — | Physical integration environment pending | M2/M3/M1 |
| W2-T03 | M5/M1/M4 | Live API/browser required | — | — | Plan W2-T03 | Real persisted event rendered accurately | Not run | NOT EXECUTED | — | Admin event API/adapter pending | M1/M4/M5 |
| W2-T04 | M4/M1 | HTTP/browser required | — | — | Plan W2-T04 | Real auth/session/unauthorized behavior | Not run | NOT EXECUTED | — | Admin authentication pending | M1/M4 |
| W2-T05 | M4/M1 | HTTP/DB/browser required | — | — | Plan W2-T05 | Allow changes A only | Not run | NOT EXECUTED | — | Whitelist API and A/B setup pending | M1/M4 |
| W2-T06 | M3/M2 | Ubuntu/A–B required | — | — | Plan W2-T06 | Actual policy ALLOW/apply/ACK | Not run | NOT EXECUTED | — | Policy integration and hardware pending | M1/M3/M2 |
| W2-T07 | M4/M1/M3/M2 | Ubuntu/A–B/browser required | — | — | Plan W2-T07 | Revoke blocks A, B unchanged | Not run | NOT EXECUTED | — | Full policy chain pending | M1–M4 |
| W2-T08 | M1/M3/M5 | HTTP/DB/A–B required | — | — | Plan W2-T08 | Cross-endpoint credential misuse rejected | Not run | NOT EXECUTED | — | Credential binding and A/B runtime pending | M1/M3/M5 |
| W2-T09 | M3/M2 | Ubuntu/offline required | — | — | Plan W2-T09 | Offline unknown USB remains BLOCKED/queued | Not run | NOT EXECUTED | — | Physical offline runtime pending | M2/M3 |
| W2-T10 | M3/M1 | Ubuntu/HTTP required | — | — | Plan W2-T10 | Reconnect persists queued event correctly | Not run | NOT EXECUTED | — | Real outbox/reconnect scenario pending | M1/M3 |
| W2-T11 | M2/M3/M5 | Ubuntu GUI/physical USB required | — | — | Plan W2-T11 | One popup on correct GUI user | Not run | NOT EXECUTED | — | GUI/hardware/helper evidence pending | M2/M3/M5 |
| W2-T12 | M2/M5 | Ubuntu GUI required | — | — | Plan W2-T12 | Excluded triggers never notify | Not run | NOT EXECUTED | — | Notification negative runtime pending | M2/M5 |
| W2-T13 | M2/M3/M5 | Ubuntu session/failure required | — | — | Plan W2-T13 | No wrong-user popup, events continue | Not run | NOT EXECUTED | — | Session/helper failure runtime pending | M2/M3/M5 |
| W2-T14 | M4/M5 | Live HTTP/browser required | — | — | Plan W2-T14 | Empty/401/403/500/network handled truthfully | Full live case not run; FE sub-run below | NOT EXECUTED | — | Actual HTTP statuses/session expiry pending | M1/M4/M5 |
| W2-T15 | M5 | Fixture + live API/browser | — | — | Plan W2-T15 | Complete filter/date/query semantics | Fixture portion executed; live query pending | NOT EXECUTED | — | Server query/page contract pending | M1/M4/M5 |
| W2-T16 | M1/M3 | Java/HTTP + Ubuntu required | — | — | Plan W2-T16 | Apply/ACK failure never claims applied success | Not run | NOT EXECUTED | — | Policy recovery/security implementation pending | M1/M3 |
| W2-T17 | M4/M5 | Windows/Node/local Chromium | Base + M5 worktree | 2026-10-13; log timestamps +07:00 | build/lint/test/test:browser | All commands exit 0 | Build/lint/browser pass; 45 tests pass | PASS | [FE report](week2-evidence/M5_FRONTEND_VERIFICATION.md) | Fixture/demo smoke; no live API | M4/M5 |
| W2-T18 | M1/M3/M2 | Java/Maven/Ubuntu required | — | — | Plan W2-T18 | Java tests and service/IPC smoke pass | Not run in this handoff | NOT EXECUTED | — | Fresh Maven/Linux execution required | M1/M3/M2 |

## Executed frontend sub-runs (not canonical live PASS)

| ID | Owner / environment | Revision / date | Steps and expected | Actual / status | Evidence | Limitation / follow-up |
|---|---|---|---|---|---|---|
| W2-T14-FE | M5; Windows SSR/Chromium | Base + M5 worktree; 2026-10-13 +07:00 | Render loading/unavailable/error/empty; no mock rows | PASS; distinct states and no fallback verified | [Unit log](week2-evidence/03-frontend-tests.txt), [browser log](week2-evidence/04-browser-smoke.txt), [live unavailable screenshot](week2-evidence/events-live-unavailable.png) | No real 401/403/500/network HTTP run; M1/M4 |
| W2-T15-FE | M5; Windows unit/explicit demo | Same revision/date | Combined filters/reset; UNKNOWN; local inclusive dates, UTC/offset and DST | PASS; Bangkok/UTC/DST boundaries and demo interaction verified | [Unit log](week2-evidence/03-frontend-tests.txt), [browser log](week2-evidence/04-browser-smoke.txt), [demo screenshot](week2-evidence/events-demo-desktop.png) | No global server filters/page integration; M1/M4/M5 |
| M5-W2-MAPPER | M5; Windows unit/draft DTO fixture | Same revision/date | Preserve unchanged M2 Mouse sample plus test ID; invalid payload fails; empty stays empty | PASS; five mapper tests | [Tests](../../frontend/tests/m5_event_api.test.mjs), [unit log](week2-evidence/03-frontend-tests.txt) | Fixture serialization, not real API/persistence; M1 freeze then M4 import |

Week 1 evidence remains historical. No canonical physical, GUI or full E2E case is marked PASS from these frontend results.

# Week 2 QA summary — M5

Updated: 2026-10-13 (Asia/Bangkok). Baseline: `8b644eafc7aa3651b92e7a6746f4f22a4f1ba65e`; M5 changes are in the `huy` working tree, not committed. [Inventory](WEEK2_TEST_INVENTORY.md), [plan](WEEK2_INTEGRATION_TEST_PLAN.md), [frontend evidence](week2-evidence/M5_FRONTEND_VERIFICATION.md).

**Week 2 is OPEN; no full E2E PASS claimed.** Implemented and verified the independently runnable M5 frontend/QA foundations. The provided handoff treats Week 1 as CLOSED for the accepted scope; its historical reports remain unchanged.

## Gate status

| Gate | Owner | Status | Evidence/remaining work |
|---|---|---|---|
| Physical USB event persisted with endpoint/active user/device/decision/time | M2/M3/M1 | NOT EXECUTED | Need Ubuntu correlated USBGuard/loginctl/Agent/Server/DB run |
| Web shows real backend events | M1/M4/M5 | NOT EXECUTED | UI fallback fixed and draft mapper tested; Admin event API/adapter not yet integrated |
| Admin and Agent authentication | M1/M3/M4 | NOT EXECUTED | Need live auth/session and credential-binding negative tests |
| Per-endpoint Allow/Revoke/version/apply/ACK | M1–M4/M5 | NOT EXECUTED | Need A/B API/DB/rules before/after; HTTP ACK alone insufficient |
| Unknown USB remains BLOCKED during server/network/notifier outage | M2/M3 | NOT EXECUTED | Need actual offline/outbox/reconnect and USBGuard state |
| One native BLOCK notification on correct GUI user; exclusions/failures safe | M2/M3/M5 | NOT EXECUTED | Need real Ubuntu GUI screenshots/video and negative traces |
| Honest QA statuses, environment and evidence index | M5 | PASS (current handoff) | Plan contains all W2-T01–T18; inventory separates unit/demo from unexecuted real cases |

## Completed M5 work

- Events reads only ready `state.data.events`; loading/error/unavailable no longer manufacture rows. Empty ready response stays empty. Existing explicit demo loader remains the source of fixtures, with a visible demo badge.
- All six columns and endpoint links retained. Added Event Type, UNKNOWN decision and from/to dates; local inclusive calendar bounds converted to [from, day-after-to), including DST. Device metadata search includes VID/PID/serial/hash. Local ten-row pagination/newest-first regression retained.
- Pure event mapper and five draft DTO fixture tests added. Preserves endpoint/event/time/user/session/device identity and rejects malformed/unsupported payloads without a mock fallback. M1 must approve actual read DTO before live activation.
- Build, lint, **45/45 tests** and Chromium smoke PASS on Windows. Browser checks live unavailable, explicit demo filters/date/reset, route/link behavior and desktop/mobile layout; it does not call a real Admin API.

## Issues and blockers

| ID | Severity / state | Finding | Follow-up |
|---|---|---|---|
| M5-W2-01 | High / FIXED | Live non-ready Events displayed mock history, giving misleading audit data | Fix and regression tests included; verify again after M4 live adapter |
| M5-W2-02 | Integration blocker / OPEN | Admin Events controller/read DTO and shared live fetch adapter absent at baseline | M1 freezes/implements API; M4 integrates auth/read; M5 updates mapper to final DTO |
| M5-W2-03 | Integration blocker / OPEN | Current local filter/pagination cannot establish full history results on a partial server page | M1/M4/M5 agree server query/page/total/sort integration before live activation |
| M5-W2-04 | Execution blocker / OPEN | No real Ubuntu hardware/GUI/A–B/offline run executed in this Windows handoff | M2/M3 provide runtime, M1/M4 live modules, M5 coordinates/reviews evidence |

Initial build/test attempts hit sandbox EPERM; permitted retries passed. Initial browser run hit a CDP test-script duplicate variable declaration; corrected script passed. Raw logs retain both attempts. These initial failures do not represent successful system E2E runs.

## Remaining handoff

M1 provides frozen Admin DTO/route/query/error contract and server test environment. M4 imports mapper into shared fetcher, carries auth/error states, and implements server pagination/filter orchestration. M3 provides enrolled endpoint identities, transport/outbox/apply/ACK traces. M2 provides physical BLOCK/active-user and notification traces with M3 helper startup evidence.

Owner evidence is linked when delivered, under the member's chosen Week 2 folder; a proposed folder path alone is not evidence. M5 then executes the canonical scenarios, records failing/unexecuted cases honestly, and updates this gate table. Final .deb clean install/reboot and final release guides remain Week 3/4.

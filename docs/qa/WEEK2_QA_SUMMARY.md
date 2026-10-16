# Week 2 QA summary — M5

Updated: 2026-10-16 (Asia/Bangkok). Current base: `cd81060b8f5f32e4ffa42e9120e2827d92219204`, branch `huy`, plus working-tree changes. [Inventory](WEEK2_TEST_INVENTORY.md), [plan](WEEK2_INTEGRATION_TEST_PLAN.md), [current live Events verification](week2-evidence/M5_LIVE_EVENTS_VERIFICATION.md). The earlier [mapper-only run](week2-evidence/M5_EVENT_CONTRACT_VERIFICATION.md) and [2026-10-13 frontend report](week2-evidence/M5_FRONTEND_VERIFICATION.md) retain their original scope.

**Current Windows live Events scope: PASS. Week 2 group integration remains OPEN.** The user's live Events request extended the mapper work to shared adapter/session support and server-driven UI controls. Ubuntu scenarios remain deferred/NOT EXECUTED. Week 1 stays CLOSED with historical reports preserved.

M1 now supplies the [published API v0.2](../API.md), Admin Events controller and [backend/HTTP evidence](../member1/WEEK2_SERVER_INTEGRATION.md). The earlier statement that the Admin API is absent is superseded. Owner evidence was reviewed for this summary, not rerun by M5.

## Gate status

| Gate | Owner | Status | Evidence/remaining work |
|---|---|---|---|
| Physical USB event persisted with endpoint/active user/device/decision/time | M2/M3/M1 | NOT EXECUTED | Ubuntu correlated USBGuard/loginctl/Agent/Server/DB run deferred |
| Web shows real backend events | M1/M4/M5 | PARTIAL (Windows HTTP/browser PASS) | Real Spring Boot/H2 → live Web verified with isolated HTTP-seeded QA records; physical USB source correlation deferred. [Evidence](week2-evidence/13-live-events-browser-http.txt) |
| Admin and Agent authentication | M1/M3/M4 | PARTIAL (Admin browser + backend PASS) | Real browser invalid/valid login, CSRF/session/reload/logout verified; actual Agent runtime remains deferred. [M1 Agent HTTP](../member1/week2-evidence/03-agent-auth-http.txt) |
| Per-endpoint Allow/Revoke/version/apply/ACK | M1–M4/M5 | PARTIAL (backend only) | [M1 A/B isolation](../member1/week2-evidence/04-policy-A-B-isolation.txt), [ACK](../member1/week2-evidence/05-policy-version-ack.txt); UI/apply/USBGuard evidence pending, Ubuntu deferred |
| Unknown USB remains BLOCKED during server/network/notifier outage | M2/M3 | NOT EXECUTED | Actual offline/outbox/reconnect and USBGuard state deferred |
| One native BLOCK notification on correct GUI user; exclusions/failures safe | M2/M3/M5 | NOT EXECUTED | Ubuntu GUI/physical notification and negative traces deferred |
| Honest QA statuses, environment and evidence index | M5 | PASS (current handoff) | Plan contains all W2-T01–T18; inventory separates unit/demo from unexecuted real cases |

## Completed M5 work

- Events reads only ready `state.data.events`; loading/error/unavailable no longer manufacture rows. Empty ready response stays empty. Existing explicit demo loader remains the source of fixtures, with a visible demo badge.
- All six columns and endpoint links retained. Added Event Type, UNKNOWN decision and from/to dates; local inclusive calendar bounds converted to [from, day-after-to), including DST. Device metadata search includes VID/PID/serial/hash. Local ten-row pagination/newest-first regression retained.
- Mapper aligned to the published Admin `EventResponse`: canonical `device.interfaceClass`, string `usbDeviceId`, separate receivedAt, unchanged event/user/session attribution and source timestamp precision. Invalid/unsafe IDs or payloads fail the read.
- Added `EventPage` and `mapEventPage()` with preserved server order/page/size/global totals, including last/empty/out-of-range pages and invalid-page rejection.
- Connected live Events in the shared API adapter with cookie session, CSRF login/logout and `/me` restoration. Events queries exact endpoint/user/device identity, server enums and inclusive calendar dates; table uses server totals and 20-row pages. Demo remains separate and performs no Admin HTTP calls. Other management adapters/actions remain unavailable.
- Current Windows verification: build, lint and **60/60 tests PASS**, plus browser regression and real Spring Boot/H2/Chromium integration. Authenticated Agent HTTP seeded 25 QA events into an isolated DB; Web read, pagination, empty/filter/date/reload/logout and mobile layout passed. Browser 403/500/network failures were injected. Raw logs and screenshots are linked in the current report.
- The earlier mapper-only run passed 52 tests; its synthetic fixture/HTTP limitations apply to that run, which is preserved separately.
- Historical 2026-10-13 verification: build/lint, 45 tests and Chromium demo/unavailable smoke PASS. Browser was not rerun for this mapper/model change; its earlier evidence is not a current live Admin API test.

## Issues and blockers

| ID | Severity / state | Finding | Follow-up |
|---|---|---|---|
| M5-W2-01 | High / FIXED | Live non-ready Events displayed mock history, giving misleading audit data | Fix and regression tests included; verify again after M4 live adapter |
| M5-W2-02 | FIXED (Windows Events scope) | Shared adapter/session login and live Events now read actual API responses | M4 reviews shared auth/API changes; remaining management pages have their own scope |
| M5-W2-03 | FIXED (Windows Events scope) | Live table now uses server page/total/query, verified beyond first page | Retain server metadata; local pagination remains demo-only |
| M5-W2-04 | DEFERRED by current user scope | Ubuntu hardware/GUI/A–B/offline scenarios not executed | M2/M3/M5 execute in a later Ubuntu phase; retain NOT EXECUTED |
| M5-W2-05 | FIXED (published API semantics) | Live labels/query use exact identities; date upper bound is one nanosecond before next midnight | H2 persists microsecond precision; input precision/rounding remains a backend storage consideration |

The historical 2026-10-13 logs retain initial sandbox EPERM and browser script attempts plus successful retries. Current tests/build used permitted local execution for child processes and passed; lint passed locally. No Ubuntu test was attempted in the current scope.

## Remaining handoff

Review the shared API/auth/layout changes with M4. The live Events path is implemented and Windows-verified; Endpoint Detail/dashboard/policy management live adapters remain separate work. Current DTO/query/session details are documented in [mapping/handoff](EVENT_VIEW_MODEL_MAPPING.md) and [run instructions](../../frontend/README.md). Production Event History displays whatever the configured backend actually persists; the browser harness seeds only its isolated QA database.

When Ubuntu testing resumes, M3 provides enrolled endpoint/transport/outbox/apply/ACK traces; M2 provides physical BLOCK/active-user and notification traces with M3 helper startup evidence. Those runs are deferred, not prerequisites to completing the current mapper/model work.

Owner evidence is linked when delivered, under the member's chosen Week 2 folder; a proposed folder path alone is not evidence. M5 then executes the canonical scenarios, records failing/unexecuted cases honestly, and updates this gate table. Final .deb clean install/reboot and final release guides remain Week 3/4.

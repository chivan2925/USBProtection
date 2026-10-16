# Event transport to frontend mapping

Owner: M5. Updated: 2026-10-16 (Asia/Bangkok).

The [pure mapper](../../frontend/src/services/mappers/eventMapper.ts) follows **Admin API v0.2** `EventResponse` and `EventPage`. [Twelve fixture contract tests](../../frontend/tests/m5_event_api.test.mjs) verify that boundary. The merged [shared API adapter](../../frontend/src/services/api.ts), auth provider and server-driven Events controls are verified at frontend revision `a9b1799e2920cba8b6d02fc1d7186fbc0bc59825`. [Current post-merge verification](week2-evidence/M5_POST_MERGE_VERIFICATION.md) records 72 tests and three browser suites; [M4/M5 handoff](M4_M5_WEEK2_HANDOFF.md) documents the shared contracts. Ubuntu execution remains deferred.

Sources: [canonical API](../API.md), [Admin read controller/records](../../admin-server/src/main/java/com/group/usbshield/server/event/AdminEventController.java), [serialized device DTO](../../admin-server/src/main/java/com/group/usbshield/server/agentapi/dto/AgentUsbDeviceDto.java), [M2 sample](../agent-contract/sample-usb-event.json), [frontend view model](../../frontend/src/types/event.ts).

| Transport/source | Frontend field | Mapping for API integration |
|---|---|---|
| Persisted event ID | `id` | Positive integer identity, represented as a string in the view model; never generated from a device name. |
| `occurredAt` | `timestamp` | Require valid ISO-8601 with Z/offset; preserve source value. Never substitute receivedAt or current time. |
| `receivedAt` | `receivedAt` | Preserve the offset-bearing arrival timestamp, including fractional precision. Null/missing legacy values remain undefined. |
| `endpointId` | `endpointId` | Preserve for filtering and endpoint detail links. |
| `hostname` | `endpointName` | Display hostname; table falls back to endpointId. |
| `linuxUsername` | `username` | Preserve attribution; missing/null/blank becomes UNKNOWN, matching server behavior. Never use the Admin account or endpoint currentUser. |
| `usbDeviceId` | `usbDeviceId` | Positive persisted device ID represented as a string; use as the source for server `deviceId` filtering and M4's whitelist selection. Null/missing remains undefined. |
| `device.name` | `deviceName` | Display label only; not unique device identity. Null device enrichment remains undefined. |
| `eventType` | `type` | CONNECTED → connected; DISCONNECTED → disconnected. Other frontend values are mock/UI categories needing explicit API agreement. |
| `decision` | `decision` | v0.2 emits ALLOWED → allowed and BLOCKED → blocked. Existing defensive read support for explicit UNKNOWN → unknown is retained; it does not mean v0.2 accepts UNKNOWN query/ingestion values. Missing/unrecognized decision is a mapping error. Demo events may omit decision; “none” differs from UNKNOWN. |
| No canonical `message` field | `message` | Empty string unless a response explicitly supplies display text; never invent an audit message. |
| `linuxUid`, `sessionId`, `sessionType`, `seat` | Same optional fields on view model | Retained for detail/QA correlation; UID must be a nonnegative safe integer and session ID a string. |
| `device.vendorId/productId/serial/hash/interfaceClass` | Same nested fields | Preserve identity, including empty serial. The Agent ingestion sample's `interface` is an input alias; the Admin response serializes `interfaceClass`. Local demo search covers name/VID/PID/serial/hash. |

## Read DTO and failure behavior

`GET /api/admin/events/{id}` returns one `EventResponse`; `GET /api/admin/events` returns an `EventPage`. `mapEvent(unknown)` maps one item, `mapEvents(unknown)` maps an array, and `mapEventPage(unknown)` validates/maps the page envelope. An Agent ingestion response (`success/eventId/receivedAt`) is not a read DTO.

Event/device IDs accept positive safe integer numbers or positive decimal strings. Unsafe numeric IDs are rejected rather than rounded; any future IDs above JavaScript's safe integer range need string serialization from M1. UID is a nonnegative safe integer. Invalid items fail the whole read instead of being dropped or replaced with mocks. CONNECTED/DISCONNECTED are the supported transport event types; other demo categories are not API enums.

The [Admin page fixture](../../frontend/tests/fixtures/admin-events-page.json) is synthetic and follows the controller records and `AgentUsbDeviceDto` serialization. Its mouse attribution matches the unchanged M2 sample: session 21, ALLOWED, the same instant normalized to UTC. Its IDs, totals, receivedAt and second device are test data, not captured HTTP/DB evidence. [Verification and command logs](week2-evidence/M5_EVENT_CONTRACT_VERIFICATION.md) distinguish this run from real integration.

## Server page handoff to M4

`mapEventPage()` returns the `EventPage` view model with the same keys:

```ts
{ content: UsbEvent[], page: number, size: number, totalElements: number, totalPages: number }
```

- `page` remains zero-based; `size` is 1..100; totals are nonnegative safe integers. `totalPages` must match `ceil(totalElements / size)`.
- Content cannot exceed the page size or total; nonempty out-of-range pages are rejected. Empty results (`totalElements=0,totalPages=0`) and an empty page beyond the last page are accepted without fabricated rows/totals.
- Server order is retained. The mapper does not sort, refilter, truncate or infer global totals from `content.length`. One bad item rejects the entire page.
- The shared adapter now calls `mapEventPage()` and live Events uses server-driven table controls with all metadata. A server page is never put through demo local filtering/pagination. Refresh and filter changes cancel previous reads; changing filters resets page zero. A history that shrinks past the current page is reread at its last valid page.

## Date, sort and pagination semantics

- `occurredAt` is the event instant; browser timezone controls presentation and calendar date selection. `receivedAt` is server arrival time, not the Event History timestamp.
- `dateFrom` and `dateTo` include both selected local dates. `getEventDateRange()` produces UTC ISO boundaries **[from local midnight, midnight after to)**. One-sided bounds are supported. Use calendar-day advancement, not a fixed 24-hour duration, across DST. Invalid/reversed ranges match no rows and show a validation message.
- Demo retains local newest-first sorting and ten-row pagination on its complete fixture array. Live Events uses the server's `occurredAt DESC, id DESC` order, 20 rows/page and global totals.
- Live query keys are `endpointId`, `linuxUsername`, `deviceId`, `decision`, `eventType`, `page`, `size`, `from`, `to`. Endpoint/user match exactly; `deviceId` uses persisted `usbDeviceId`, displayed under each USB name. The UI labels this difference from demo text search. Live decision options are ALLOWED/BLOCKED and event types CONNECTED/DISCONNECTED.
- `getEventQuery()` reconciles inclusive API bounds: `from` is local midnight in UTC; `to` is one nanosecond before next local midnight, formed from the previous second plus `.999999999Z`. This includes sub-millisecond stored events without including the next day; DST still uses calendar boundaries. H2 storage precision is distinct: the browser test uses a `.999999Z` stored event, because the timestamp column rounds finer source fractions.
- Loading/error/unavailable render no fixture rows. Demo fixtures flow only through explicit `?demo=1` and make no Admin API requests. The shared AuthProvider guards live routes; an API 401 dispatches session expiry and redirects to Login. Initial `/me` 401 also opens Login; a connection failure during session restoration shows retry instead of assuming authentication. 403/500/network failures clear live rows. Session cookie/CSRF login, `/me` restoration and logout use the shared adapter, never browser storage for credentials.

## Shared session and management contract after merge

- There is one `request()` in `services/http.ts` for events, auth and management. Relative `/api` URLs use `credentials: include`, `cache: no-store`, JSON validation and cancellation. CSRF responses require a nonblank token and canonical `X-CSRF-TOKEN` header; the token remains in memory.
- Login fetches CSRF, POSTs credentials, verifies `/me`, then refreshes the rotated CSRF token. `/me` restoration refreshes CSRF as well. Logout POSTs with the current token and clears it after success. All live pages share AuthProvider; successful login opens Dashboard and Events uses the same provider/layout.
- Live Dashboard/Endpoints/detail/Devices/Whitelist now use M4 adapters. Allow POSTs `{usbDeviceId}` to the selected endpoint; Revoke DELETEs its entry ID. Desired version and applied version remain separate; Admin success alone never means USBGuard applied the change. Settings/global Block retain their declared unavailable capability.
- Vite dev/preview target precedence is `API_PROXY_TARGET`, then `USBSHIELD_API_TARGET`, then `http://127.0.0.1:8080`. Production serves `/api` through the same-origin reverse proxy.

## Remaining integration and evidence boundaries

Mapper, session/auth, live Events controls, dates and the merged M4 management paths have been exercised with real Spring Boot/H2 and Chromium. Management coverage includes A-only Allow/duplicate/Revoke, B isolation, controlled HTTP ACK and restart persistence. HTTP-created QA events in an isolated DB validate Backend → Web; physical USB attribution, actual Agent apply/ACK and native GUI notification remain deferred. Browser 403/500/network failures are explicitly injected. The technical handoff is complete; human M4 acknowledgment is PENDING until recorded by that owner. Update mapper and tests together when the API changes; the [Week 2 plan](WEEK2_INTEGRATION_TEST_PLAN.md) retains Ubuntu scenarios and the [older Events report](week2-evidence/M5_LIVE_EVENTS_VERIFICATION.md) retains its original 60-test scope.

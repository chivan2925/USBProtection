# Event transport to frontend mapping

Owner: M5. Updated: 2026-10-13 (Asia/Bangkok).

Week 1: mapping specification only. Week 2: [pure mapper](../../frontend/src/services/mappers/eventMapper.ts) and [fixture tests](../../frontend/tests/m5_event_api.test.mjs) implemented. **DRAFT read DTO, pending M1 contract freeze; not a live API integration.** M4 owns shared fetching/authentication/pagination and imports M5's mapper after the contract is agreed.

Sources: [M1 protocol](../AGENT_SERVER_PROTOCOL.md), [server request DTO](../../admin-server/src/main/java/com/group/usbshield/server/agentapi/dto/AgentEventRequest.java), [M2 sample](../agent-contract/sample-usb-event.json), [frontend view model](../../frontend/src/types/event.ts).

| Transport/source | Frontend field | Mapping for API integration |
|---|---|---|
| Persisted event ID | `id` | Request DTO has no ID. Admin read API must provide a stable event ID; device name is not identity. |
| `occurredAt` | `timestamp` | Require valid ISO-8601 with Z/offset; preserve source value. Never substitute receivedAt or current time. |
| `endpointId` | `endpointId` | Preserve for filtering and endpoint detail links. |
| `hostname` | `endpointName` | Display hostname; table falls back to endpointId. |
| `linuxUsername` | `username` | Preserve attribution; missing/null/blank becomes UNKNOWN, matching server behavior. Never use the Admin account or endpoint currentUser. |
| `device.name` | `deviceName` | Display label only; not unique device identity. |
| `eventType` | `type` | CONNECTED → connected; DISCONNECTED → disconnected. Other frontend values are mock/UI categories needing explicit API agreement. |
| `decision` | `decision` | ALLOWED → allowed; BLOCKED → blocked; explicit UNKNOWN → unknown/amber badge. Missing/unrecognized transport decision is a mapping error. Existing demo service events may omit decision; “none” filter is distinct from UNKNOWN. |
| No message field in request DTO | `message` | Read API may provide display text; otherwise use an empty string. |
| `linuxUid`, `sessionId`, `sessionType`, `seat` | Same optional fields on view model | Retained for detail/QA correlation; UID must be a nonnegative safe integer and session ID a string. |
| `device.vendorId/productId/serial/hash/interface` | `device.vendorId/productId/serial/hash/interfaceClass` | Preserve identity, including empty serial. Device filter searches name, VID, PID, serial and hash. Name is never a unique identity. |

## Draft shape and failure behavior

The proposed read item uses the unchanged M2 transport field names plus a **persisted `id`**, optional `message` and device enrichment by M1. This is not the Agent ingestion response (`success/eventId/receivedAt`) or a raw JPA entity. `mapEvent(unknown)` maps one item; `mapEvents(unknown)` maps an array. The mapper does not fetch or guess the pagination envelope. M4 extracts its content after M1 freezes the actual response.

Stable positive numeric IDs become strings; large Java Long IDs must arrive as strings to avoid JSON precision loss. A nonblank string ID is accepted. No generated fixture IDs are used in live mapping. Invalid items fail the whole read rather than being silently dropped or replaced with mocks. CONNECTED/DISCONNECTED are the only currently supported transport event types; other demo/UI categories need M1 agreement. Preserve the Week 1 Mouse sample as ALLOWED, session 21.

## Date, sort and pagination semantics

- `occurredAt` is the event instant; browser timezone controls presentation and calendar date selection. `receivedAt` is server arrival time, not the Event History timestamp.
- `dateFrom` and `dateTo` include both selected local dates. `getEventDateRange()` produces UTC ISO boundaries **[from local midnight, midnight after to)**. One-sided bounds are supported. Use calendar-day advancement, not a fixed 24-hour duration, across DST. Invalid/reversed ranges match no rows and show a validation message.
- Sort newest-first by instant, then paginate at ten rows locally. Filter changes reset the page. This is valid only for a complete bounded dataset, including demo fixtures; an empty ready array stays empty.
- **Do not wire one server page into the current local filter/table and claim global search.** M1/M4 must supply server filter/sort/page/total integration (or explicitly guarantee a complete bounded dataset) before live activation. Canonical query names are pending; the UTC boundaries do not define query parameter names.
- Loading/error/unavailable render no fixture rows. Demo fixtures flow only through the existing explicit `?demo=1` loader. The Events badge follows that mode, not object identity. M4 still owns distinguishing HTTP 401/403/500, session expiry and network errors.

## Contract freeze checklist for M1/M4

Confirm Admin route, item ID serialization, nested device/hostname shape, enums and unknown/null behavior; timezone requirements; pagination envelope and totals; filter keys/matching semantics; sort order; auth/CSRF/errors. If the frozen DTO differs from this draft, update mapper and fixtures together. [Week 2 plan](WEEK2_INTEGRATION_TEST_PLAN.md) tracks the remaining integration.

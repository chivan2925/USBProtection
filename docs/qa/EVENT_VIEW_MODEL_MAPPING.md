# Event transport to frontend mapping

Owner: M5. Reviewed: 2026-10-10 (Asia/Bangkok). Status: integration specification; no API adapter implemented in Week 1.

Sources: [M1 protocol](../AGENT_SERVER_PROTOCOL.md), [server request DTO](../../admin-server/src/main/java/com/group/usbshield/server/agentapi/dto/AgentEventRequest.java), [M2 sample](../agent-contract/sample-usb-event.json), [frontend view model](../../frontend/src/types/event.ts).

| Transport/source | Frontend field | Mapping for API integration |
|---|---|---|
| Persisted event ID | `id` | Request DTO has no ID. Admin read API must provide a stable event ID; device name is not identity. |
| `occurredAt` | `timestamp` | Preserve ISO-8601 value. Sorting compares instants; Date filter uses browser-local day. |
| `endpointId` | `endpointId` | Preserve for filtering and endpoint detail links. |
| `hostname` | `endpointName` | Display hostname; table falls back to endpointId. |
| `linuxUsername` | `username` | Preserve attribution, including explicit UNKNOWN. Do not infer a user from endpoint metadata. |
| `device.name` | `deviceName` | Display label only; not unique device identity. |
| `eventType` | `type` | CONNECTED → connected; DISCONNECTED → disconnected. Other frontend values are mock/UI categories needing explicit API agreement. |
| `decision` | `decision` | ALLOWED → allowed; BLOCKED → blocked. Optional decision means no applicable policy decision; explicit unknown values must not silently become “not applicable”. |
| No message field in request DTO | `message` | Read API may provide display text; otherwise use an empty string. |
| `linuxUid`, `sessionId`, `sessionType`, `seat`, device fingerprint fields | Not represented in table view model | Preserve in a transport/detail model during integration; they remain in the M1 contract. |

UI labels decisions ALLOW/BLOCK. Explicit UNKNOWN decisions are not supported by the current frontend decision union; agree on their representation with M1 before enabling live data. Service events have no policy decision. Mock rendering does not establish deserialization or persistence.

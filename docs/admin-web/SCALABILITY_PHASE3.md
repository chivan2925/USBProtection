# USBShield — Phase 3 Scalability (R09–R11)

Baseline: `vinhkhang` HEAD `3b8d0c7d469c7a2d52a5fb34d0529e35766dc955` (2026-11-03). This patch is **not** a PR/merge or Ubuntu E2E acceptance.

## Contracts

- `GET /api/admin/events?page=0&size=20` unchanged: server-side filters, paging and ordering. Device metadata and hostname fallback are now batch-loaded once per page (R09); historical rows missing USB metadata remain visible.
- `GET /api/admin/dashboard/summary?from=<ISO-8601>&to=<ISO-8601>` is a new admin-only aggregate. `from`/`to` define the browser's local-day boundaries, converted to Instants. Fields: `totalDevices`, `blockedDevices`, `allowedDevices`, `recentEvents` (**total audit event count**, not just the latest page), `blockedToday`. Protection/service are still `unknown` without native enforcement evidence.
- `GET /api/admin/device-states?page=0&size=20[&endpointId=...][&status=blocked][&q=...]` is a new admin-only paged inventory. `size` must be 1–100; statuses: `allowed`, `blocked`, `detected`, `disconnected`. Filters/search run on the server before paging. Page metadata follows `EventPage` (`content`, `page`, `size`, `totalElements`, `totalPages`).
- For each endpoint, a canonical USB identity is: nonblank HASH first; otherwise nonblank serial qualified by VID/PID; otherwise the event's own ID (no guessed identity). Latest event is ordered by `(occurredAt DESC, event ID DESC)`. `DISCONNECTED` wins over decision for the display status. The device catalog remains global, but state/allow/revoke remain endpoint-specific.

## Frontend behavior

- Dashboard performs **one** bounded `/events?page=0&size=100` preview read and **one** aggregate read, rather than up to 100 full audit pages. No total-page-stability assertion across HTTP calls. Dashboard labels `recentEvents` as **Tổng sự kiện**.
- Devices and Blocked pages now load a `DevicePage` with server-side status/endpoint/search filters and page controls. They do not derive the entire inventory from a subset of audit events.
- Endpoint details fetch their own scoped DevicePage (`endpointId`) with search and pagination. The Allow dropdown contains only the displayed page of observed USBs with stable HASH; users can search/page to older devices. The recent-events section is explicitly a **maximum of 100**, with a link to full Event History. The latter remains server-paged.
- Whitelist pages still read per-endpoint whitelist APIs, but dashboard/devices pages no longer perform all-endpoint whitelist reads.
- One HTTP request is not a global atomic snapshot of *all* endpoints, history and aggregates; concurrent new events can change counters between requests. The UI must not reject a valid bounded preview solely because a later aggregate changed. For audit completeness, consult the paged Event History.

## Tests and limitations

- Server `AdminScalabilityIntegrationTest`: R09 SQL statement-count regression with 100 different devices, and R10 10,050-event H2 fixture with bounded API page reads.
- Frontend `phase3_scalability.test.mjs`: >10,000 events, event arrival between HTTP requests, server-paged device filters, alongside updated M4 fixture contract.
- Release gates: both Maven modules, frontend `npm test`, `npm run lint`, `npm run build`, integration/browser regression on the candidate SHA. Tests on Windows do not replace native Ubuntu acceptance.
- The aggregate and device page queries use a SQL window over audit records. This removes the browser's 10k-event hard cap and N+1 event mapping, but is **not** a constant-time materialized view. Before production-scale deployment, load-test at realistic event counts and consider maintaining indexed endpoint/device current-state projections if query latency becomes unacceptable.
- S01 remains deferred until HTTPS rollout; S03 Ubuntu hardware E2E remains deferred until Windows findings close. No changes to R06–R08/R12–R13.

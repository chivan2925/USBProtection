# Admin Web — Week 1 UI Skeleton

> **Owner:** Member 4  
> **Scope:** Week 1  
> **Framework:** React + Vite + TypeScript  
> **Location:** `frontend/` (maps to `admin-web/` in blueprint)

---

## 1. Implemented screens

| Screen | Route | File | Status |
|---|---|---|---|
| Login | `/login` | `src/pages/Login.tsx` | ✅ Done |
| Dashboard | `/dashboard` | `src/pages/Dashboard.tsx` | ✅ Done |
| Endpoints list | `/endpoints` | `src/pages/Endpoints.tsx` | ✅ Done |
| Endpoint Detail | `/endpoints/:id` | `src/pages/Endpoints.tsx` (`EndpointDetail`) | ✅ Done |
| Event History | `/events` | `src/pages/Events.tsx` (M5 owner) | ✅ Done |
| Devices | `/devices` | `src/pages/Devices.tsx` | ✅ Done |
| Whitelist | `/whitelist` | `src/pages/Devices.tsx` (mode=whitelist) | ✅ Done |
| Blocked | `/blocked` | `src/pages/Devices.tsx` (mode=blocked) | ✅ Done |
| Settings | `/settings` | `src/pages/Settings.tsx` | ✅ Done |

---

## 2. Routing

Router lives in [`src/App.tsx`](../frontend/src/App.tsx):

```text
/login               → <Login />
/dashboard           → <Dashboard />
/endpoints           → <Endpoints />
/endpoints/:id       → <EndpointDetail endpointId={id} />
/events              → <Events />          (M5 route)
/devices             → <Devices mode="devices" />
/whitelist           → <Devices mode="whitelist" />
/blocked             → <Devices mode="blocked" />
/settings            → <Settings />
```

Layout shell: [`src/layouts/AdminLayout.tsx`](../frontend/src/layouts/AdminLayout.tsx)

Navigation groups:
- **TỔNG QUAN**: Dashboard, Endpoints
- **QUẢN LÝ USB**: Devices, Whitelist, Blocked
- **HỆ THỐNG**: Event Logs, Settings

---

## 3. Mock-data assumptions

Week 1 uses mock data files at `src/mocks/`:

| File | Owner | Content |
|---|---|---|
| `src/mocks/endpoints.ts` | M4 | LAB-PC-01 (online), LAB-PC-02 (offline) |
| `src/mocks/dashboard.ts` | M4 | Summary stats |
| `src/mocks/events.ts` | M5 | Event history rows |

> The shipped application reads from `protectionApi.getSnapshot()` in `src/services/api.ts`.
> That adapter currently throws `BackendUnavailableError` until M1 Admin Server API is live.
> Mock files are **not imported by the application** — they are reference data for tests and Week 2 integration.

---

## 4. Per-endpoint whitelist UX

**Core business rule:** whitelist is **per-endpoint**, not global.

Implemented in [`src/components/endpoint/EndpointWhitelistPanel.tsx`](../frontend/src/components/endpoint/EndpointWhitelistPanel.tsx):

- Button label: **"Allow on this endpoint"** ✅
- Button label: **"Revoke on this endpoint"** ✅
- No label "Allow globally" or "Allow on all machines" ✅

The panel filters `entries` by `endpointId` so only entries belonging to that machine are shown.

Mock data in `src/mocks/endpoints.ts` demonstrates:
```text
LAB-PC-01 → Kingston DataTraveler 2.0 is whitelisted (wl-01)
LAB-PC-02 → Kingston is NOT whitelisted (different endpoint policy)
```

---

## 5. Component ownership

| Component | Owner | Note |
|---|---|---|
| `components/endpoint/EndpointWhitelistPanel.tsx` | M4 | Required |
| `components/endpoint/EndpointStatusBadge.tsx` | M4 | Visual primitive; M5 may consume |
| `components/event/EventTable.tsx` | M5 | M4 must not redefine |
| `components/event/EventFilters.tsx` | M5 | M4 must not redefine |

---

## 6. Week 2 API integration points

When M1 Admin Server is ready:

| Feature | API endpoint | Notes |
|---|---|---|
| Snapshot data | `GET /api/snapshot` | Replace `BackendUnavailableError` in `api.ts` |
| Endpoint list | `GET /api/endpoints` | Map to `Endpoint[]` from `services/models.ts` |
| Event history | `GET /api/events` | Map to `UsbEvent[]` |
| Allow device | `POST /api/endpoints/:id/whitelist` | Per-endpoint |
| Revoke device | `DELETE /api/endpoints/:id/whitelist/:wlId` | Per-endpoint |
| Auth/login | `POST /api/auth/login` | Login.tsx currently no-ops |

---

## 7. Known Week 1 limitations

- Auth is a wireframe — no real token handling.
- All data shown in the UI comes from the backend snapshot; when backend is unavailable a `ResourceNotice` banner is shown.
- `EndpointWhitelistPanel` callbacks (`onAllow`, `onRevoke`) are wired but currently call `BackendUnavailableError` path.

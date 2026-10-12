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

Endpoints list includes separate **Endpoint ID**, **Policy version**, and **Last Applied Version** columns, alongside machine name/hostname, OS, status, current user, last seen, and the detail link. `Last Applied Version` reads `appliedPolicyVersion` from the frontend view model and displays `—` when absent; it does not fall back to the assigned policy version.

---

## 2. Routing

Router lives in [`src/App.tsx`](../../frontend/src/App.tsx). Routes use the URL hash (for example, `#/endpoints`):

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

Layout shell: [`src/layouts/AdminLayout.tsx`](../../frontend/src/layouts/AdminLayout.tsx)

Navigation groups:
- **TỔNG QUAN**: Dashboard, Endpoints
- **QUẢN LÝ USB**: Devices, Whitelist, Blocked
- **HỆ THỐNG**: Event Logs, Settings

---

## 3. Mock-data assumptions

Week 1 provides an explicit, read-only demo mode using mock data files at `src/mocks/`:

| File | Owner | Content |
|---|---|---|
| `src/mocks/endpoints.ts` | M4 | LAB-PC-01 (online), LAB-PC-02 (offline), per-endpoint whitelist and assigned/applied policy versions |
| `src/mocks/dashboard.ts` | M4 | Reference summary fixture; demo summary is computed from demo devices, endpoints and events |
| `src/mocks/events.ts` | M5 | Event history rows |

- By default, the application reads from `protectionApi.getSnapshot()` in [`src/services/api.ts`](../../frontend/src/services/api.ts). That adapter currently throws `BackendUnavailableError` until the Admin monitoring API is integrated, so the UI shows a backend-unavailable notice.
- Open `?demo=1#/dashboard` using **Mở demo** in the console or **Xem demo Admin Web** on Login. [`src/services/demo.ts`](../../frontend/src/services/demo.ts) imports the endpoint/whitelist and event fixtures, maps them to `Snapshot`, and computes the dashboard summary. Endpoint mapping includes `endpointId` → `id`, `lastSeen` → `lastSeenAt`, and `appliedPolicyVersion`.
- Demo data supports Dashboard, Endpoints list/detail, Devices, Whitelist, Blocked, and Event History. The banner identifies **Chế độ demo · Dữ liệu mock**; **Refresh dữ liệu** reloads the fixtures, and **Thoát demo** returns to the default backend path.
- Demo mode does not send HTTP requests, create a login session, or modify USB policy on real endpoints.
- Event History also has its own Week 1 mock fallback when the snapshot is unavailable.

---

## 4. Per-endpoint whitelist UX

**Core business rule:** whitelist is **per-endpoint**, not global.

Implemented in [`src/components/endpoint/EndpointWhitelistPanel.tsx`](../../frontend/src/components/endpoint/EndpointWhitelistPanel.tsx) and integrated into `EndpointDetail` in [`src/pages/Endpoints.tsx`](../../frontend/src/pages/Endpoints.tsx):

- Button label: **"Allow on this endpoint"** ✅
- Button label: **"Revoke on this endpoint"** ✅
- No label "Allow globally" or "Allow on all machines" ✅

Endpoint Detail passes `state.data.whitelist` and the selected `endpointId` to the panel. The panel filters `entries` by `endpointId` so only entries belonging to that machine are shown. It is rendered only when the endpoint is available; a missing endpoint or unavailable snapshot does not display a misleading empty whitelist.

The integrated panel is read-only: Allow/Revoke buttons are disabled, no mutation callbacks are supplied, and a message explains that the per-endpoint whitelist API is pending.

Mock data in `src/mocks/endpoints.ts` demonstrates:
```text
LAB-PC-01 → Kingston DataTraveler 2.0 is whitelisted (wl-01)
LAB-PC-02 → Kingston is NOT whitelisted (different endpoint policy)
```

---

## 5. Component ownership

| Component | Owner | Note |
|---|---|---|
| `components/endpoint/EndpointWhitelistPanel.tsx` | M4 | Integrated in Endpoint Detail; read-only pending API |
| `components/endpoint/EndpointStatusBadge.tsx` | M4 | Visual primitive; M5 may consume |
| `components/event/EventTable.tsx` | M5 | M4 must not redefine |
| `components/event/EventFilters.tsx` | M5 | M4 must not redefine |

---

## 6. Week 2 API integration points

When M1 Admin Server is ready:

| Feature | API endpoint | Notes |
|---|---|---|
| Snapshot data | `GET /api/snapshot` | Replace `BackendUnavailableError` in `api.ts` |
| Endpoint list | `GET /api/endpoints` | Map to `Endpoint[]` from `services/models.ts`, including ID, assigned policy version and last applied version |
| Event history | `GET /api/events` | Map to `UsbEvent[]` |
| Allow device | `POST /api/endpoints/:id/whitelist` | Per-endpoint |
| Revoke device | `DELETE /api/endpoints/:id/whitelist/:wlId` | Per-endpoint |
| Auth/login | `POST /api/auth/login` | Login inputs and submit are disabled pending authentication support |

These Admin API routes are proposed integration points; they are not an implemented backend contract. The existing Agent controllers do not supply Admin monitoring, authentication, or whitelist APIs.

---

## 7. Known Week 1 limitations

- Auth is a wireframe with disabled credentials/submit and no real token handling. Opening demo does not authenticate the user.
- Admin snapshot integration is pending. The default mode shows a `ResourceNotice` banner when backend data is unavailable; explicit demo mode shows fixture data, and Event History has a Week 1 mock fallback.
- The integrated `EndpointWhitelistPanel` has disabled Allow/Revoke buttons and no mutation callbacks wired. Week 2 must supply those callbacks after the per-endpoint API is agreed and integrated.

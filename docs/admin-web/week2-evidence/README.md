# M4 Week 2 — Execution report

Date: 2026-10-16, Asia/Saigon. Windows, Java 21.0.9, Node.js 24.21.0,
Chrome headless, Vite production build. Baseline HEAD:
`177a4c8c4a9290d8b5cd4d17ce86444516e1c008`, plus uncommitted M4 changes.
Source: [SHA-256 manifest](05-source-sha256.txt).

**M4 Admin Web + real M1 backend: PASS. Whole Week 2 Gate remains open.**
Previous statement that this repository lacks Admin APIs is obsolete.
Tests now run real Spring Boot/Spring Security and file-backed H2.
Enrollment/event/ACK inputs are synthetic; actual M3 Agent and physical USB
execution remain distinct requirements.

## Executed checks

| Command | Result | Evidence |
|---|---|---|
| npm run build | PASS, exit 0 | [build](01-frontend-build.txt) |
| npm run lint | PASS, exit 0 | [lint](02-frontend-lint.txt) |
| npm test | PASS, 57/57 | [tests](03-frontend-tests.txt) |
| npm run test:browser | PASS, HTTP fixture regression | [browser](04-browser-smoke.txt) |
| npm run test:live | PASS, Spring Security/H2 + Chrome | [live browser](06-live-backend-browser.txt) |

Runtime: [Spring Boot log](07-live-server-runtime.txt). Test starts M1 JAR with
isolated H2, random credentials and loopback HTTP; restarts against same file.
Existing project database untouched. No M1/M2/M3 source changes needed.

## M4 test matrix

| ID | Executed scope | Status |
|---|---|---|
| M4-W2-01 | Chrome wrong/right login, HttpOnly session, logout, protected API 401 | PASS (real backend) |
| M4-W2-02 | Reload verifies /me; restart expires session; login again | PASS (real backend) |
| M4-W2-03 | Empty H2 gives zero counts; persisted synthetic event drives metrics | PASS (real backend, synthetic event) |
| M4-W2-04 | Endpoint list/detail, hostname, desired/applied versions | PASS (real backend) |
| M4-W2-05 | Web POST usbDeviceId adds HASH to A; duplicate no version bump | PASS (real backend) |
| M4-W2-06 | Revoke A; desired 1 → 2 → 3; B versions/rules unchanged | PASS (real backend) |
| M4-W2-07 | Real CSRF 403, unauthorized 401, cross-endpoint revoke 404, future ACK 409; UI 403/409/5xx/network via fixture | PASS (scope shown) |
| M4-W2-08 | Empty H2 and fixture stay empty, no mock fallback | PASS |
| M4-W2-09 | Explicit demo read-only, no HTTP/mutations/session | PASS (fixture/browser) |
| M4-W2-10 | Build/lint/57 tests; fixture desktop/mobile; live mobile; M5 Events | PASS |

A before Allow: desired/applied **1/0**. After Allow: **2/0**.
Controlled HTTP ACK version 2: **2/2**. After Revoke: **3/2**.
After restart: **3/2**, whitelist empty, one persisted synthetic event.
Admin 2xx never changes applied version by itself. Exact IDs/versions in live log.

Fixed contract mismatches: `/api/auth/csrf` `{token,headerName}` with rotation;
event `page` envelope; Allow `{usbDeviceId}` with HASH only; whitelist without
`createdAt` renders `—` instead of failing.

## Real backend screenshots

Real Spring Boot/H2 with synthetic devices/events. Hostnames containing `ubuntu`
are test labels; server/browser ran on Windows.

- [Invalid login](m4-live-login.png)
- [Empty database](m4-live-empty.png)
- [Dashboard](m4-live-dashboard.png)
- [Allow A, desired 2 / applied 0](m4-live-allow-a.png)
- [Controlled HTTP ACK, not actual Agent](m4-live-http-ack.png)
- [Revoke A, desired 3 / applied 2](m4-live-revoke-a.png)
- [M5 Events from persisted synthetic input](m4-live-events.png)
- [Mobile](m4-live-mobile.png)

Other m4-*.png without live remain fixture regression screenshots.

## Remaining execution

| Scope | Status | Evidence needed |
|---|---|---|
| M1 Admin login/session/CSRF, endpoints/events/whitelist/H2 persistence | PASS | Linked above |
| Authenticated policy fetch/ACK contract and UI | PASS (controlled HTTP client) | Linked above; no Agent process |
| M3 Agent applies policy through M2 and sends ACK | NOT EXECUTED | Ubuntu Agent/USBGuard logs correlated with server/UI |
| Physical USB Allow/Revoke and physical event → Web | NOT EXECUTED | Ubuntu hardware, active user/session, USBGuard/device state |
| Native BLOCK notification | NOT EXECUTED | Correct graphical user; one notification per eligible Insert |

WSL inspection outside sandbox was not authorized. HTTP/fixture passes do not
prove hardware behavior. M2/M3 supply physical evidence; M5 owns whole-system
signoff. [Run/handoff guide](../WEEK2_ADMIN_API_INTEGRATION.md).

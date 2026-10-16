# Week 2 integration test plan — M5

Owner: M5. Created: 2026-10-13 (Asia/Bangkok). Baseline: `8b644eafc7aa3651b92e7a6746f4f22a4f1ba65e`.

Current execution scope (2026-10-16): the user deferred Ubuntu tests, then requested live Event History. M5 completed the mapper/model/page contracts and connected Events through the shared adapter, with necessary Admin login/session support. Windows Spring Boot/H2/Chromium execution now covers persisted Backend → Web, query/page/date controls and auth. HTTP-seeded QA data and injected browser faults are labeled in [current verification](week2-evidence/M5_LIVE_EVENTS_VERIFICATION.md). The full scenarios below remain the plan; deferred physical Ubuntu cases stay NOT EXECUTED.

Source: the provided `USBShield_Week2_File_Ownership_M1_M3_M4_M5.md`, sections 1, 3 and 7. This document is an execution plan, not evidence that Week 2 has passed. [Inventory](WEEK2_TEST_INVENTORY.md) records runs; [summary](WEEK2_QA_SUMMARY.md) records gates/blockers.

## Scope and prerequisites

Required: real Ubuntu USB event to persisted Server event to Web row; Admin/Agent auth; per-endpoint Allow/Revoke and desired/applied/ACK consistency; offline fail-closed and retry; exactly one BLOCK notification on the correct active GUI user. Week 1 stays closed for the accepted scope. Final .deb clean install/reboot and release guides are outside this Week 2 gate.

| Prerequisite | Owner | Ready when |
|---|---|---|
| P1 — Admin/Agent API contract and running server | M1 | Routes, DTOs, enums, auth/CSRF, errors, filters/paging frozen; H2 inspection available; credentials distributed privately |
| P2 — Live authenticated Agent | M3/M2 | Enrolled identity, token, heartbeat, real USB listener, outbox, policy apply and ACK wired; no simulation auto-events in live mode |
| P3 — Ubuntu GUI and physical storage | M2/M3 | Ubuntu 24.04, USBGuard, actual class 08 device with stable identity, active username/UID/session; desktop helper started |
| P4 — Endpoint A/B isolation setup | M1–M4 | Two distinct authenticated endpoints; same device identity tested on each; initial whitelist, desired/applied versions and rules captured |
| P5 — Web live integration | M4/M5 | Admin session, shared adapter, mapper and Events query/page integration; no silent demo fallback |

Record commit/tag for each module, exact environment, Asia/Bangkok timestamp/offset and event/endpoint/device identifiers. Use `occurredAt`, not `receivedAt`, for event time comparison. A screenshot alone does not establish persistence, policy enforcement or user identity.

## Canonical scenarios

| ID / owner | Prerequisites and steps | Expected | Minimum evidence |
|---|---|---|---|
| W2-T01 — M1/M3 | P1/P2. Start live Agent; enroll; heartbeat; inspect registry. Retry a request with invalid/missing token. | Valid endpoint authenticates and lastSeen advances; unauthorized request rejected. | Enrollment/heartbeat HTTP statuses, sanitized Agent/Server logs, endpoint DB row |
| W2-T02 — M2/M3/M1 | P1–P3. Capture loginctl/user session and USB identity. Insert unknown physical class 08 device; correlate USBGuard, outbound payload and persisted event. | Physical USB BLOCKED; correct endpoint, active Linux user/UID/session/device, event type and occurredAt stored. | USBGuard target, loginctl, Agent/Server trace and DB/event ID |
| W2-T03 — M5/M1/M4 | P1–P3/P5 and W2-T02 event. Open live Events; find ID; compare all six columns and endpoint detail link. | One matching persisted event displayed accurately; newest-first by occurredAt, no fixture substitution. | Browser screenshot, Admin GET response, DB row and correlated event ID |
| W2-T04 — M4/M1 | P1/P5. Login with invalid/valid credentials; refresh; logout; call protected API while logged out/expired. | Session behavior correct; unauthorized reads rejected; no fake authenticated view/data. | Sanitized HTTP status/cookie behavior and browser capture |
| W2-T05 — M4/M1 | P1/P4/P5. Record A/B whitelist and versions. Allow device X through A detail; repeat according to frozen duplicate semantics. | Only A changes; controlled desired version change and explicit response; B unchanged. | API request/response, before/after whitelist DB and versions |
| W2-T06 — M3/M2 | P1–P4; W2-T05. A heartbeat/fetch/apply/ACK; reconnect X on A and B. | X genuinely ALLOWED on A; applied version follows successful apply; B permission unchanged. | Policy JSON, USBGuard rules/list-devices, apply/ACK logs and DB |
| W2-T07 — M4/M1/M3/M2 | P1–P5; W2-T06. Revoke X on A; wait fetch/apply/ACK; reconnect. | X genuinely BLOCKED on A, desired/applied versions consistent; B unchanged. | UI/API action, versions, rules and device targets before/after |
| W2-T08 — M1/M3/M5 | P1/P2/P4. Using A credential, request/read/ACK/post for B; test body/header/URL mismatch under frozen contract. | 401/403 or agreed rejection; B event/policy/applied version/rules unchanged. | Sanitized requests/statuses and before/after B DB/rules |
| W2-T09 — M3/M2 | P2/P3. Disconnect network/stop server; insert unknown USB; inspect target and durable spool. | Unknown USB remains BLOCKED; failure does not imply ALLOWED; event queued. | USBGuard offline target, Agent error/spool metadata |
| W2-T10 — M3/M1 | W2-T09/P1. Restore server/network; observe retries; query prior event and check duplicates according to frozen event identity contract. | Queued event persisted with original attribution/time; retry behavior and count consistent with contract. | Queue before/after, retry/Server logs, correlated DB IDs/count |
| W2-T11 — M2/M3/M5 | P1–P3. Insert real BLOCKED class 08 on active GUI; correlate UID/session and timestamp; exercise duplicate callback handling. | Exactly one native notification for the Insert on correct active graphical user; event still delivered. | Screenshot/video with user/UID, trigger/device/time, helper and Server logs |
| W2-T12 — M2/M5 | P2/P3. Exercise ALLOWED storage, HID mouse, Remove, PolicyChanged and PolicyApplied separately. | No blocked-USB popup for any excluded trigger; normal event processing continues. | Per-trigger traces and observation/video; mark injected/unit variants separately |
| W2-T13 — M2/M3/M5 | P1–P3. Exercise UNKNOWN user, no graphical session and notifier/helper failure. | No wrong-user notification; no crash/stall; USBGuard decision unchanged and event persisted/queued. | Session/helper failure logs, USBGuard target, Server row or outbox |
| W2-T14 — M4/M5 | P1/P5. Use live 200 empty, 401, 403, 500 and network failure; refresh/retry. | Empty/error/auth states truthful; no mock fallback, fake success or stale attribution. | Actual HTTP/browser traces; fixture-only checks listed separately |
| W2-T15 — M5 | Demo: endpoint/user/name/VID/PID/serial/hash/type/decision/date filters. Live P1/P5: exact endpointId/linuxUsername/deviceId, canonical type/decision and inclusive local dates; reset; page beyond first page. | Correct full-dataset server result and totals; demo UNKNOWN differs from no decision; local dates include final persisted sub-millisecond value, exclude next midnight. | Unit/fixture logs and actual HTTP/browser query/results, with source/fault injection labeled |
| W2-T16 — M1/M3 | P1/P2. Force apply failure, then ACK network failure; test invalid/negative/future/cross-endpoint ACK. | No false applied success; ACK only after apply succeeds; retry/recovery and validation match contract. | Failing mock/real runs clearly labeled, HTTP/DB versions, apply/ACK logs |
| W2-T17 — M4/M5 | Installed frontend dependencies/local Chromium. Run build/lint/unit/browser commands below. | All commands exit 0; live unavailable has no fixture rows; explicit demo filters/routes/mobile regressions pass. | Fresh terminal logs, environment/time/revision and screenshots |
| W2-T18 — M1/M3/M2 | Maven/Java toolchain; P2/P3 for startup smoke. Run Maven tests; verify non-root service, USBGuard IPC and desktop helper startup on Ubuntu. | Tests pass; services/helper ready with correct access; separate Java-only results from Linux smoke. | Maven output and systemd/IPC/helper evidence |

W2-T15 fixture execution does not imply its live query portion passed. W2-T17 browser smoke currently uses unavailable live state and explicit demo, not a live Admin API. Keep canonical IDs intact; label sub-runs `W2-T14-FE`, `W2-T15-FE`, etc., without replacing the canonical real scenarios or M2-W2-* IDs.

## Runnable frontend checks

From `frontend/`:

```powershell
npm run build
npm run lint
npm test
npm run test:browser
```

Use existing dependencies; `npm ci` only if dependency installation is needed. Browser smoke requires local Chrome/Chromium/Edge (`CHROME_PATH` can select executable), built dist and free port 4173. Script fixes browser timezone to Asia/Bangkok. It launches preview and headless browser and cleans up its own temporary profile. Screenshots under `frontend/.artifacts/` are runtime outputs; selected copies can be kept in QA evidence.

From repo root, when executing M1/M3 tests:

```powershell
mvn -B -f admin-server/pom.xml test
mvn -B -f client-agent/pom.xml test
```

These commands are a plan, not a recorded Maven run in this handoff.

## Evidence and status rules

Classify each run: UNIT, FRONTEND FIXTURE, BROWSER DEMO, HTTP CONTRACT, REAL USB, GUI NOTIFICATION or FULL E2E. PASS requires matching expected/actual with fresh evidence and exit codes. FAIL means executed but expectation failed. NOT EXECUTED means not run. BLOCKED means an identified prerequisite prevents execution; name follow-up owner. Never promote old Windows/simulation evidence to physical Ubuntu E2E.

For each canonical test, record owner/environment/revision/date-time/steps/expected/actual/status/evidence/limitation/follow-up. Preserve initial failed attempts and explain successful retries. Keep secrets and raw tokens/passwords out of evidence. Link M1 `docs/member1/week2-evidence/`, M3 `docs/client-agent/week2-evidence/`, M4 `docs/admin-web/week2-evidence/` and actual M2 owner artifacts when delivered; proposed locations are not proof of delivery.

## Contract and ownership boundaries

M1 owns Admin APIs/DTOs and canonical docs; M4 owns fetch/auth/shared state; M5 owns mapper/Events/filter/QA. M5 does not create a second fetcher. Confirm [mapping contract checklist](EVENT_VIEW_MODEL_MAPPING.md) before enabling live reads, especially server filters/pagination. Local filters operate only on a complete bounded array; never filter one server page as if it were all history.

Full Week 2 closure requires all seven gates in the [QA summary](WEEK2_QA_SUMMARY.md), including real GUI notification. Frontend PASS alone cannot close Week 2.

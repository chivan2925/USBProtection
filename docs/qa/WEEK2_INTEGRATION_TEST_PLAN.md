# Week 2 integration test plan — M5

Owner: M5. Created: 2026-10-13 (Asia/Bangkok). Baseline: `8b644eafc7aa3651b92e7a6746f4f22a4f1ba65e`.

Current execution scope (2026-10-18): M5 completion excluding Ubuntu on `huy`, working tree based on `e7b05fe972f07aa2d304dcd8e6cf21c664f4d085`. [Current verification](week2-evidence/M5_NON_UBUNTU_COMPLETION_20261018.md) records 72 tests, lint/build and all three browser suites. The shared harness stops Windows Java launcher descendants before restart and verifies old-session HTTP 401, cleared rows and reauthentication. Web Allow/Revoke, A/B isolation, controlled ACK, H2 persistence and Backend to Web Events use synthetic HTTP inputs; browser faults are explicitly injected. M3 61-test owner evidence from 2026-10-17 is indexed separately. Physical Ubuntu/GUI scenarios remain NOT EXECUTED; [handoff](M4_M5_WEEK2_HANDOFF.md) separates technical completion from pending human M4 acknowledgment.

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

W2-T15 has fixture and real HTTP/browser query evidence recorded separately. W2-T17 now includes HTTP fixture regression and both real Spring Boot/H2 browser suites. Full physical scenarios retain their complete acceptance criteria even when Windows sub-runs pass. Keep canonical IDs intact; label sub-runs without replacing the canonical real scenarios or M2-W2-* IDs.

## Runnable frontend checks

From `frontend/`:

```powershell
npm run build
npm run lint
npm test
npm run test:browser
npm run test:live
npm run test:browser:live
```

Use existing dependencies; `npm ci` only if installation is needed. Browser tests require local Chrome/Chromium/Edge (`CHROME_PATH`), built dist and run sequentially. Fixture/management uses preview 4173; real Events uses backend 18083 and preview 4174. Real suites also require the built Admin Server JAR and JDK 21+ (`JAVA_HOME`); tested binaries are identified by the environment/manifest. Both suites use isolated H2, synthetic HTTP input and Asia/Bangkok browser timezone; temporary profiles/processes are cleaned up. Selected screenshots are copied from `frontend/.artifacts/` into the new evidence folder.

From repo root, when executing M1/M3 tests:

```powershell
mvn -B -f admin-server/pom.xml test
mvn -B -f client-agent/pom.xml test
```

These commands are a plan, not a recorded Maven run in this handoff.

## Evidence and status rules

Classify each run: UNIT, FRONTEND FIXTURE, BROWSER DEMO, HTTP CONTRACT, REAL USB, GUI NOTIFICATION or FULL E2E. PASS requires matching expected/actual with fresh evidence and exit codes. FAIL means executed but expectation failed. NOT EXECUTED means not run. BLOCKED means an identified prerequisite prevents execution; name follow-up owner. Never promote old Windows/simulation evidence to physical Ubuntu E2E.

For each canonical test, record owner/environment/revision/date-time/steps/expected/actual/status/evidence/limitation/follow-up. Preserve initial attempts and explain retries. Keep secrets and raw tokens/passwords out of evidence. Current owner artifacts are linked from the inventory: M1 `docs/member1/week2-evidence/`, M2 `docs/usbguard/week2-evidence/`, M3 historical Windows runtime in `docs/client-agent/evidence/`, and M4 `docs/admin-web/week2-evidence/`. No current Ubuntu M3 Week 2 runtime package is claimed; a proposed folder is not proof of delivery.

## Contract and ownership boundaries

M1 owns Admin APIs/DTOs and canonical docs; M4 owns fetch/auth/shared state; M5 owns mapper/Events/filter/QA. M5 does not create a second fetcher. Confirm [mapping contract checklist](EVENT_VIEW_MODEL_MAPPING.md) before enabling live reads, especially server filters/pagination. Local filters operate only on a complete bounded array; never filter one server page as if it were all history.

Full Week 2 closure requires all seven gates in the [QA summary](WEEK2_QA_SUMMARY.md), including real GUI notification. Frontend PASS alone cannot close Week 2.

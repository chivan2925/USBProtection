# Week 2 QA summary — M5

Updated: 2026-10-18 (Asia/Bangkok). Branch `huy`; tested working tree based on `e7b05fe972f07aa2d304dcd8e6cf21c664f4d085`, including the Windows Java process-tree fix in the browser harness. [Current verification](week2-evidence/M5_NON_UBUNTU_COMPLETION_20261018.md), [source/environment](week2-evidence/2026-10-18/source-manifest.json), [inventory](WEEK2_TEST_INVENTORY.md), [plan](WEEK2_INTEGRATION_TEST_PLAN.md), [M4/M5 technical handoff](M4_M5_WEEK2_HANDOFF.md). The earlier 2026-10-16 reports remain historical.

**M5 implementation and QA excluding Ubuntu: COMPLETE / PASS. Whole Week 2 integration remains OPEN.** All six frontend verification commands pass on the current working tree. Physical Ubuntu/USBGuard/GUI scenarios remain NOT EXECUTED in this scope. Week 1 historical acceptance is preserved. Human M4 acknowledgment remains PENDING separately from technical completion.

## Gate status

| Gate | Owner | Status | Evidence and remaining work |
|---|---|---|---|
| Physical USB event persisted with endpoint/active user/device/decision/time | M2/M3/M1 | NOT EXECUTED | Correlated Ubuntu USBGuard/loginctl/Agent/Server/DB run remains deferred |
| Web shows persisted backend events | M1/M4/M5 | PARTIAL; Windows HTTP/browser PASS | [Current 25-event run](week2-evidence/2026-10-18/06-browser-events.txt); physical USB source correlation remains deferred |
| Admin and Agent authentication | M1/M3/M4 | PARTIAL; Admin browser and Agent HTTP PASS | [Current management browser](week2-evidence/2026-10-18/05-browser-management.txt), [M1 Agent authorization](../member1/week2-evidence/03-agent-auth-http.txt); actual Ubuntu Agent runtime remains deferred |
| Per-endpoint Allow/Revoke/version/apply/ACK | M1-M4/M5 | PARTIAL; Web/API/H2 and controlled HTTP ACK PASS | [Current management run](week2-evidence/2026-10-18/05-browser-management.txt): A desired 1 -> 2 -> 3, duplicate Allow idempotent, B unchanged, controlled ACK and genuine restart persistence; actual Agent/USBGuard apply remains deferred |
| Unknown USB remains BLOCKED during server/network/notifier outage | M2/M3 | NOT EXECUTED | Actual offline/outbox/reconnect and USBGuard state remain deferred |
| One native BLOCK notification on correct GUI user; safe exclusions/failures | M2/M3/M5 | NOT EXECUTED | Ubuntu GUI/physical notification and negative traces remain deferred |
| QA status, environment, revision and evidence index | M5 | PASS for the requested non-Ubuntu scope | Current source hashes, six logs/screenshots and reviewed M3 61-test evidence are indexed; canonical Ubuntu cases retain NOT EXECUTED |

## Completed implementation and verification

- Event mapping preserves endpoint, Linux user/UID/session, USB identity, source `occurredAt`, separate `receivedAt`, canonical enums and string IDs. UNKNOWN never becomes the Admin username. Invalid records or page metadata fail the read.
- Live Event History uses the shared API adapter and `mapEventPage()`: six columns, endpoint detail links, server order, exact endpoint/user/Device ID filters, canonical type/decision, 20-row pages and server global totals. Calendar dates include the whole selected local day, with timezone/DST and fractional timestamp regression coverage.
- Explicit demo uses local fixture filtering/newest-first/ten-row pagination. Loading, empty, error and unauthorized live reads never substitute mock history. Protected routes and expired sessions redirect to Login through the shared AuthProvider; API payload errors and 403/500/network failures clear rows.
- The merged M4 management adapters now supply live Dashboard, Endpoints/detail, Devices and per-endpoint whitelist Allow/Revoke. Settings and global Block remain unavailable as declared by capabilities. Auth/session/CSRF, shared HTTP errors, cancellation and both Vite proxy variable names are documented in the handoff.
- [Current verification](week2-evidence/M5_NON_UBUNTU_COMPLETION_20261018.md) captures build, lint, **72/72 tests** and all three browser suites. The Windows restart regression is fixed: the test terminates the full Java process tree, verifies old-session HTTP 401, Login with no protected rows, reauthentication and H2 persistence. Management Allow/Revoke, A/B isolation and controlled ACK pass; Events covers 25 synthetic authenticated HTTP inputs, filters/pagination/dates/session/mobile and explicitly injected failures.
- [Test Plan](../TEST_PLAN.md) references the separate Week 2 plan; inventory, summary, mapping and technical handoff now describe the merged implementation. Owner evidence is linked below rather than rewritten.

## Evidence provenance and owner handoff

| Producer | Reviewed or rerun evidence | Interpretation |
|---|---|---|
| M1 | [Server integration report](../member1/WEEK2_SERVER_INTEGRATION.md), [13-test Maven summary](../member1/week2-evidence/test-summary.txt), [A/B policy](../member1/week2-evidence/04-policy-A-B-isolation.txt), [ACK](../member1/week2-evidence/05-policy-version-ack.txt) | Existing owner-run backend evidence reviewed; no new Maven execution in this capture |
| M2 | [Integration report](../usbguard/USBGuard_Week2_Integration.md), [raw Maven output](../usbguard/week2-evidence/11-mvn-test.txt), [M2→M3 contract](../usbguard/WEEK2_HANDOFF_M3.md) | Raw output now present: 40 tests, zero failures/errors/skips, BUILD SUCCESS on 2026-10-14. Older M2 status text still says the raw output is missing; the actual artifact supersedes that statement. Windows unit evidence does not prove Ubuntu/GUI behavior |
| M3 | [Week 2 handoff](../client-agent/WEEK2_AGENT_INTEGRATION.md), [raw Maven log](../client-agent/week2-evidence/01-agent-maven-test.txt) | Reviewed owner-run 2026-10-17 evidence: 61 tests, zero failures/errors/skips, BUILD SUCCESS. Covers transport/credentials, endpoint persistence, heartbeat, outbox and apply/ACK recovery at unit/contract level. The raw log does not identify its OS or Git revision; no new Maven run or Ubuntu/physical/GUI execution is claimed |
| M4 | [Owner execution report](../admin-web/week2-evidence/README.md), [live backend browser](../admin-web/week2-evidence/06-live-backend-browser.txt) | Historical 57-test M4 implementation run; management/browser integration rerun against the merged frontend in the new M5 evidence |
| M5 | [Current completion verification](week2-evidence/M5_NON_UBUNTU_COMPLETION_20261018.md), [source hashes](week2-evidence/2026-10-18/source-manifest.json) | Current `huy` working tree: 72 tests and all three browser suites PASS, including restart/session regression; synthetic HTTP source clearly identified |

The earlier [45-test frontend report](week2-evidence/M5_FRONTEND_VERIFICATION.md), [52-test mapper report](week2-evidence/M5_EVENT_CONTRACT_VERIFICATION.md), [60-test Events report](week2-evidence/M5_LIVE_EVENTS_VERIFICATION.md), and their logs/manifests remain historical records of their original revisions. They are not edited to claim the new run.

## Issues and remaining work

| ID | State | Finding and follow-up |
|---|---|---|
| M5-W2-01 | FIXED and regression verified | Non-ready live Events no longer displays fabricated mock rows |
| M5-W2-02 | FIXED and merge verified | Shared auth/API and management adapters are integrated with live Events; [technical handoff](M4_M5_WEEK2_HANDOFF.md) complete, M4 human acknowledgment not recorded |
| M5-W2-03 | FIXED and regression verified | Live filters/pages use global server results and totals; demo alone keeps local pagination |
| M5-W2-04 | DEFERRED | Ubuntu real Agent, USBGuard apply/revoke, offline/outbox and GUI notification scenarios require M2/M3/M5 execution |
| M5-W2-05 | FIXED for published API semantics | Exact identity filters and inclusive nanosecond query upper bound verified; H2 stored timestamps have microsecond precision |
| M5-W2-06 | DOCUMENTATION COMPLETE | QA reports identify the current working tree/hashes, six verification logs, reviewed M3 61-test evidence and source limitations |
| M5-W2-07 | FIXED (MEDIUM, test harness) | Windows Java launcher descendants now terminate before restart; current browser regression verifies actual session expiry and database persistence |

M5's technical review and review-ready M4 handoff package are complete. A human M4 approval is **PENDING** because no such confirmation is available in the repository; this report does not substitute an automated run for an owner's signature.

When Ubuntu testing resumes, M3 supplies authenticated Agent/transport/outbox/apply/ACK and helper startup traces; M2 supplies physical BLOCK/active-user and notification traces; M5 correlates them with persisted IDs and Web rows. Final .deb clean install/reboot and release guides remain Week 3/4.

The historical [M3 2026-10-12 simulation](../client-agent/evidence/01-agent-server-e2e-live-run.txt) is retained as historical transport evidence. No application authentication code was changed in the restart harness fix.

## Windows preview startup follow-up — 2026-10-20

A later Windows run reported `npm run lint` PASS, `npm run build` PASS and `npm test` **72/72 PASS**, but `npm run test:browser` and `npm run test:live` timed out while waiting for the Vite startup **log text** even though Vite printed the local URL. The outcome of `test:browser:live` was not supplied. [Browser readiness fix](WEEK2_WINDOWS_BROWSER_READINESS_FIX.md) changes the harness gate to an actual HTTP probe via an idempotent one-time migration, accompanied by four additional regression checks. The browser suites have **not** yet been rerun on the patched Windows workspace; do not infer PASS. The earlier 2026-10-18 Windows browser evidence remains historical and unaffected. Ubuntu hardware/service/desktop checks remain **DEFERRED UNTIL AFTER WEEK 4**.

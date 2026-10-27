# USBShield Week 3 demo script — DRAFT

Owner: M5. Prepared: 2026-10-21; updated: 2026-10-24 (Asia/Bangkok). This is a Windows demonstration draft, not a final release rehearsal. [Test plan](qa/WEEK3_INTEGRATION_TEST_PLAN.md), [QA results](qa/WEEK3_QA_SUMMARY.md), [evidence](qa/week3-evidence/README.md).

## Windows preparation

Use a fresh server JAR, frontend production build, local Chrome/Edge, isolated temporary H2 and randomized QA accounts/tokens. Do not use the operator's application database. Set JDK 21+ in `JAVA_HOME`; build and verify with the commands in the test plan. Record revision, toolchain, command results and hashes before presenting.

The browser harnesses run headlessly and create screenshots. For a narrated walkthrough, use the recorded captures or an owner-provided isolated interactive environment. `test:live` and `test:browser:live` start separate databases and do not share their QA accounts/data. Run suites sequentially; each cleans up its processes and temporary runtime.

Current evidence: [24/10 integrated capture](qa/week3-evidence/2026-10-24/integrated-regression/README.md), revision `a0ad421901d5cb67fbe1b27ea9a74c05dfe2c3c1`. The extra packaged-JAR variant demonstrates Events UI/API from one origin without Vite, using HTTP/default runtime settings; it is separate from the checked-in suites and does not demonstrate production HTTPS. Use its [partial-username capture](qa/week3-evidence/2026-10-24/integrated-regression/screenshots/events-username-substring.png) for the current Linux User behavior. [Handoff/gate status](qa/WEEK3_M5_COMPLETION_20261024.md) remains OPEN with the M3 staging-verifier finding and pending owner/group decisions.

## Windows walkthrough, approximately 8–10 minutes

| Step | Presenter action / automated equivalent | Expected observation | Scope statement |
|---|---|---|---|
| 1. Admin login | `npm run test:live`: invalid password, valid login and reload | Clear error for invalid login; Dashboard after success; session restored; CSRF enforced | Real Spring Security/session; random test account |
| 2. Endpoint | Open Endpoints and endpoint A; compare B and desired/applied | Actual API endpoint data, status and independent policy versions | Endpoints enrolled through synthetic HTTP, not Ubuntu Agent |
| 3. Event | Show the synthetic event, then use `npm run test:browser:live` for the isolated Events demonstration | Endpoint, Linux username, USB, event, decision and source timestamp; 25 initial records and 20+5 pages | Real persisted H2 events seeded over authenticated HTTP |
| 4. Allow/Revoke A | In the management suite, Allow on A, repeat, controlled ACK, Revoke | A desired/applied progresses 1/0 → 2/0 → 2/2 → 3/2; duplicate does not bump version | ACK is an HTTP test input; no USBGuard apply is demonstrated |
| 5. B unaffected | Compare B whitelist/version after each action | B remains unchanged; actions are endpoint-specific | Web/API isolation, not physical enforcement |
| 6. Outage/recovery | Events suite injects 403/500/network failure, then disables fault injection and Refreshes | Error is explicit; rows disappear; real API data returns; no mock fallback | Browser network fault injection, not Agent fail-closed proof |
| 7. Week 3 attribution | Events suite adds A/UNKNOWN, A/new user and B/other user for a shared no-serial hash | Three added records (28 total); endpoint/USB name-or-ID and partial case-insensitive username filters; UNKNOWN and source fractional timestamp preserved | Synthetic user snapshots; no actual Linux session switching |
| 8. Session/restart/mobile | Management suite restarts its real backend; Events suite checks 375px and logout | Old session expires, reauthentication works, H2 state persists; mobile Events fits; protected rows disappear after logout | Server restart is not client OS reboot |

Use `npm run test:browser` separately to demonstrate fixture/error/readonly demo coverage. Announce the fixture scope explicitly. The Events suite's no-serial/UNKNOWN/A/B captures are indexed under Week 3 evidence after actual execution. If a suite fails, show its actual log and open issue rather than presenting earlier Week 2 PASS as a current result.

## Ubuntu demonstration — deferred until after Week 4

Requires M2/M3, Ubuntu 24.04 with USBGuard, physical class-08 USB, active GUI users and two endpoints for isolation. M1/M4 supply integrated server/Web artifacts; M5 correlates evidence. Current execution: **NOT EXECUTED / DEFERRED_UBUNTU**.

1. Clean install the built `.deb`, configure server/enrollment, start live service and show endpoint ONLINE.
2. Insert unknown physical storage: show real BLOCK, active Linux user and the correlated persisted Web event.
3. Allow the device on A, verify actual apply/ACK and ALLOW; show B unaffected. Revoke A and verify actual BLOCK after reconnect.
4. Show real no-serial identity by USBGuard hash and real user switching/UNKNOWN behavior.
5. Interrupt server/network; confirm physical fail-closed behavior, durable spool and later delivery/recovery.
6. Show one correct-user native popup, notification exclusions and safe helper failure.
7. Reboot and demonstrate retained credentials/policy/service/outbox; complete uninstall/reinstall checks with documented data retention.

Do not claim hardware enforcement, native notification, clean install or reboot PASS from Windows fixtures/HTTP/mocks. Final demo rehearsal and final guides remain Week 4 work; group Windows acceptance and Ubuntu acceptance are recorded separately by the integration owner.

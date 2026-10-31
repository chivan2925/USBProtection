# TEST PLAN v0.1

> **Owner:** Member 5  
> **Project:** USBShield Ubuntu  
> **Scope:** Week 1 — Prove Core + Freeze Client/Server Contract  
> **Version:** v0.1

---

Updated: 2026-10-12 (Asia/Saigon).

M2 PASS means M2-reported evidence, not an independent Ubuntu runtime rerun. See [M2 verification](qa/WEEK1_M2_POC_VERIFICATION.md). Frontend PASS applies to mock/fixture checks in [frontend verification](qa/evidence/M5_FRONTEND_VERIFICATION.md). Missing execution evidence stays NOT EXECUTED. T01–T38 are the canonical Test Plan IDs used by [the inventory](qa/WEEK1_TEST_INVENTORY.md).

## 1. Week 1 Test Inventory

| ID | Test Case | Owner | Status | Notes |
|---|---|---|---|---|
| T01 | Unknown USB Flash Disk → BLOCK | M2 | **PASS (M2 reported)** | Kingston DataTraveler 2.0, interface 08:06:50 |
| T02 | Physical USB Mouse → ALLOW | M2 | **PASS (M2 reported)** | USB Optical Mouse, class 03:01:02 |
| T03 | Mass Storage class 08 detected | M2 | **PASS (M2 reported)** | `with-interface 08:06:50` confirmed |
| T04 | Specific Allow (permanent whitelist) | M2 | **PASS (M2 reported)** | `usbguard allow-device --permanent` |
| T05 | Reconnect blocked device stays BLOCK | M2 | **PASS (M2 reported)** | 3 reconnect attempts |
| T06 | Reboot persistence of permanent allow | M2 | **PASS (M2 reported)** | Rule survives reboot |
| T07 | USBGuard event stream (watch) | M2 | **PASS (M2 reported)** | Remove→Insert→PolicyChanged→PolicyApplied |
| T08 | Active local Ubuntu user resolved | M2 | **PASS (M2 reported)** | `usbdev`, UID 1000, wayland session |
| T09 | Second user attribution | M2 | **PASS (M2 reported)** | `student01`, UID 1001, session 19 |
| T10 | Resolver ambiguity → UNKNOWN | M2 | **PASS (M2 reported, SIMULATED)** | Two equally valid candidates → UNKNOWN |
| T11 | Java USBGuard parser — massStorage=true | M2 | **PASS (M2 reported)** | DataTraveler parsed correctly |
| T12 | Sample Agent event contract complete | M2 | **PASS (M2 reported)** | endpoint+user+USB+decision in JSON |
| T13 | Admin Server skeleton boots | M1 | **PASS (Windows PoC)** | [Server verification](member1/WEEK1_SERVER_VERIFICATION.md): standalone JAR boot + H2 File Mode |
| T14 | Agent heartbeat POST reaches Admin Server | M3+M1 | **PASS (Windows PoC)** | Actual Agent JAR enrolls and sends heartbeat; server runtime log |
| T15 | Agent event POST deserialized by server | M3+M1 | **PASS (Windows PoC, simulated event)** | Unchanged M2 sample HTTP accepted; actual Agent simulated event persisted; 4 server tests pass |
| T16 | Admin Web boots | M4 | **PASS** | React/Vite app starts |
| T17 | Login wireframe renders | M4 | **PASS** | username/password/button present |
| T18 | Dashboard renders stat cards | M4 | NOT EXECUTED (dedicated assertion) | Browser checks route/heading; no dedicated stat-card assertion in the recorded suite |
| T19 | Endpoints list renders rows | M4 | **PASS** | hostname, status, policy version |
| T20 | Endpoint Detail renders per-endpoint info | M4 | **PASS** | |
| T21 | Per-endpoint whitelist — "Allow on this endpoint" | M4 | **PASS** | Label verified in test |
| T22 | Per-endpoint whitelist — "Revoke on this endpoint" | M4 | **PASS** | Label verified in test |
| T23 | Event History table renders | M5 | **PASS** | Mock data in Week 1 |
| T24 | Event History columns: Timestamp, Endpoint, Linux User, USB, Event, Decision | M5 | **PASS** | All columns present |
| T25 | Event History filter by decision | M5 | **PASS (MOCK)** | UI filter works on mock |
| T26 | Multiple USB Mass Storage independent handling | M2 | **NOT EXECUTED** | Only one device available |
| T27 | Real multi-session ambiguity (live) | M2 | **NOT EXECUTED** | Simulated only |
| T35 | Physical USB Keyboard ALLOW | M2 | NOT EXECUTED | Mock keyboard UI is not hardware evidence |
| T36 | Revoke per-endpoint permission and enforce BLOCK | M1+M2+M3 | NOT EXECUTED / PLANNED WEEK 2 | Functional enforcement; distinct from T22 button-label test |
| T37 | Event field filters, local date and reset | M5 | PASS (MOCK) | eventFilters.test.mjs and browser smoke |
| T38 | Whitelist/detail fixture isolation | M4+M5 | PASS (frontend) | components.test.mjs and m4_endpoints.test.mjs |
| T28 | Admin Server H2 schema correct | M1 | **PARTIAL (Week 1)** | H2 connection/JPA context and persisted endpoint/event/device verified; full schema/constraint review remains Week 2 |
| T29 | Agent → Server live heartbeat E2E | M1+M3 | PLANNED WEEK 2 (Ubuntu) | Windows real HTTP transport covered by T14; Ubuntu deployment remains pending |
| T30 | Agent → Server live event E2E | M1+M3 | PLANNED WEEK 2 (physical USB/Ubuntu) | T15 proves HTTP with sample/simulation, not physical USB attribution |
| T31 | Per-endpoint policy sync | M1+M2+M3 | PLANNED WEEK 2 | |
| T32 | Admin Web live API integration | M1+M4 | PLANNED WEEK 2 | |
| T33 | USBGuard block on real hardware (no whitelist) | M2 | PLANNED WEEK 2 | Production integration |
| T34 | .deb package install/uninstall | M3 | PLANNED WEEK 3 | |

---

## 1.1. Allow/Revoke functional acceptance

Precondition: use a storage device with stable identity, registered endpoints A/B and an active user. Record initial policy versions and device targets on both endpoints.

| Test | Action | Expected result | Execution status |
|---|---|---|---|
| T04 local permanent allow | Allow the specific device permanently in USBGuard; reconnect | Device becomes ALLOW; the specific rule persists. This local PoC does not establish central per-endpoint sync. | PASS (M2 reported); runtime rerun pending |
| T31 central per-endpoint allow | Add the device to A's whitelist and apply its policy | Device becomes ALLOWED on A with intended identity and policy version; B's whitelist and permission remain unchanged. | PLANNED WEEK 2 |
| T36 central per-endpoint revoke | Remove the same entry from A and apply the new policy; reconnect | Device becomes BLOCKED on A; removed permission stays absent after reconnect. B remains unchanged. Record policy versions, USBGuard targets and endpoint/user/device/decision event. | NOT EXECUTED / PLANNED WEEK 2 |

T21/T22 verify UI wording only; they do not verify API calls, policy synchronization or device enforcement.

## 2. Week 1 Gate Criteria

| Gate | Owner | Result |
|---|---|---|
| Unknown Flash BLOCK | M2 | PASS (M2 reported; evidence reviewed) |
| Physical mouse ALLOW | M2 | PASS (M2 reported; evidence reviewed) |
| Keyboard ALLOW | M2 | NOT EXECUTED; physical keyboard evidence missing |
| Active Ubuntu username/session | M2 | PASS (M2 reported; evidence reviewed) |
| Sample endpoint+user+USB+decision | M2 | PASS (M2 reported; evidence reviewed) |
| Admin Server skeleton | M1 | PASS (Windows PoC; boot/H2 evidence) |
| Contract freeze v0.1 | M1 | PASS (sample/DTO/docs v0.1); production interoperability remains Week 2 |
| Agent shell starts | M3 | PASS (Windows standalone JAR) |
| Heartbeat/event POST PoC | M3+M1 | PASS (real HTTP; simulated USB event) |
| Admin Web skeleton | M4 | ✅ PASS |
| Event History mock | M5 | ✅ PASS |
| Test Plan v0.1 | M5 | ✅ This document |

---

## 3. Test Evidence Sources

| Component | Evidence |
|---|---|
| USBGuard PoC | `docs/usbguard/USBGuard_POC.md` |
| USBGuard evidence | `docs/usbguard/evidence/` |
| Sample event contract | `docs/agent-contract/sample-usb-event.json` |
| M1/M3 server and transport PoC | [Server verification](member1/WEEK1_SERVER_VERIFICATION.md), [4-test summary](member1/evidence/test-summary.txt), [server log](member1/evidence/server-runtime.log), [Agent log](member1/evidence/agent-runtime.log) |
| Admin Web tests | `frontend/tests/m4_endpoints.test.mjs` |
| Event History tests | `frontend/tests/m5_events.test.mjs` |
| M2 PoC verification | `docs/qa/WEEK1_M2_POC_VERIFICATION.md` |

---

## 4. Out of Scope — Week 1

```text
Production Agent → Admin Server live event delivery
Per-endpoint policy sync (USBGuard rules pulled from server)
Real multi-session Ubuntu ambiguity test
.deb packaging
Full Admin authentication
```

---

## 5. Planned — Week 2

```text
Full H2 schema/constraint review (Week 1 connection and persistence already verified)
Ubuntu Agent heartbeat and physical USB event reach server (Windows PoC already verified)
Admin Web reads real API data
Per-endpoint whitelist CRUD via API
Production interoperability and authenticated endpoint contract beyond v0.1 PoC
```

## 6. Week 2 execution plan (2026-10-13)

Week 2 uses a separate [integration plan](qa/WEEK2_INTEGRATION_TEST_PLAN.md), [execution inventory](qa/WEEK2_TEST_INVENTORY.md) and [QA summary](qa/WEEK2_QA_SUMMARY.md). Canonical IDs are **W2-T01–W2-T18**; existing T01–T38 and M2-W2-* IDs are unchanged.

Per the Week 2 ownership handoff, the group's accepted Week 1 scope is CLOSED/PASS; the historical evidence and limitations above remain intact. Physical keyboard testing is not a prerequisite for reopening Week 1. Native Ubuntu client BLOCK notification is a Week 2 requirement. Final .deb clean install/reboot and release guides remain Week 3/4.

Frontend unit/fixture and browser-demo results are recorded separately from real HTTP, physical USB, GUI notification and full E2E results. Unexecuted cases stay NOT EXECUTED; new reports link owner evidence rather than copying or rewriting historical evidence.

## 7. Week 3 Windows execution plan (2026-10-21)

Week 3 tracks productization, Event History hardening and Windows regression in a separate [integration test plan](qa/WEEK3_INTEGRATION_TEST_PLAN.md), [test inventory](qa/WEEK3_TEST_INVENTORY.md), [QA summary](qa/WEEK3_QA_SUMMARY.md) and [evidence index](qa/week3-evidence/README.md). Canonical IDs are **W3-F01–W3-F10**, **W3-S01–W3-S02**, **W3-A01–W3-A02**, **W3-P01–W3-P02**, **W3-D01** and **W3-U01–W3-U09**. Earlier IDs and execution records above remain historical.

The 2026-10-21 Event filter/table follow-up adds **W3-F11** for endpoint/USB name-or-ID search, exact Linux username and server-side filtered pagination. Its [separate report](qa/WEEK3_EVENT_NAME_SEARCH_FIX.md) links the new source manifests and reruns; earlier Week 3 execution artifacts remain unchanged.

`WINDOWS_EXECUTABLE` covers Node/npm unit/component/fixture tests, real Spring Boot/H2 HTTP/browser tests using synthetic events and controlled ACK, Maven unit/contract tests, and package/script static validation. Every result records command, date/time in Asia/Bangkok, OS, base Git revision, source hashes, exit status and evidence. A prior Week 2 PASS is not a Week 3 execution result.

All actual Ubuntu USBGuard/physical USB/session switching/native notification/IPC/systemd/clean install/uninstall/reinstall/reboot tests are **DEFERRED UNTIL AFTER WEEK 4**, recorded as `NOT EXECUTED / DEFERRED_UBUNTU`. Unit mocks and HTTP-seeded events do not establish their runtime behavior. The [Demo Script](DEMO_SCRIPT.md) is a Week 3 draft with separate Windows and deferred Ubuntu paths.

M5 may complete its owned implementation/tests and prepare documentation independently. M1–M4 still own contract and implementation acceptance for their components; M1 coordinates the integrated revision and the group decision. The allowed group label is `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED` only after all Windows P0 items are demonstrated. This M5 execution does not automatically close that group gate or declare a final release.

The later Linux User substring correction adds **W3-F12** for partial/case-insensitive username search before counting/pagination, with backward-compatible exact `linuxUsername` API filtering. See the [separate capture](qa/week3-evidence/2026-10-21/username-substring/README.md).

## 8. Week 4 stabilization and release preparation (2026-10-28)

Week 4 uses the [final test matrix and execution inventory](qa/WEEK4_TEST_INVENTORY.md). It maps **Blueprint section 30 BP-T01–BP-T28** and **section 8 DoD-01–DoD-11** to owners, environments, data/suite sources, expected/actual results, tested SHA and status. Current source baseline is `2013ce726ef8cc960b30720435be23e8ccef1903`; it is not an approved Week 4 candidate tag. Fresh execution results require fresh command logs/source manifests; the Week 1–3 tables above retain their historical results.

Week 3 already has [final owner acceptance](qa/WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md): **`PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`**, H05 CLOSED. Historical OPEN wording in older reports is preserved as history and does not reopen the accepted Week 3 gate. Its documented checkout-SHA limitation remains intact.

The Blueprint's T numbers and this document's original Week 1 T01–T38 describe different scenarios. The Week 4 inventory labels the latter **TP1-Txx** only when cross-referencing them, and labels Blueprint requirements **BP-Txx**. For example, BP-T01 is enrollment while TP1-T01 is unknown USB BLOCK; BP-T25 is native notification while TP1-T25 is a mock decision filter. Matching numbers never establish coverage. W2-* and W3-* IDs remain unchanged.

Fresh Windows cases are **W4-F01–F10**, **W4-S01–S02**, **W4-A01–A04**, **W4-P01–P03** and **W4-D01–D03**. They distinguish unit/mock, fixture/browser, real Spring Boot/H2 HTTP with synthetic input, static/build and documentation results. **W4-U01–U28** form the one-to-one Blueprint crosswalk; BP-T06/20/21 can be checked at their stated HTTP level on Windows, while physical USB/Agent/Ubuntu acceptance remains separate. A controlled policy ACK or synthetic user event is labelled accordingly.

M5 runs existing Maven/npm/static/browser checks, verifies Event History fields/filters/paging/source time/no-serial/UNKNOWN and production error handling, updates the [Demo Script](DEMO_SCRIPT.md), and records [evidence/provenance](qa/week4-evidence/README.md), [Event review](qa/WEEK4_EVENT_HISTORY_REVIEW.md) and the [release validation report](qa/WEEK4_RELEASE_VALIDATION.md). Shared frontend acceptance still requires M4 review; final candidate regression needs M1's chosen integrated SHA, frozen contracts and M1–M4 handoffs. Application/build changes after the baseline require affected reruns.

Per the supplied Week 4 schedule, actual Ubuntu 24.04, physical USB, USBGuard daemon/IPC, graphical sessions/native notification, systemd, clean `.deb` install/upgrade/uninstall/reinstall and reboot are **NOT EXECUTED / DEFERRED_UBUNTU until after Week 4**. M5 prepares [executable Ubuntu test sheets](qa/WEEK4_UBUNTU_ACCEPTANCE.md) without inventing their results. Windows evidence never closes those cases.

The eventual group label is **`WEEK 4 — WINDOWS/RELEASE-CANDIDATE PREPARATION PASS; UBUNTU E2E DEFERRED`** only after all Windows preparation gates and owner decisions are satisfied. An independently passing baseline regression or a completed M5 report is not that group decision. `FULL RELEASE PASS` requires the later Ubuntu acceptance and deployment security verification.

## 9. Week 4 post-pull applicability and focused regression (2026-10-30)

The current source snapshot is **S1 `751387bf166c528326ae91c6a16a820a6d8fc24a`**, after M4's final guide/shared-frontend handoff and M2's watch listener hardening. It has not been selected as the final release candidate. The previous section and 28/10 evidence remain a historical execution snapshot on **S0 `2013ce726ef8cc960b30720435be23e8ccef1903`**; their PASS results are not newly executed tests on S1.

The [inventory's current applicability section](qa/WEEK4_TEST_INVENTORY.md) adds **W4-A05** for `UsbGuardWeek4WatchStressTest`: replay dedup across watch reconnect/restarts, Remove/reinsert, policy-event noise, malformed/oversized input, bounded recent keys, CRLF/EOF and daemon recovery. Run the existing Agent suite with the new test and build its JAR, then rerun the packaging static/archive verifier against that current JAR. These Windows mock/static checks support BP-T03/04/15/17/24/28 and DoD-03/09/11 without closing their actual Ubuntu runtime acceptance.

The [post-pull evidence index](qa/week4-evidence/2026-10-30/post-pull-regression/README.md) records fresh commands/results, [build provenance](qa/week4-evidence/2026-10-30/post-pull-regression/build-manifest.json), [JUnit summary](qa/week4-evidence/2026-10-30/post-pull-regression/junit-summary.json), [scope equivalence](qa/week4-evidence/2026-10-30/post-pull-regression/scope-equivalence.json) and [validation](qa/week4-evidence/2026-10-30/post-pull-regression/validation.json). Do not record fresh PASS before the corresponding execution evidence exists. Compare unchanged frontend/server/packaging inputs with S0; retain earlier evidence for equivalent scopes and rerun affected checks if that comparison finds further changes.

Fresh S1 results: Agent **136/136 PASS**, 0 failures/errors/skips, including **9/9** new watch stress tests; executable JAR built and the packaging static/JAR verifier PASS. [01 Agent command](qa/week4-evidence/2026-10-30/post-pull-regression/01-agent-package.json) and [02 packaging command](qa/week4-evidence/2026-10-30/post-pull-regression/02-packaging-static.json) retain exact source/command/result provenance. Unchanged frontend/server/packaging scopes retain the prior evidence by Git source comparison; no fresh frontend/server/browser execution is claimed. [Post-pull validation PASS](qa/week4-evidence/2026-10-30/post-pull-regression/validation.json) verifies both fresh captures, JUnit totals, current Agent JAR checksum, source equivalence, links/hashes and preservation of historical evidence. The optional `.github` path has no tracked entry at either revision and establishes no CI execution.

The [M4 Week 4 handoff](qa/evidence/M4_WEEK4_HANDOFF.md) is **received and technically acknowledged by M5 for the unchanged S1 frontend**, including its reviewed SHA and shared Event adapter/auth/browser harness scope. [M5's post-pull acknowledgment](qa/evidence/M5_WEEK4_POST_PULL_ACK_20261030.md) records the independent source comparison and acceptance limits. That missing-input dependency is closed for this reviewed source. M5 matrix/report/evidence preparation is complete for this post-pull scope. Final-candidate selection/API/security freeze remains with M1; M2's Part 2/3 and M3's final Agent/package/installation handoff remain required owner inputs. [Release validation](qa/WEEK4_RELEASE_VALIDATION.md) records the current disposition. Ubuntu, physical USB, daemon/IPC, GUI, systemd, installation lifecycle and reboot remain **NOT EXECUTED / DEFERRED_UBUNTU**; M5-07's prepared sheets do not claim those executions.

## 10. Integrated Agent and packaging applicability (2026-10-31)

Current snapshot **S2 `0b726304a29a78ac36aefbbc71e40fbd7758ae2e`** receives M2 Part 2 policy/session hardening and M3 HTTP client, POM/Surefire, staging/verifier and final guide/manifest/checklist changes. S0/S1 sections above retain their original dates, counts and tested sources. S2 is not a selected final RC.

The [current matrix](qa/WEEK4_TEST_INVENTORY.md) adds **W4-A06** (12 policy-integrity test methods), **W4-A07** (8 active-session safety methods), **W4-A08** (real production HTTP transport smoke), **W4-P04** (safely prepared real staging plus independent nine-file/JAR byte hashes) and **W4-P05** (Week 4 packaging verifier). Existing Agent cases require a fresh clean package on the integrated source; test-method counts do not become PASS until real Maven/JUnit results exist. The transport smoke records its exact asserted routes/DTOs and synthetic input separately from unit/mock or browser results.

Fresh evidence uses the [31/10 integrated index](qa/week4-evidence/2026-10-31/integrated-agent-packaging/README.md), [JUnit summary](qa/week4-evidence/2026-10-31/integrated-agent-packaging/junit-summary.json), [build manifest](qa/week4-evidence/2026-10-31/integrated-agent-packaging/build-manifest.json), [scope applicability](qa/week4-evidence/2026-10-31/integrated-agent-packaging/scope-equivalence.json), [staging manifest](qa/week4-evidence/2026-10-31/integrated-agent-packaging/staging-manifest.json), [passing HTTP summary](qa/week4-evidence/2026-10-31/integrated-agent-packaging/http-transport-summary-02.json) and [validation PASS](qa/week4-evidence/2026-10-31/integrated-agent-packaging/validation.json). The current validator uses the explicit [run profile](qa/week4-evidence/2026-10-31/integrated-agent-packaging/run-spec.json), verifies five positive captures and retains failed attempt 05; source/build/JUnit/staging/HTTP hashes, links and historical-evidence preservation pass. Current M5 matrix/report/evidence preparation is complete for this scope.

Executed S2 Agent result: **156/156 PASS**, no failures/errors/skips, including **12/12 policy**, **8/8 session** and **9/9 watch** cases; executable JAR built. [01 command metadata](qa/week4-evidence/2026-10-31/integrated-agent-packaging/01-agent-package.json) records the current source/build provenance. Safe staging preparation [02](qa/week4-evidence/2026-10-31/integrated-agent-packaging/02-staging-prepare.json), full Week 4 verifier [03](qa/week4-evidence/2026-10-31/integrated-agent-packaging/03-packaging-week4.json) without skip mode and independent staging integrity [04](qa/week4-evidence/2026-10-31/integrated-agent-packaging/04-staging-integrity.json) all PASS. All **9/9 staged files match current source/JAR bytes**. This does not establish POSIX ownership/modes, a built `.deb` or Linux runtime behavior.

The production HTTP client smoke [06](qa/week4-evidence/2026-10-31/integrated-agent-packaging/06-http-transport.json) PASS covers **15 scenarios, 14 real loopback requests and 255 assertions** using the real `AdminServerClient(AgentProperties)` against a controlled JDK HttpServer. It is not a real Admin Server/Spring Security, retry-scheduler or Ubuntu test. Failed [attempt 05](qa/week4-evidence/2026-10-31/integrated-agent-packaging/05-http-transport.json) is preserved: its QA harness expected ISO-string Instants, while the JAR emits numeric seconds. Corrected assertions decode and compare exact `ack.appliedAt`/`event.occurredAt` values; attempt 06 passes on the unchanged JAR/source. This resolved harness assumption is not an application failure; the final evidence validator confirms the passing result and retains the failed capture.

Exact Git tree comparison and final validation confirm two unchanged scopes through S2: frontend and Admin Server. The M4 technical handoff/ACK and historical UI/backend evidence remain applicable with their original provenance. No new frontend/server/browser execution is claimed. Packaging now has two new scripts and Agent has production/build changes, so the 30/10 blanket packaging equivalence and validator's exact-two-file/+9 assumptions cannot validate S2 unchanged; the explicit current run profile handles those deltas. `.github` is absent from both compared Git trees and supplies no CI execution evidence.

M2 Part 2 and M3's [Week 4 closeout](client-agent/WEEK4_CLIENT_AGENT_CLOSEOUT.md), [manifest](client-agent/WEEK4_PACKAGING_MANIFEST.md), [installation guide](CLIENT_INSTALLATION.md) and [14-case Ubuntu checklist](client-agent/WEEK4_ACCEPTANCE_TEST_CHECKLIST.md) are received. M3 owner-report test-count/JAR-hash discrepancies remain provenance notes; retain the raw evidence and independently identify the current tested artifact. M1 final freeze/security/candidate/group decision and M2 Part 3 remain open. Actual `.deb` build and Ubuntu installation/permissions/USB/GUI/daemon/systemd/reboot remain deferred; new Windows staging does not close those runtime gates.

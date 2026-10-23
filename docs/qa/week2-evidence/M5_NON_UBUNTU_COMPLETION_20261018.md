# M5 Week 2 completion excluding Ubuntu

Date: 2026-10-18 (Asia/Bangkok). Branch: `huy`. Tested source: working tree based on `e7b05fe972f07aa2d304dcd8e6cf21c664f4d085`, including the shared browser harness fix. [Source hashes](2026-10-18/source-manifest.json), [environment](2026-10-18/07-environment.json), [QA summary](../WEEK2_QA_SUMMARY.md), [inventory](../WEEK2_TEST_INVENTORY.md).

**M5 implementation and QA within the requested non-Ubuntu scope: COMPLETE / PASS.** Full Week 2 physical/Ubuntu integration remains OPEN. Human M4 acknowledgment remains PENDING; this report records a technical review, not an owner's acceptance.

## Fresh verification

| Command from `frontend/` | Result | Raw evidence |
|---|---|---|
| `npm test` | PASS: 72 tests, no failures/skips/cancellations | [Tests](2026-10-18/01-frontend-tests.txt) |
| `npm run lint` | PASS, exit 0 | [Lint](2026-10-18/02-frontend-lint.txt) |
| `npm run build` | PASS, exit 0 | [TypeScript/Vite build](2026-10-18/03-frontend-build.txt) |
| `npm run test:browser` | PASS, exit 0 | [HTTP fixture/browser](2026-10-18/04-browser-fixture.txt) |
| `npm run test:live` | PASS, exit 0 | [Real management backend/browser](2026-10-18/05-browser-management.txt) |
| `npm run test:browser:live` | PASS, exit 0 | [Real Events backend/browser](2026-10-18/06-browser-events.txt) |

Each log records command, working directory, branch, base revision, timestamps with +07:00 offset, scope and exit code. The tested working tree is identified by file hashes, not described as an already committed revision. Browser suites ran sequentially. The existing backend JAR was executed and hashed; Maven was not rerun. The local collector initially could not print a Unicode build checkmark under Python's cp1252 console; the build itself exited 0 and its complete UTF-8 log was already saved. Collector output was corrected before the remaining browser checks.

## Restart regression resolved

Issue M5-W2-07, severity MEDIUM (test harness), is FIXED. With `JAVA_HOME` unset, this Windows host resolves `java` to Oracle's `javapath` launcher. That launcher starts a separate JDK JVM. The old harness killed only the launcher, leaving the JVM holding its isolated H2 file and serving authenticated requests. Restart readiness could accept the old server, so the browser still received 200 instead of the expected expired-session response. [Extracted diagnostic server output](2026-10-18/08-restart-diagnostic.txt) shows the replacement JVM failing because H2 was already in use.

`frontend/tests/liveServer.mjs` now terminates the Windows process tree for the specific test process and waits for termination before restarting. Unix retains the existing termination behavior. The regression verifies that the browser's old session gets HTTP 401 after restart, Refresh opens Login, protected event rows disappear, reauthentication works, and H2 retains the event/whitelist/version state. Application authentication code was not changed. Three JVMs left by the earlier failed audit runs were identified by their exact isolated database paths and stopped.

Fresh management assertions also verify invalid/valid login, CSRF rejection, cookie/session reload, A-only Allow and duplicate idempotency, B isolation, controlled HTTP ACK, Revoke, restart persistence and logout. A desired/applied moves 1/0 → 2/0 → 2/2 → 3/2; the actual IDs and before/after records are in the raw management log.

## M5 acceptance coverage

| Requirement from the supplied ownership plan, section 7 | Verified result |
|---|---|
| Persisted event mapping and six-column Event History | PASS: source `occurredAt`, endpoint, Linux attribution, USB identity, type/decision, UNKNOWN and endpoint detail links |
| Global filtering, server order/pagination, date/time semantics | PASS: 25 HTTP-created persisted events, 20+5 rows, exact endpoint/user/device filters, type/decision, whole local dates and fractional timestamp boundary |
| Live loading/empty/error and explicit demo | PASS: no mock fallback, no stale rows during injected 403/500/network errors, recovery, protected routes and readonly demo |
| M4 integration and regression | PASS: shared adapter/auth, management policy actions, A/B isolation, restart/session regression, desktop/mobile |
| Plan, inventory, truthful status and owner evidence | COMPLETE for this scope: current logs/source/environment and reviewed M3 61-test evidence are indexed; Ubuntu cases retain NOT EXECUTED |

## M3 evidence reviewed

The [M3 raw Maven log](../../client-agent/week2-evidence/01-agent-maven-test.txt) records 61 tests, zero failures/errors/skips, BUILD SUCCESS, completed at `2026-10-17T22:11:24+07:00`. The [M3 handoff](../../client-agent/WEEK2_AGENT_INTEGRATION.md) and test sources cover transport headers/contract, endpoint persistence, heartbeat, event sending/outbox and apply/ACK recovery. This is reviewed owner-run unit/contract evidence, not a fresh Maven run by M5 or proof of actual Ubuntu/USB/GUI behavior. The raw owner log does not identify its OS or Git revision; that missing metadata is not inferred. The older M2 40-test log and M3 2026-10-12 simulation remain historical records.

## Screenshots

| Capture | Meaning |
|---|---|
| [Expired session](2026-10-18/screenshots/m4-live-expired-session.png) | Real restart, HTTP 401, Login and no protected event rows |
| [Allow A](2026-10-18/screenshots/m4-live-allow-a.png), [Revoke A](2026-10-18/screenshots/m4-live-revoke-a.png) | Real backend policy changes with desired/applied distinction |
| [Events desktop](2026-10-18/screenshots/events-real-http-desktop.png) | Persisted HTTP inputs and server pagination |
| [Date filter](2026-10-18/screenshots/events-real-http-date-filter.png) | Inclusive local day, final stored microsecond and next midnight boundary |
| [Events mobile](2026-10-18/screenshots/events-real-http-mobile.png) | Live Events at 375px |

## Remaining scope and preservation

Actual Linux Agent runtime, physical USB/USBGuard enforcement, active Linux user/session attribution, actual apply/ACK/offline recovery, systemd/IPC smoke and native GUI notification execution remain NOT EXECUTED in this user-requested scope. Synthetic HTTP events and controlled ACK do not close these full integration cases. Week 1 historical acceptance is unchanged.

The [M4/M5 technical handoff](../M4_M5_WEEK2_HANDOFF.md) is complete; human M4 acknowledgment must be recorded by that owner and remains PENDING. No message was sent to another member. The user's application database was not used/reset by testing; its unchanged hash is recorded in the source manifest. Prior 2026-10-16 evidence remains intact as historical evidence.

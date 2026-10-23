# Week 3 integration test plan — M5

Prepared: 2026-10-21; updated: 2026-10-24 (Asia/Bangkok). Owner: M5. Scope: Event History hardening, Windows regression and preparation of deferred Ubuntu acceptance. [Inventory](WEEK3_TEST_INVENTORY.md), [QA summary](WEEK3_QA_SUMMARY.md), [evidence](week3-evidence/README.md), [demo draft](../DEMO_SCRIPT.md).

The supplied Week 3 M1–M5 ownership plan is the scope reference. Existing code that meets a requirement is verified rather than rewritten. This plan does not transfer ownership of backend, USBGuard core, Agent, packaging, shared auth or management UI to M5.

## Execution and evidence rules

- `WINDOWS_EXECUTABLE`: Node/npm unit/component/fixture, Spring Boot/H2 synthetic HTTP/browser, Maven mock/contract tests and static package/script checks.
- `DEFERRED_UBUNTU`: real Ubuntu daemon, USB devices, loginctl sessions, native GUI, Unix sockets, systemd, clean `.deb` install, upgrade/uninstall/reinstall and reboot. Execution is scheduled after Week 4; current result is `NOT EXECUTED`.
- Use `PASS`, `FAIL` or `NOT EXECUTED` for each actual case. An environment failure is recorded with its cause and exit code; do not infer a functional failure from a blocked runner.
- Record exact command/cwd, start/end time (+07:00), OS/toolchain, base commit, working-tree source hashes, result and evidence link. Test names map to the inventory; command success alone is not proof of unasserted features.
- Use isolated temporary H2, randomized QA credentials/enrollment tokens, test browser profiles and loopback ports. No real application database or production credentials are used. Browser suites run sequentially.
- Preserve Week 1/2 logs/reports. Place new evidence under `week3-evidence/`; fixture and HTTP-seeded source must be visible in reports and demo.

## Windows scenario matrix

| ID | Owner / level | Steps or implementation under test | Expected result |
|---|---|---|---|
| W3-F01 | M5 / component | Render `Events`/`EventTable` with source endpoint, user, device, event, decision and nanosecond timestamp; include escaped text and special characters in endpoint ID | Six columns; source instant retained in `time`; safe text and encoded detail link; UNKNOWN is not BLOCK |
| W3-F02 | M5 / mapper + real HTTP/browser | Map empty/null/missing serial fixtures; POST no-serial storage with hash; read persisted Events | No invented serial; device hash/ID and endpoint identity retained; no-serial event renders |
| W3-F03 | M5 with M1 / mapper + real HTTP/browser | Ingest A/UNKNOWN, A/new user and B/other user with the same no-serial device; filter endpoint/user and combined A/B user | Historical UNKNOWN/user/session snapshots remain independent; B cannot match A-only user history; source time preserved; no claim of an actual Linux user switch |
| W3-F04 | M5 with M1 / API/browser | Seed 25 baseline events, then three Week 3 attribution events; filter endpoint/user/device/type/decision and reset | Server-wide filters and totals; names/IDs and partial username use literal case-insensitive matching, while type/decision remain exact; no filtering only one loaded page; cross-endpoint shared device keeps all three events |
| W3-F05 | M5 / unit + browser | Use local date range including stored final microsecond, next midnight, one-sided ranges and DST unit cases | Selected local days included; next midnight excluded; invalid range rejected; source precision not rewritten |
| W3-F06 | M5 / mapper + component + browser | Request page 0/1, partial final page, empty/out-of-range page; inject invalid enums, malformed metadata and excess final-page rows | Server order/global totals retained; 20+5 baseline rows; correct final page controls; malformed responses reject the entire read |
| W3-F07 | M5 with M4 / unit + browser | Load real empty results; inject 403, 500 and network failure; recover; reject HTML/malformed response in unit tests | Loading/empty/error clear protected rows; no mock fallback; Refresh resumes API data |
| W3-F08 | M5 with M4/M1 / API/browser | Invalid/valid login, protected Events before login, cookie reload, CSRF checks, real backend restart and logout | Correct 401/403/auth UX; expired session shows Login without protected rows; no JS credential storage |
| W3-F09 | M5 with M4 / fixture + real browser | Open Events at desktop/375px; exercise explicit demo, readonly management and fixture filters | No page overflow; demo is visibly identified and does not mutate live API; real Events uses persisted HTTP input |
| W3-F10 | M5 with M4/M1 / real HTTP/browser | Allow/repeat Allow/Revoke on A; inspect A/B whitelist, desired/applied; controlled HTTP ACK and server restart | Idempotency and B isolation; desired is distinct from applied; persistence and session expiry; controlled ACK is not USBGuard apply |
| W3-F11 | M5/M1 / API + component + real browser | Enter visible endpoint/USB names (partial/mixed case) or IDs; combine with username/type/decision/date; use page 2; include legacy hostname fallback, missing device details and literal `%`/`_`/backslash | Filter labels match table; names and IDs search all persisted history before counting/paging; source hostname precedence and existing exact-ID API filters remain correct; Linux username is not interpreted as UID |
| W3-F12 | M5/M1 / API + browser | Enter part of a Linux username with mixed case, combine with endpoint/device/date, request a later page; test UNKNOWN and literal wildcard characters | Case-insensitive literal substring matching before count/paging; legacy exact `linuxUsername` remains available for API callers |
| W3-S01 | M1; rerun by M5 / Maven | Run existing server tests: enrollment, token hashing, missing/invalid/cross-endpoint credentials, Admin CSRF/session, events | Current server contract/security regression passes with isolated test data |
| W3-S02 | M1; rerun by M5 / Maven + build | Run existing status/UNKNOWN, per-endpoint version/ACK/concurrency/persistence tests and executable JAR packaging | Current assertions and production-default tests pass; -Pwith-frontend packages the built React bundle; verify same-origin JAR serving separately and retain owner review |
| W3-A01 | M2; rerun by M5 / Maven mocks | Run existing parser, presence/dedup, policy/base/foreign-rule and active-user/notification policy tests | Current fixtures/mocks pass; not a daemon, IPC or actual GUI test |
| W3-A02 | M3; rerun by M5 / Maven mocks + build | Run enrollment persistence, heartbeat, event/outbox, policy sync/apply-failure/ACK-recovery tests and JAR build | Existing transport/recovery assertions pass; packaged JAR produced; unrepresented Week 3 fault cases remain owner follow-up |
| W3-P01 | M3; review by M5 / static | Execute existing Windows package check with built JAR; inspect service/live config/helper loader and required class entries | Static source/JAR checks pass; installer/systemd runtime not inferred |
| W3-P02 | M3; review by M5 / shell syntax | `bash -n` on installer, maintainer scripts and desktop launcher using Git Bash | Shell parses on Windows; no `.deb` binary, install or runtime claim |
| W3-P03 | M3; QA by M5 / negative static-verifier check | Request -CheckStaging with absent staging, then missing required file and valid staging when available; do not run an installer | Requested absent/incomplete staging must fail or explicitly remain unverified; valid staging may PASS. Current absent-directory probe reproduces M5-W3-06. |
| W3-D01 | M5 / documentation | Review plan, inventory, QA summary, evidence/provenance and demo draft | Actual statuses/evidence/owner blockers recorded; historical results retained |

## Deferred Ubuntu acceptance matrix

Each case below is `DEFERRED_UBUNTU / NOT EXECUTED`, after Week 4. Capture Ubuntu version, exact integrated revision/JAR/package hashes, USB identity and correlated Agent/server/USBGuard/user traces when execution resumes.

| ID | Owner | Preconditions and steps | Expected result / required evidence |
|---|---|---|---|
| W3-U01 | M3/M1 | Clean Ubuntu 24.04; build/install `.deb`, configure URL/enrollment token, start live service | Correct dependencies/user/permissions; authenticated enrollment; ONLINE/lastSeen and durable credentials |
| W3-U02 | M2/M3/M1/M5 | Unknown class-08 USB inserted with active local GUI user; correlate event to Admin Web | Real BLOCK and correct endpoint/user/UID/session/device/source timestamp; database ID and Web row correlation |
| W3-U03 | M1/M2/M3/M4/M5 | Two real endpoints A/B; same device identity; Allow A, reconnect, Revoke A, reconnect | Actual A apply/ACK and ALLOW/BLOCK; B policy/USBGuard unchanged; desired/applied and versions correlated |
| W3-U04 | M2/M3/M5 | Switch/logout users; no GUI, remote-only and ambiguous sessions | Correct active local attribution; UNKNOWN when unresolved; old identity not reused |
| W3-U05 | M2/M3/M1/M5 | Stop server/network; insert unknown USB; recover network and restart Agent with pending event/ACK | Fail-closed enforcement, durable outbox/retry, no false success/ACK, eventual delivery without losing saved events |
| W3-U06 | M2/M3/M5 | Reboot client with whitelist/base rules, registration, pending spool/ACK | Credentials/policy/outbox survive; service starts live; no simulation; permissions and actual USB behavior remain correct |
| W3-U07 | M2/M3/M5 | GUI helper under correct UID; BLOCKED storage Insert; excluded events; missing/failing helper | One native popup per eligible Insert, correct GUI user/socket, dedup/exclusions, telemetry continues on notifier failure |
| W3-U08 | M3/M2/M5 | Install repeatedly, upgrade, uninstall/reinstall; inspect credentials, base rules/whitelist and directories | Idempotent lifecycle, documented retention/removal, no unintended loss of endpoint/policy data |
| W3-U09 | M2/M3/M1/M5 | Physical no-serial USB, stable USBGuard hash; reconnect and per-endpoint Allow/Revoke | Hash-based identity without weak serial inference; real policy/enforcement and history remain consistent |

## Reproduction on Windows

Run from repository root with a JDK 21+ in `JAVA_HOME`, Maven, compatible Node/npm and locally installed Chrome/Edge. Set `CHROME_PATH` if needed. Offline options below use the caches available on this QA machine; a cache miss is recorded rather than relabeled PASS.

```powershell
mvn -o -B -f client-agent/pom.xml clean package
powershell -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week3-windows.ps1 -CheckBuiltJar
bash -n packaging/scripts/build-deb.sh
bash -n packaging/agent-deb/DEBIAN/postinst
bash -n packaging/agent-deb/DEBIAN/prerm
bash -n packaging/desktop/usbshield-notifier-launcher.sh
Push-Location frontend
npm ci --offline --no-audit --no-fund
npm run lint
npm test
npm run build
Pop-Location
mvn -o -B -f admin-server/pom.xml -Pwith-frontend clean package
Push-Location frontend
npm run test:browser
npm run test:live
npm run test:browser:live
node --test tests/m4_policy_live.test.mjs
Pop-Location
```

`clean package` runs tests and creates fresh JARs. Build frontend/dist before invoking the with-frontend profile. The three checked-in browser suites use Vite preview/proxy and do not themselves verify JAR-served React. The [24/10 packaged-JAR variant](week3-evidence/2026-10-24/integrated-regression/packaged-browser-variant.json) separately retained all M5 assertions, set web = api and omitted Vite; it passed against the combined JAR on HTTP/default settings, not production HTTPS. Its temporary derived script is identified by hash, not required as a tracked test. [Execution logs](week3-evidence/2026-10-24/integrated-regression/README.md) and [completion/owner findings](WEEK3_M5_COMPLETION_20261024.md) record current results and remaining inputs. Collect raw logs even for failed attempts, hash tested source/builds and copy only screenshots produced by the run.

## Handoff and completion

M5 can complete F01–F09 and D01 against available contracts, and rerun existing S/A/P tests without modifying other owners' source. Shared code/API changes require owner review and regression on the integrated revision. M1 supplies Week 3 security/status/policy/profile and embedded-SPA evidence; M2 supplies additional core/session hardening; M3 supplies retry/packaging coverage and `.deb` build provenance; M4 supplies shared auth/management UI acceptance.

M5's independent completion is separate from group acceptance. M1 and the owners review the integrated revision, all remaining Windows P0 items and blockers before `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`. Full Ubuntu acceptance remains open until the U cases are actually executed.

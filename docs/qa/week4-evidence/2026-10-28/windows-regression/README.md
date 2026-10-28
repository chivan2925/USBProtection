# Week 4 — fresh Windows regression, 2026-10-28

Owner: M5. Timezone: Asia/Bangkok. Source baseline: `2013ce726ef8cc960b30720435be23e8ccef1903`. Source tree SHA-256: `40b49888fa950c01ecc1381702e6f3ca15d95370b55319b614be2a12dcb3c759`.

[Release validation](../../../WEEK4_RELEASE_VALIDATION.md), [inventory](../../../WEEK4_TEST_INVENTORY.md), [Event History review](../../../WEEK4_EVENT_HISTORY_REVIEW.md), [Demo Script](../../../../DEMO_SCRIPT.md), [Ubuntu sheets](../../../WEEK4_UBUNTU_ACCEPTANCE.md).

Every log below has adjacent same-stem JSON containing actual executable/argv/cwd, source hash/revision before and after, timestamps in UTC and +07:00, command exit and scope. The [capture runner](../../../scripts/capture-week4.mjs) uses the local JDK 22 for this Windows run; a source manifest covers tracked frontend/server/Agent/packaging/build inputs independently of concurrent documentation edits.

| Capture | Executed check | Actual result |
|---|---|---|
| [01-npm-ci](01-npm-ci.txt) | `npm ci --offline --no-audit --no-fund` | FAIL, exit 1: updated lockfile needs uncached Vite 8.3.5; resolved by 07 |
| [02-agent-package](02-agent-package.txt) | `mvn -o -B -f client-agent/pom.xml clean package` | PASS; 127 tests, no failures/errors/skips; executable Agent JAR |
| [03-shell-builder](03-shell-builder.txt) | `bash -n packaging/scripts/build-deb.sh` | PASS; syntax only |
| [04-shell-postinst](04-shell-postinst.txt) | `bash -n packaging/agent-deb/DEBIAN/postinst` | PASS; installer not executed |
| [05-shell-prerm](05-shell-prerm.txt) | `bash -n packaging/agent-deb/DEBIAN/prerm` | PASS; removal not executed |
| [06-shell-notifier](06-shell-notifier.txt) | `bash -n packaging/desktop/usbshield-notifier-launcher.sh` | PASS; native notification not executed |
| [07-npm-ci-registry](07-npm-ci-registry.txt) | `npm ci --no-audit --no-fund` | PASS; exact existing lockfile, no dependency updates |
| [08-packaging-static](08-packaging-static.txt) | `verify-week3-windows.ps1 -CheckBuiltJar` | PASS; static service/config/helper + current Agent JAR |
| [09-staging-probes](09-staging-probes.txt) | `test-staging-verifier.ps1` | PASS, 3/3; invalid missing/incomplete inputs correctly fail; positive staging uses dummy files |
| [10-frontend-tests](10-frontend-tests.txt) | `npm test` | PASS, 96/96, 0 failures/skips/cancellations |
| [11-frontend-lint](11-frontend-lint.txt) | `npm run lint` | PASS |
| [12-npm-audit](12-npm-audit.txt) | `npm audit --audit-level=high --json` | PASS; 0 vulnerabilities reported for this lockfile at execution time |
| [13-frontend-build](13-frontend-build.txt) | `npm run build` | PASS; TypeScript + React/Vite production build |
| [14-server-with-frontend](14-server-with-frontend.txt) | `mvn -o -B -f admin-server/pom.xml -Pwith-frontend clean package` | PASS, 26 tests, no failures/errors/skips; JAR embeds current React build |
| [15-browser-fixture](15-browser-fixture.txt) | `npm run test:browser` | PASS; labelled/readonly demo, auth, states, errors and desktop/mobile; 14 fresh screenshots |
| [16-browser-management](16-browser-management.txt) | `npm run test:live` | PASS; real isolated Spring Boot/H2, A/B Allow/Revoke, desired/applied/controlled ACK, actual JVM restart/session expiry; 9 screenshots |
| [17-policy-http](17-policy-http.txt) | `node --test tests/m4_policy_live.test.mjs` | PASS, 1/1; duplicate Revoke/future ACK/B isolation |
| [18-browser-events](18-browser-events.txt) | `npm run test:browser:live` | PASS; 28 synthetic HTTP events, filters/pages/date/UNKNOWN/no-serial/A/B, injected errors/recovery/mobile; 8 screenshots |
| [19-browser-packaged-rehearsal](19-browser-packaged-rehearsal.txt) | `node docs/qa/scripts/rehearse-week4.mjs` | PASS; JAR-origin UI/API, 31 original assertion lines retained, 8 screenshots; actual 207-frame CDP recording |

Only browser suites run sequentially. Agent/Windows syntax work and server build/fixture browser ran independently. The initial collector scanned screenshot mtimes for every command, so capture 14 also retained copies of the concurrently running fixture images. Those are **owned by capture 15**, not evidence produced by Maven. The final screenshot index uses only the owning browser capture; the collector now limits screenshot collection to browser commands.

Bootstrap note: the Windows sandbox initially rejected Node child-process creation (`spawnSync ... git.exe EPERM`) before any test suite launched. Captures above used authorized process execution. No failed bootstrap is presented as an application test result.

## Scope and reproducibility

Events, endpoint enrollment and ACKs are synthetic test inputs to real HTTP/H2 in the live suites. Browser-injected 403/500/network errors establish UI failure/recovery; the management suite separately performs an actual backend JVM stop/start. Neither proves physical USB enforcement, live Linux sessions, Agent fail-closed behavior, native GUI notification or Ubuntu service/reboot/package lifecycle. There is no production HTTPS browser acceptance in this run.

The packaged rehearsal uses an ignored, documented temporary copy of the original Events harness, serves the SPA and API from the same JAR, and preserves every original assertion. [Provenance and substitutions](recording/provenance.json), [cleanup confirmation](recording/cleanup.json), [frame timeline/hashes](recording/frames.json), [playable recording](recording/replay.html). It captured **207 actual CDP JPEG frames / 6,764,775 bytes**, excluding login/password and ending before logout. This is a timestamped screencast replay without encoded video/audio/narration; capture existence and test verdict are reported separately.

Final [validation PASS](validation.json): **18 positive checks**, 39 owned screenshots, 207 actual recording frames (~10.6 seconds), 5 frontend assets byte-matched inside the JAR, and unchanged application/historical evidence. [validate-week4.py](../../../scripts/validate-week4.py) generated [environment/tool versions](environment.json), [JUnit summary](junit-summary.json), [build manifest](build-manifest.json), [owned screenshot records](screenshots.json), [QA document/tool manifest](qa-documents.json) and [SHA-256 evidence inventory](checksums.json). Its initial self-output link-order failure and fix are retained in [validation attempts](validation-attempts.json); no application test was failed by that utility issue. Temporary H2 databases/browser profiles/QA tokens are excluded from evidence backup. Reviewed screenshot samples and recording endpoints contain fixture/QA identifiers; no deployment credentials are used.

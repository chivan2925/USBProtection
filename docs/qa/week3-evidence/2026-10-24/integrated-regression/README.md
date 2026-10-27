# M5 integrated Windows regression — 2026-10-24

Branch `huy`; tested revision `a0ad421901d5cb67fbe1b27ea9a74c05dfe2c3c1`. [Completion and remaining owners](../../../WEEK3_M5_COMPLETION_20261024.md), [current inventory](../../../WEEK3_TEST_INVENTORY.md).

[Environment](environment.json), [source manifest](sources/117de4465bd08ad8604fe940475e1699ba8cb539c060f6bfb8316f0c3b7b6fd6.json), [JUnit summary](junit-summary.json), [validation/artifacts](validation.json). Source tree SHA-256 `117de4465bd08ad8604fe940475e1699ba8cb539c060f6bfb8316f0c3b7b6fd6`. Each command has adjacent same-stem JSON with full argv/cwd/revision/source/start/end +07:00/scope/exit.

**16 successful positive checks, four resolved environment launch failures, one reproduced verifier failure.** Frontend96/server26/Agent117/standalone policy1 PASS. Group gate remains OPEN; negative staging exit 0 is not a PASS of its intended assertion.

| Log | Exact command | Result / interpretation |
|---|---|---|
| [01-npm-ci](01-npm-ci.txt) | `npm.cmd ci --offline --no-audit --no-fund` | PASS, exit 0 |
| [02-agent-tests-package](02-agent-tests-package.txt) | `mvn.cmd -o -B -f client-agent/pom.xml clean package` | PASS, exit 0 |
| [03-shell-builder](03-shell-builder.txt) | `bash -n packaging/scripts/build-deb.sh` | ENVIRONMENT LAUNCH FAILURE, exit 3221225794; resolved by 07–10, not a syntax failure |
| [04-shell-postinst](04-shell-postinst.txt) | `bash -n packaging/agent-deb/DEBIAN/postinst` | ENVIRONMENT LAUNCH FAILURE, exit 3221225794; resolved by 07–10, not a syntax failure |
| [05-shell-prerm](05-shell-prerm.txt) | `bash -n packaging/agent-deb/DEBIAN/prerm` | ENVIRONMENT LAUNCH FAILURE, exit 3221225794; resolved by 07–10, not a syntax failure |
| [06-shell-launcher](06-shell-launcher.txt) | `bash -n packaging/desktop/usbshield-notifier-launcher.sh` | ENVIRONMENT LAUNCH FAILURE, exit 3221225794; resolved by 07–10, not a syntax failure |
| [07-shell-builder-retry](07-shell-builder-retry.txt) | `bash -n packaging/scripts/build-deb.sh` | PASS, exit 0 |
| [08-shell-postinst-retry](08-shell-postinst-retry.txt) | `bash -n packaging/agent-deb/DEBIAN/postinst` | PASS, exit 0 |
| [09-shell-prerm-retry](09-shell-prerm-retry.txt) | `bash -n packaging/agent-deb/DEBIAN/prerm` | PASS, exit 0 |
| [10-shell-launcher-retry](10-shell-launcher-retry.txt) | `bash -n packaging/desktop/usbshield-notifier-launcher.sh` | PASS, exit 0 |
| [11-frontend-tests](11-frontend-tests.txt) | `npm.cmd test` | PASS, exit 0 |
| [12-frontend-lint](12-frontend-lint.txt) | `npm.cmd run lint` | PASS, exit 0 |
| [13-frontend-build](13-frontend-build.txt) | `npm.cmd run build` | PASS, exit 0 |
| [14-server-with-frontend](14-server-with-frontend.txt) | `mvn.cmd -o -B -f admin-server/pom.xml -Pwith-frontend clean package` | PASS, exit 0 |
| [15-packaging-static](15-packaging-static.txt) | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week3-windows.ps1 -CheckBuiltJar` | PASS, exit 0 |
| [16-browser-fixture](16-browser-fixture.txt) | `npm.cmd run test:browser` | PASS, exit 0 |
| [17-staging-negative-probe](17-staging-negative-probe.txt) | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week3-windows.ps1 -CheckStaging` | ASSERTION FAIL / M5-W3-06; exit 0 incorrectly prints PASS for absent staging |
| [18-browser-management](18-browser-management.txt) | `npm.cmd run test:live` | PASS, exit 0 |
| [19-policy-http-contract](19-policy-http-contract.txt) | `node --test tests/m4_policy_live.test.mjs` | PASS, exit 0 |
| [20-browser-events](20-browser-events.txt) | `npm.cmd run test:browser:live` | PASS, exit 0 |
| [21-browser-packaged-jar](21-browser-packaged-jar.txt) | `node .artifacts/audit-packaged-events-20261024.mjs` | PASS, exit 0 |

The four browser suites ran sequentially; standalone policy HTTP used a separate random-port/H2 runtime. Maven and browser databases are QA-only, not the user's application DB. Events and controlled ACKs are synthetic HTTP inputs; no physical USB/Ubuntu/session/GUI/systemd/install/reboot execution occurred.

[Negative staging probe](staging-probe.json) records the absent-directory precondition and false-positive outcome. The source also merely warns for missing files. M3 owns correction/triage; no packaging source was changed by M5. No staging or .deb artifact was found, so package status is NOT BUILT / NOT VERIFIED.

[Packaged-browser provenance](packaged-browser-variant.json) records exact two substitutions in an ignored temporary copy of the unchanged M5 browser script: web=api and remove Vite startup. All original assertions remain. HTTP/default profile only; production HTTPS is not verified. [Screenshot provenance](screenshots.json) records hashes and mtimes within each owning command's execution interval. [Desktop](screenshots/events-real-http-desktop.png), [mobile](screenshots/events-real-http-mobile.png), [partial username](screenshots/events-username-substring.png), [UNKNOWN/no-serial](screenshots/events-week3-unknown-no-serial.png), [endpoint A](screenshots/events-week3-endpoint-a.png), [endpoint B](screenshots/events-week3-endpoint-b.png), [date](screenshots/events-real-http-date-filter.png), [session expiry](screenshots/m4-live-expired-session.png).

Earlier dated evidence/reports are preserved. No application/test/packaging source edits or group/owner acceptance is implied by this QA capture.

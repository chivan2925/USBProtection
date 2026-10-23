# M5 post-pull audit evidence — 2026-10-23

Branch `huy`; tested commit `8c222a47142d7a80f75e56b8d77e5bf732797e16`. Timezone: Asia/Bangkok (+07:00). [Task status report](../../../WEEK3_M5_POST_PULL_REVIEW_20261023.md).

[Environment](environment.json), [source manifest](sources/e16a567bf1cd754d48d43c9e69c7c58a793695c91273e751d585fd30f631de4d.json) and [final validation/artifacts](validation.json) identify this run. Source tree SHA-256: `e16a567bf1cd754d48d43c9e69c7c58a793695c91273e751d585fd30f631de4d`. Each log has same-stem JSON recording command/cwd, exact revision, source manifest, start/end timestamps, scope and exit code. The product working tree was clean at start; new files are audit documentation/evidence.

| Log | Command / scope | Result |
|---|---|---|
| [01](01-npm-ci.txt) | `npm ci --offline --no-audit --no-fund` | Exit 0; exact lockfile dependencies |
| [02](02-frontend-tests.txt) | `npm test` | Exit 0; **96/96** |
| [03](03-frontend-lint.txt) | `npm run lint` | Exit 0 |
| [04](04-frontend-build.txt) | `npm run build` | Exit 0; TypeScript/Vite |
| [05](05-agent-tests-package.txt) | `mvn -o -B -f client-agent/pom.xml clean package` | Exit 0; **61 tests**, executable JAR |
| [06](06-server-with-frontend.txt) | `mvn -o -B -f admin-server/pom.xml -Pwith-frontend clean package` | Exit 0; **26 tests**, combined server/React JAR |
| [07](07-browser-fixture.txt) | `npm run test:browser` | Exit 0; fixture/browser |
| [08](08-browser-management.txt) | `npm run test:live` | Exit 0; real Spring Boot/H2 management/auth/session/policy/browser |
| [09](09-browser-events.txt) | `npm run test:browser:live` | Exit 0; unchanged M5 Events suite with 28 HTTP-seeded events |
| [10](10-policy-http-contract.txt) | `node --test tests/m4_policy_live.test.mjs` | Exit 0; **1/1**, separate from npm unit suite |
| [11](11-packaging-static.txt) | Existing Windows package verifier with `-CheckBuiltJar` | Exit 0; static source/archive checks |
| [12](12-browser-packaged-jar.txt) | `node .artifacts/audit-packaged-events-20261023.mjs` | Exit 0; all M5 Events assertions with browser UI/API at one JAR origin, no Vite |
| [13](13-shell-builder.txt) | `bash -n packaging/scripts/build-deb.sh` | Exit 0; parsing only |
| [14](14-shell-postinst.txt) | `bash -n packaging/agent-deb/DEBIAN/postinst` | Exit 0; parsing only |
| [15](15-shell-prerm.txt) | `bash -n packaging/agent-deb/DEBIAN/prerm` | Exit 0; parsing only |
| [16](16-shell-launcher.txt) | `bash -n packaging/desktop/usbshield-notifier-launcher.sh` | Exit 0; parsing only |

The three existing browser suites and additional packaged-JAR variant ran sequentially. Maven/browser data was isolated in in-memory/temporary H2; credentials and enrollment tokens were generated for QA. The user's application runtime/database was not used or reset. No actual Ubuntu/USBGuard/physical USB/session switching/native GUI/systemd/IPC/install/reboot execution occurred.

For run 12, a temporary copy of the M5 browser script retained all assertions, used `web = api` and omitted Vite startup. [Variant provenance](packaged-browser-variant.json) records the exact original hash, replacements and derived hash. The original tracked script is unchanged. Run 12 uses HTTP/default runtime configuration; it verifies the packaged bundle and API share an origin, not production HTTPS behavior. Fresh packaged screenshots: [desktop](screenshots/events-real-http-desktop.png), [partial Linux username](screenshots/events-username-substring.png), [mobile](screenshots/events-real-http-mobile.png). [Management restart/session](screenshots/m4-live-expired-session.png) is from run 08.

No `.deb` binary/build provenance or dedicated M2/M3 Week 3 handoff was found in this review. Existing 61-test Agent and static checks retain unit/mock/static scope. The status report records documentation alignment and owner/group decisions still outstanding; old evidence was preserved.

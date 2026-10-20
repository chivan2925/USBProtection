# M5 — Week 2 Windows-scope closeout and deferred Ubuntu execution

**Date:** 2026-10-20. **Reviewed base:** `chivan2925/USBProtection` `main` at `e75213b437d01b3335b952618b02705874d257dc`. This is an addendum to `../WEEK2_QA_SUMMARY.md` and `../WEEK2_TEST_INVENTORY.md`, not a replacement for historical evidence.

**Schedule decision:** All tests requiring the Ubuntu 24.04 VM, USBGuard daemon, physical USB, systemd/XDG, real Unix-domain notification IPC, or a genuine GNOME/Wayland popup are **DEFERRED UNTIL AFTER WEEK 4**. Their status is `NOT EXECUTED`, not `PASS`, and they do **not** block the Windows-scope Week 2 decision. Full physical E2E remains OPEN.

## Historical PASS evidence (not a fresh test of this patch)

| Owner | Windows-compatible evidence already in `main` | Classification |
| --- | --- | --- |
| M1 | `docs/member1/week2-evidence/01-maven-test.txt`, HTTP scripts / 13 Maven tests PASS | Historical PASS, tested on Windows |
| M2 | `docs/usbguard/week2-evidence/11-mvn-test.txt`, 40 JUnit tests PASS, 2026-10-14 | Historical Windows PASS, raw log present; M2 document corrected |
| M3 | `docs/client-agent/week2-evidence/01-agent-maven-test.txt`, 61 tests PASS, 2026-10-17 | Historical PASS; original raw log does **not** record OS/commit, so do not infer Windows from the file itself |
| M4/M5 | `docs/qa/week2-evidence/2026-10-18/` — frontend build, lint, 72/72 tests and three browser suites PASS | Historical Windows PASS; browser HTTP events/ACK are synthetic inputs to a real M1 server |

M3's purported Linux/physical/GUI evidence `02`–`08` is **not accepted as executed evidence** without source/runtime corroboration. See `../../client-agent/week2-evidence/STATUS_AND_PROVENANCE_20261020.md` for per-file classification. The original files are preserved unchanged to avoid rewriting provenance.

## Changes for Windows regression

- M3 systemd unit explicitly selects live Spring profile and root-owned external YAML.
- M3 .deb builder now stages the correct installation YAML with absolute data path `/var/lib/usbshield-agent` and no public default enrollment secret.
- M3 desktop launcher uses Spring Boot 3.3 `PropertiesLauncher` to call M2's helper main instead of trying to class-load `BOOT-INF/classes` directly via `-cp`.
- M2 report's stale `11-mvn-test.txt` missing-log statements corrected; no M2 Java changes.
- A Windows-runnable packaging static check script lives in `packaging/tests/verify-week2-windows.ps1`.

## Regression commands (PowerShell, repository root)

```powershell
mvn -B -f admin-server/pom.xml clean test
mvn -B -f client-agent/pom.xml clean test
mvn -B -f admin-server/pom.xml -DskipTests package
mvn -B -f client-agent/pom.xml -DskipTests package
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\verify-week2-windows.ps1 -CheckBuiltJar

Push-Location frontend
npm ci
npm run lint
npm run build
npm test
npm run test:browser
npm run test:live
npm run test:browser:live
Pop-Location
```

Prerequisites: JDK 21+ (recommend 21) and Maven 3.9+, Node.js compatible with frontend Vite 8 (prior evidence uses Node 24.21), Chrome/Edge for browser suites. The browser suites use dedicated ports and should be run **sequentially**. `test:live` and `test:browser:live` expect the built M1 JAR; use source-relative PowerShell paths from root. Windows runs never touch the actual Ubuntu device or establish its enforcement/GUI behavior.

**Closeout rule:** Mark Windows-scope Week 2 `PASS` only after collecting fresh execution logs for these commands. Preserve historical files and clearly label source revision, date and environment of each new run. M4's human acknowledgment of the technical M4/M5 handoff remains a separate administrative follow-up.

# USBShield Week 3 — final Windows-scope owner closeout candidate

> **SUPERSEDED — 2026-10-28:** This was the pre-approval checklist. The owner subsequently approved H05, so the current, definitive Week 3 decision is [WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md](WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md): `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`. Keep the original PENDING fields below as historical evidence only; they are not the current gate status.


**Date:** 2026-10-28 (Asia/Ho_Chi_Minh)  
**Baseline merged main:** `5f32d415fc5a5ef6fb8e7b4d1867041eb4d05487`  
**Decision state:** `PENDING_OWNER_VERIFICATION_AND_H05_SIGNOFF`  
**Scope:** `WINDOWS_EXECUTABLE`; Ubuntu E2E remains `DEFERRED UNTIL AFTER WEEK 4`.

This patch includes all QA/handoff changes as final files; no patch/apply script is required. This is a **candidate status and execution checklist**. It does not invent a test run on the patched working tree or pre-authorize H05. Any earlier contradictory text in historic review snapshots is superseded by the cited current code and M5 QA closure. Preserve the original historical review text as evidence.

## Member-by-member handoff status

| Owner | Week 3 Windows technical status | Supporting record | Remaining action |
|---|---|---|---|
| M1 — Server | Implemented, 26/26 server tests PASS, H01 acknowledged | `docs/member1/WEEK3_SERVER_HARDENING.md`; 24/10 integrated regression | Review final integrated SHA and lead H05 |
| M2 — USBGuard/session/notification | Implemented, H03 DONE, mock/unit cases included in Agent suite | `docs/usbguard/WEEK3_CORE_HARDENING.md`; M5 Week 3 inventory | Ubuntu physical/daemon tests after Week 4 |
| M3 — Client/packaging | Windows code/static checks PASS; M5-W3-06 and M5-W3-07 CLOSED, 127/127 Agent tests | `docs/qa/WEEK3_M5_M3_PERSISTENCE_CLOSURE_20261028.md`; evidence 28/10 | Provide genuine `.deb` build/staging provenance when possible; do not claim it exists now |
| M4 — Admin frontend | Implemented, 96/96 frontend tests PASS and browser PASS; H02 acknowledged on 28/10 | `docs/admin-web/WEEK3_UI_UX_HARDENING.md` (H02 section) | H02 reflected as DONE in current M5 QA summary and inventory by this direct-file patch |
| M5 — Event History/QA | Implemented, Week 3 QA matrix and demo draft done; M5-W3-07 independently closed | `docs/qa/WEEK3_QA_SUMMARY.md`; M3 persistence closure | Capture final owner verification results; record H05 only after decision |

Previous gate blockers `M5-W3-06` (staging false-PASS) and `M5-W3-07` (false ACK on policy persist failure) are **resolved** in current merged source. Current QA tests are historical executions at documented source revisions; avoid relabeling those logs as newly run at this commit.

## Windows command gate — run from repository root

Prerequisites: Windows 11, Java (project targets 21; existing QA ran JDK 22), Maven, Node/npm, Chrome for browser checks, PowerShell. For native JDK `jar tf`, ensure `JAVA_HOME\bin` is on `PATH`.

```powershell
 git branch --show-current
 git rev-parse HEAD
 git status --short
 java -version
 mvn -version
 node --version
 npm --version

 mvn -B -f client-agent/pom.xml clean package
 mvn -B -f admin-server/pom.xml clean test
 mvn -B -f admin-server/pom.xml -DskipTests package

 powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging/tests/test-staging-verifier.ps1
 powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week3-windows.ps1 -CheckBuiltJar
 bash -n packaging/agent-deb/DEBIAN/postinst

 Push-Location frontend
 npm ci
 npm run lint
 npm run build
 npm test
 npm run test:browser
 npm run test:live
 npm run test:browser:live
 Pop-Location

 git diff --check
 git status --short
```

**Important:** `npm ci` can mutate local `frontend/node_modules` and may hit Windows `EPERM` if an existing Vite/Chrome process holds files; close active processes or use a clean working tree. Browser commands should be run **sequentially**, not in parallel. `mvn` test will require dependency access unless cached. Build the bundled React server JAR using the repository's documented `admin-server/build-release.ps1` workflow if the simple `mvn package` does not include frontend static assets; do not claim a bundled build from a backend-only Maven run.

For each command, report its exit code and last summary lines. Do not paste real passwords, tokens, `endpoint.json`, DB contents, or personal USB event data. Evidence captured during this run must be assigned a **new** run date, SHA, OS, toolchain and source hash, separate from historic logs.

### Optional Linux/CI `.deb` artifact build provenance — NOT required as Ubuntu E2E PASS

If Linux or WSL2 with working `dpkg-deb` is available, stage/build the package **without installing it**:

```bash
bash packaging/scripts/build-deb.sh
sha256sum packaging/dist/usbshield-agent_1.0.0_amd64.deb
dpkg-deb --info packaging/dist/usbshield-agent_1.0.0_amd64.deb
dpkg-deb --contents packaging/dist/usbshield-agent_1.0.0_amd64.deb
```

Check that the actual output filename and script invocation match `packaging/scripts/build-deb.sh`; if different, report the filename and build log rather than inventing a result. Do not execute `postinst`, `prerm`, `systemctl` or USBGuard daemon outside an intentionally prepared Ubuntu test environment.

## Installer update in this overlay

The updated `postinst` checks the *effective* active `ImplicitPolicyTarget`: comments and whitespace are ignored, exactly one `block` is acceptable, no active directive results in an explicit `block` being appended, and conflicting/unsafe directives cause the install to fail with a diagnostic rather than silently overriding another administrator's configuration. If it adds `block`, USBGuard is restarted so the setting can take effect; failure to start/restart USBGuard is an error, not a PASS. Existing foreign USBGuard rules are retained. **No Ubuntu runtime result is claimed.** M3 and M2 should independently review this installer change before installing on a disposable Ubuntu client after Week 4.

## Owner verification follow-up received — 2026-10-28

The owner supplied full frontend and packaging-verifier output and confirmed `BUILD SUCCESS` for the three Maven commands. Direct output: staging verifier **3/3 PASS**, static/JAR PASS, frontend lint/build PASS, **96/96 frontend tests PASS**, and all three browser suites PASS. `npm ci` warned of **2 high-severity npm vulnerabilities**; one Vite cache `EPERM` appeared while the unit suite still passed. The owner-local `git rev-parse HEAD` and `git status --short` were not supplied. See [owner evidence/limitations](WEEK3_OWNER_WINDOWS_VALIDATION_20261028.md).

The implementation gates have substantial passing Windows evidence, but source provenance, npm security finding disposition, and **H05 owner/group sign-off remain pending**. Do not automatically set H05 to DONE or claim a `.deb`/Ubuntu runtime PASS.

## H05 group decision — needs human approval

- [ ] All requested final Windows test commands have recorded exit 0 or an accepted, documented alternative.
- [x] M1 H01 owner acknowledgment exists.
- [x] M2 H03 technical handoff exists.
- [x] M3 M5-W3-06/M5-W3-07 closure evidence exists.
- [x] M4 H02 acknowledgment exists.
- [x] M5 QA review of the previous integrated revision exists.
- [ ] M5 reviews **new** final owner output and marks new evidence revision.
- [ ] M1 and the group explicitly accept H05; record author, date, commit SHA and outcome.
- [ ] Deployment-secret rotation/re-enrollment status documented for any real deployment (security follow-up, not a synthetic Windows test).

**Until those unchecked items are done:** `WEEK 3 WINDOWS SCOPE — TECHNICALLY READY / H05 PENDING`.

**Only after accepted H05 and no P0 issues:** `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`.

Never label the entire product or original Ubuntu Gate `FULL PASS` before actual Ubuntu acceptance.

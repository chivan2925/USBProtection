# USBShield Week 2 — Windows browser-test follow-up (2026-10-20)

**Baseline:** GitHub `main` at `e75213b437d01b3335b952618b02705874d257dc`, with `frontend/`, `docs/` provided in `USBProtection(7).zip`. The previous readiness migration is already present in the uploaded snapshot. **This document records remediation and pending verification, not a claim that all browser tests have passed.**

## Actual results supplied by the owner

| Command | Result | Notes |
| --- | --- | --- |
| `npm run lint` | PASS | Zero warnings/errors |
| `npm run build` | PASS | Vite 8.3.1 |
| `npm test` | PASS 76/76 | Includes prior HTTP-readiness regression tests |
| `npm run test:browser` | FAIL | Timed out navigating `/dashboard` after clearing fixture sessions |
| `npm run test:live` | PASS | Spring Boot/H2 through HTTP/browser, synthetic input, not USB hardware |
| `npm run test:browser:live` | FAIL | `Timed out: real backend readiness`; backend output absent from submitted log |

## Fix A — fixture session expiry

After mobile-route verification, the fixture browser harness was on a protected page. It called `fixture.state.sessions.clear()`, then `navigate('/dashboard', 'Tổng quan bảo vệ')`. That helper waits for the Dashboard heading, which is incompatible with an invalidated session once the application checks authentication. **Expected behavior is redirection to Login, not rendering Dashboard.** The harness now uses Chrome DevTools `Page.reload` to force session revalidation, then waits for Login, logs in again, and continues the original assertions. It does not weaken authentication checks.

## Fix B — actual backend bootstrap diagnostics

The M5 `browser-live.mjs` launcher assumed an absolute **JDK 22 installation** whenever `JAVA_HOME` was not set. This is unsuitable for the owner's Windows **JDK 21** setup. It now uses `$env:JAVA_HOME\bin\java.exe` when configured or `java` from `PATH`, including a `java -version` preflight. The previous readiness loop swallowed process-exit exceptions, reporting only a generic timeout. The new loop immediately surfaces the JVM exit code, signal and recent backend output. The underlying reason for the owner's original timeout cannot be proven from that timeout alone; JVM selection and occupied port are plausible contributors. A dynamically selected backend TCP port avoids accidental connection to an old service on `18083`.

For cleanup, the harness stops its own spawned Windows process trees (`taskkill /PID <own PID> /T /F`) rather than a launcher alone, to avoid orphan child processes and H2 file locks. It does **not** kill arbitrary Java processes.

## Verification and constraints

- Source checks: `node --check frontend/tests/browser.mjs` and `node --check frontend/tests/browser-live.mjs` — PASS in the patch environment.
- Diagnostic smoke: with a deliberately invalid temporary JAR, `browser-live.mjs` aborted promptly with `Admin Server exited before readiness: exit=1` and `Invalid or corrupt jarfile`, and wrote `frontend/.artifacts/live-events-server-last.log` — PASS for **error reporting only**.
- Full `npm run test:browser` and `npm run test:browser:live` on Windows: **PENDING owner rerun**. Do not report PASS until actual execution.
- No production React, M1 Admin Server, M2 core, M3 Agent or packaging files changed in this patch. Ubuntu real USB/GUI/runtime tests remain deferred after Week 4.

## How to verify on Windows 11

Open PowerShell from the repository root; make sure the Admin Server JAR is built with the intended JDK 21:

```powershell
java -version
mvn -B -f admin-server/pom.xml -DskipTests package
cd frontend
npm run lint
npm run build
npm test
npm run test:browser
npm run test:live
npm run test:browser:live
```

Run browser suites **one at a time**, not in parallel. When using `JAVA_HOME`, set it to the JDK **home** directory, not the `bin` directory. If the backend test still fails, inspect `frontend/.artifacts/live-events-server-last.log` and the new startup diagnostic. Browser tests use isolated temporary H2; they should not delete or reset the real Admin Server database.

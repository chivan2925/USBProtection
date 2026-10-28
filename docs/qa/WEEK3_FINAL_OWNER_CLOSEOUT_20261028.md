# USBShield — Week 3 integrated owner acceptance

**Decision date:** 2026-10-28 (Asia/Ho_Chi_Minh)  
**Decision:** **`WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`**  
**H05 integration gate:** **CLOSED — owner explicitly authorized closeout in conversation on 2026-10-28.**  
**Canonical merged application baseline:** GitHub `main` at `5f32d415fc5a5ef6fb8e7b4d1867041eb4d05487`.  
**Verification provenance:** repository QA evidence + owner-submitted Windows command outputs and explicit owner confirmations. Owner-local `git rev-parse HEAD` was not supplied, so the exact SHA of the *tested working tree* is not independently established. No separate unanimous signature or approval from all five members is asserted.

## Member acceptance — Windows scope only

| Owner | Final Week 3 assessment | Evidence / ownership |
|---|---|---|
| M1 — Admin Server | **PASS / H01 DONE** | Backend tests reported `BUILD SUCCESS`; earlier integrated QA 26/26; production profile, API/session/ACK, React same-origin JAR recorded in prior QA. |
| M2 — USBGuard, sessions, notifications | **PASS / H03 DONE** | Agent regression coverage includes no-serial, policy reconciliation, reconnect, active-session and notifications. Actual Ubuntu device behavior deferred. |
| M3 — Agent sync/outbox/packaging | **PASS (WINDOWS SCOPE)** | Owner reports `mvn ... client-agent ... clean package` BUILD SUCCESS; earlier M5 QA 127/127; M5-W3-06 (staging false-pass) and M5-W3-07 (false ACK after persistence failure) CLOSED; 3/3 staging probes and JAR static verifier PASS. Real `.deb` staging/build and installation remain **NOT BUILT/NOT VERIFIED** per available evidence. |
| M4 — Admin Web | **PASS / H02 DONE** | 96/96 frontend unit tests; lint/build and fixture/live/Event browser suites PASS; integrated shared-UI acknowledgment in `docs/admin-web/WEEK3_UI_UX_HARDENING.md`. |
| M5 — Events and QA | **PASS (WINDOWS SCOPE)** | Event History, regression inventory, real HTTP/H2 synthetic-event integration, independent M3 closure evidence and demo draft present. |

## Accepted Windows verification

- **Maven:** Owner explicitly confirmed `BUILD SUCCESS` for all three requested commands (Client Agent clean package; Admin Server clean test; Admin Server package). Fresh Maven logs were not supplied; do not relabel prior independent counts as fresh counts.
- **Frontend:** `npm ci` succeeded, lint reported 0 warnings/errors, Vite/TypeScript production build succeeded, `npm test` ended **96/96 PASS, 0 failures**, and `npm run test:browser`, `npm run test:live`, `npm run test:browser:live` all reported PASS in the submitted text logs.
- **Packaging:** `test-staging-verifier.ps1` ended **3/3 PASS** (both invalid inputs *correctly failed*); `verify-week3-windows.ps1 -CheckBuiltJar` ended PASS and verified executable-JAR entries.
- **npm security follow-up:** The owner stated both `npm audit --omit=dev --audit-level=high` and `npm audit --audit-level=high` returned **`found 0 vulnerabilities`**. The raw output of those follow-up audits was not attached, so this is **owner-attested**, not independently reproduced or a claim that the earlier `npm ci` warning never appeared. No `npm audit fix` or lockfile modifications are implied.
- **Nonblocking test warning:** A Vite `EPERM` cache rename message appeared during `npm test`, but the same run finished 96/96 PASS. Re-investigate if reproducible; it is not a failed Week 3 Windows gate in the supplied run.
- **Code provenance limit:** The `main` commit above is the independently inspected canonical application source. The user's test checkout HEAD/clean-status output was not captured. This qualification is retained rather than inventing a test-to-commit attestation.

## Gate closure

| Gate | Decision |
|---|---|
| H01 M1 | **DONE** |
| H02 M4 | **DONE** |
| H03 M2 | **DONE** |
| H04 M3 — Windows technical scope | **DONE**; actual `.deb` build/staging/install provenance carried forward as deferred work |
| M5-W3-06, M5-W3-07 | **RESOLVED** and regression evidence reviewed |
| H05 integrated Windows owner acceptance | **CLOSED / PASS**, on owner's explicit instruction |
| Ubuntu E2E U01–U09 | **NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4** |

**Excluded from the Windows PASS claim:** physical USB insertion, actual USBGuard daemon enforcement/IPC, `loginctl` switching, native user notification, systemd lifecycle, clean `.deb` build/install/upgrade/uninstall, reboot, production HTTPS, and actual endpoint behavior under Ubuntu. None was newly executed or accepted here.

**Post-Week-3 follow-ups (not retroactive Windows P0 failures):** record real `.deb` artifact build/checksum and staging provenance when run in Linux/CI; perform full Ubuntu acceptance after Week 4; assess and revoke/rotate any still-valid credentials previously exposed in public Git history and re-enroll impacted agents; confirm new deployment safeguards. Refer to `docs/qa/WEEK3_DEPLOYMENT_SECURITY_FOLLOWUP.md`. Do not treat Git index cleanup as proof of token revocation.

This is an **owner-requested closeout record**, not a fabricated independent human signature from M1/M2/M3/M4/M5 or a claim of `WEEK 3 — FULL PASS`.

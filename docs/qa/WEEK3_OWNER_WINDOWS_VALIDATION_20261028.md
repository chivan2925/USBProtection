# Week 3 — Owner Windows verification follow-up (28/10/2026)

> **Final decision addendum (28/10):** The owner explicitly approved **H05 CLOSED / `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`**. Both `npm audit --omit=dev --audit-level=high` and `npm audit --audit-level=high` were subsequently reported as **`found 0 vulnerabilities`** by the owner; raw logs were not supplied. The earlier `npm ci` high-severity warning remains preserved below as a historical observation, not an open confirmed current advisory. The local checkout HEAD was not captured. See [final decision](WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md). The prior `H05_PENDING_GROUP_SIGNOFF` status below reflects the earlier snapshot.


**Status:** `WINDOWS_TECHNICAL_TESTS_REPORTED_PASS / H05_PENDING_GROUP_SIGNOFF`.  
**Canonical application source:** `origin/main` at `5f32d415fc5a5ef6fb8e7b4d1867041eb4d05487` checked on GitHub for `admin-server/`, `client-agent/`, and `frontend/`.  
**Owner-local HEAD:** `NOT CAPTURED` in the supplied logs; confirm before attribution of these runs to an exact commit.  
**Inputs:** Owner-submitted PowerShell output (staging/static and frontend tests) and owner statement that all three Maven commands returned `BUILD SUCCESS`; `USBProtection(9).zip` provides documentation and packaging, **not** the three application source directories.

## Actual owner-supplied Windows results

| Command or evidence | Result | Provenance and limitation |
|---|---|---|
| `mvn -B -f client-agent/pom.xml clean package` | `BUILD SUCCESS` (owner statement) | Full Maven output not supplied; do not infer the fresh test count from the user statement. Prior independent M5 evidence records 127/127. |
| `mvn -B -f admin-server/pom.xml clean test` | `BUILD SUCCESS` (owner statement) | Full Maven output not supplied; prior independent M5 evidence records 26/26. |
| `mvn -B -f admin-server/pom.xml -DskipTests package` | `BUILD SUCCESS` (owner statement) | Package success reported; this alone does not prove the React production bundle was included in a combined JAR. Historical combined-JAR browser evidence remains separate. |
| `packaging/tests/test-staging-verifier.ps1` | `PASS` — 3/3 probes | Both invalid staging cases intentionally fail with nonzero exit; synthetic valid staging passes. This is **not** a real `.deb` build. |
| `packaging/tests/verify-week3-windows.ps1 -CheckBuiltJar` | `PASS` | Static service/control/config/notifier/build checks and Spring Boot JAR contents passed. |
| `npm ci` | Exit appears successful; 70 packages added | Reports **2 high-severity vulnerabilities**. No advisory/package breakdown was included; investigation remains open. |
| `npm run lint` | `PASS` — 0 warnings/errors | oxlint on 57 files. |
| `npm run build` | `PASS` | TypeScript and Vite 8.3.1 production bundle produced. |
| `npm test` | `PASS` — 96/96 | One Vite cache `EPERM` rename message appeared during execution but all tests passed; investigate if recurrent, do not mislabel as failed suite. |
| `npm run test:browser` | `PASS` | HTTP fixture; not hardware. |
| `npm run test:live` | `PASS` | Real Spring Security/H2 HTTP browser; Agent policy ACK input controlled/synthetic. |
| `npm run test:browser:live` | `PASS` | Real Spring Boot/H2 with 28 synthetic HTTP events; not real USB events. |
| `bash -n packaging/agent-deb/DEBIAN/postinst` | Owner says all requested checks were run; output not attached | Syntax-only check; no Ubuntu installer execution claimed. |
| `git diff --check` and `git status --short` | Output not supplied | Do not infer an exact clean final working tree. |

## Remaining verification/decision (not necessarily technical defects)

1. Capture exact owner checkout identity and working-tree status: `git branch --show-current`, `git rev-parse HEAD`, `git status --short`, `git diff --check`. The attached ZIP and remote HEAD alone do not establish what was tested in the local checkout.
2. Classify the two high-severity npm advisories. From `frontend/` run `npm audit --omit=dev --audit-level=high`, followed by `npm audit --audit-level=high` for full details. Record dependency names, production/dev exposure, fixability, and disposition; do **not** run `npm audit fix` blindly because it may change the lockfile or version contracts.
3. M5 can add this owner run to the current QA register with **source provenance qualified as above**; independent historical test counts must not be relabeled as fresh owner output.
4. M1 and the group explicitly decide H05, recording date, approvers, final Git commit and caveats. Until then: `WEEK 3 WINDOWS SCOPE — TECHNICALLY READY / H05 PENDING`.
5. `.deb` real build/staging provenance remains `NOT BUILT / NOT VERIFIED`; USBGuard daemon, native GUI notification, physical USB, systemd, actual installation and reboot stay `NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4`.
6. Historical token exposure requires deployment-owner rotation/revocation/re-enrollment assessment. Repository cleanup does not prove live credentials were revoked.

No Java/React implementation changes are made in this follow-up package.

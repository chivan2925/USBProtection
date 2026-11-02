# USBProtection — Ubuntu E2E regression fix (candidate, NOT YET ACCEPTED)

**Baseline:** `chivan2925/USBProtection`, `main` SHA `8dfd5893dab9248ae578d749f104875225d12fc5` (verified 2026-11-02).

**Contents:**
- `USBProtection_Ubuntu_E2E_Regression_Fix.patch`: complete source/test diff for 20 changed/new paths.
- `modified-files/`: only modified or added source files; identical changes to patch (alternative manual copy route).
- `TEST_MATRIX.md`: test cases, known limitations and remaining Ubuntu native checks.

## Apply on Windows to an unmodified checkout at the exact baseline

1. Back up your repository and lab H2 database; stop Admin Server and do not overwrite any running `.deb` yet.
2. In PowerShell at repository root:

```powershell
 git rev-parse HEAD
 git status --short
 git apply --check 'C:\PATH\TO\USBProtection_Ubuntu_E2E_Regression_Fix.patch'
 git apply 'C:\PATH\TO\USBProtection_Ubuntu_E2E_Regression_Fix.patch'
 git diff --check
 git status --short
```

Do not continue if HEAD differs, source has uncommitted changes, `git apply --check` fails, or the patch overlaps work from other team members. Do not use `git apply --reject`, `git reset --hard`, or patch with `--3way` to bypass conflicts; request a rebase/review first.

Alternative: copy only paths from `modified-files/` to the same paths in the repository, after verifying the baseline; avoid using both methods.

## Run tests on Windows

```powershell
mvn -B -f client-agent/pom.xml clean test
mvn -B -f admin-server/pom.xml clean test
Push-Location frontend
npm ci
npm test
npm run lint
npm run build
Pop-Location
```

**Verification status from patch author:**
- `git apply --check` on reconstructed baseline files: PASS.
- Baseline blobs for modified files were checked against `main` Git blob hashes: PASS.
- Java 21 compilation of modified `DesktopNotificationService.java` and `UsbGuardEventListener.java` against previous Agent runtime dependency JARs: PASS.
- Java 21 standalone notifier smoke: transient BLOCK→ALLOW produced no popup request; effective BLOCK produced one request — PASS. This uses a fake USBGuardGateway, not actual Ubuntu hardware.
- Syntax check of new `.mjs` via Node 22 `node --check`: PASS.
- `package.json` JSON parse: PASS.
- **Full Maven/JUnit, frontend npm, and real Ubuntu tests: NOT EXECUTED** in this environment (Maven/frontend dependencies unavailable). Your earlier green runs predate these changes.

If Windows tests pass, keep the result as a **candidate** and rebuild both Admin Server + Agent package from the same commit. Use exactly the same Agent `.deb` hash for Ubuntu A and B. Verify the mounted Kingston policy and real desktop popup; the patch does not itself prove a full Ubuntu E2E PASS.

## Intended behavior / review caveats

- Initial `PresenceChanged(block)` remains an audit event. Upon a subsequent `PolicyApplied(allow)` for the same device runtime ID and hash, Agent emits a second `CONNECTED/ALLOWED` event to reflect the effective decision. This is a contract choice and should be reviewed by M1/M2/M3/M5 (one physical insert can yield two decision records).
- Popup BLOCK is deferred 450ms, canceled on final allow and checked against fresh USBGuard `list-devices` before delivery. If live inspection fails, no unverified popup is shown; enforcement remains unaffected. Timing is an **assumption** requiring Ubuntu measurement.
- GUI metadata comes from Agent enrollment/heartbeat: `operatingSystem`, `currentUser` and a non-loopback IPv4. Multihomed hosts may still need route-aware IP selection; this patch picks a non-loopback private IPv4, which is sufficient for the single-NIC VMnet8 lab.
- Existing H2 admin schema uses Hibernate `ddl-auto: update` and new nullable endpoint columns; back up existing lab DB before switching runtime. Do not migrate production this way.
- Whitelist metadata is derived from saved `usbDeviceId` and UsbDevice details, not used as an authorization key (HASH-only enforcement remains unchanged).
- No changes to USBGuard baseline block policy, ACLs, entitlement, token contents, or persistence layout.

**Suggested commit message:** `fix(ubuntu): reconcile transient USBGuard allow events and restore endpoint metadata`

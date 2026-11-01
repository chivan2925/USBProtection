# USBShield Week 4 — Apply and verification commands

**Exact baseline:** `63e4dc61b15b86859dd4ca2ca4be60021a293091` (`main`, PR #49).  
**Package role:** proposed M2 notifier/M3 packaging IPC correction, tests, documentation and group sign-off templates; not a signed release.  
**Ubuntu 24.04 E2E:** intentionally deferred per team Week 4 plan; no native PASS is claimed.

## A. Apply safely (Windows PowerShell at repository root)

1. Confirm **clean Git worktree**, remote and exact source. Don't apply to a different HEAD; don't overwrite uncommitted changes.

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
git rev-parse HEAD
git status --short
```

`git rev-parse HEAD` must equal the baseline above. Create a separate branch before the ZIP is extracted:

```powershell
git switch -c fix/week4-ipc-group-finalization
```

2. Extract the ZIP **into the repository root** with overwrite (ZIP paths begin with `client-agent/`, `packaging/`, `docs/`, and `week4-apply-doc-sync.py`). The ZIP does not contain a full repository or historical evidence. Use PowerShell `Expand-Archive` or a ZIP manager.

3. From the repo root, after extraction:

```powershell
python .\week4-apply-doc-sync.py --check
python .\week4-apply-doc-sync.py --apply
Remove-Item .\week4-apply-doc-sync.py

git diff --check
git status --short
```

The script validates all 11 old doc/static-verifier anchors **before** writing and refuses to run against a different HEAD. It never overwrites historical `docs/**/week4-evidence` logs. If `--check` fails, **stop** and send the output: do not force replacements. The one-time script should be removed from the worktree before staging. The tracked new docs stay in `docs/rc/`.

## B. Windows owner test gate — run in order and capture exit codes

```powershell
# New M2 IPC code + existing Agent regressions
mvn -B -f client-agent/pom.xml clean test
mvn -B -f client-agent/pom.xml -DskipTests package

# M3 JAR, installer, scripts and staging; use full verifier, not Skip
powershell -NoProfile -ExecutionPolicy Bypass -File packaging/scripts/prepare-staging.ps1 -Clean
powershell -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week3-windows.ps1 -CheckBuiltJar -CheckStaging
powershell -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week4-windows.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File packaging/tests/test-staging-verifier.ps1

# Source hygiene
git diff --check
git status --short
```

For shell syntax on Git Bash/Linux:

```bash
bash -n packaging/agent-deb/DEBIAN/postinst
bash -n packaging/agent-deb/DEBIAN/prerm
bash -n packaging/desktop/usbshield-notifier-launcher.sh
bash -n packaging/tests/test-notifier-ipc-ubuntu.sh
```

Optional independent backend/browser checks after M1 verifies the exact candidate (M1 production code is not changed by this package):

```powershell
mvn -B -f admin-server/pom.xml clean test
Push-Location frontend
npm ci
npm run lint
npm test
npm run build
Pop-Location
mvn -B -f admin-server/pom.xml -Pwith-frontend clean package
```

**Do not paste credentials or full endpoint.json** into logs sent for review. A successful Maven test is **not** an Ubuntu IPC/GUI test.

## C. Ubuntu only after Windows review, not during Week 4 gate

The code path uses `acl` (`/usr/bin/setfacl`), user-owned
`/run/user/<UID>/usbshield-notifier/notice.sock`, Linux `SO_PEERCRED`, and XDG
login autostart. M2/M3 must first approve this design. Build a **real** `.deb`
on Ubuntu/CI with `dpkg-deb`, record `sha256sum`, install on an isolated test
host and run the *read-only*:

```bash
bash packaging/tests/test-notifier-ipc-ubuntu.sh <GUI_UID>
```

Then test real class-08 USB BLOCK, user routing, popup count, logout/login,
removal, foreign peer denial, USBGuard recovery and service restart while
observing that event/enforcement are unaffected by notifier failures.
The checker only proves ACL visibility at the point of inspection; it does not
prove popup rendering. Do not adjust effective USBGuard security policy to
force a notification PASS.

## D. Cross-owner review, candidate, and artifact handoff

- M2 reviews new notifier code and supplies PR #40 finding dispositions and no-breaking-wire-contract confirmation.
- M3 reviews ACL/setup/dependency and packaging/scripts. A P1 security issue may need changes beyond the candidate. Capture fresh Agent JAR checksum, keep earlier source SHA evidence separate and explain historical M3 raw log mismatches.
- M4 reviews whether any frontend/API contract changed (this package doesn't modify frontend), referencing S4 browser checks. M5 needs exact final commit/SHA and post-patch scope/verification, rather than backdating S4.
- M1 chooses the group's Windows release candidate, supplies the **actual server JAR binary or verified artifact URL**, accepts signoffs, and records the deferred Ubuntu scope. M1's old manifest JAR hash is NOT a replacement for an artifact download.
- Environment administrator checks historically exposed credentials, revokes/rotates any live secrets and re-enrolls affected Agents. Keep only **redacted attestation** in Git; no external success can be inferred from a script.

See [`WEEK4_OWNER_SIGNOFF_TEMPLATE.md`](WEEK4_OWNER_SIGNOFF_TEMPLATE.md) and [`WEEK4_IPC_ACL_DESIGN.md`](../usbguard/WEEK4_IPC_ACL_DESIGN.md).

### Suggested commit message

```text
fix(week4): secure notifier IPC and prepare cross-owner release closeout
```

**Do not publish an RC/final tag or claim group Windows Week 4 PASS while sign-offs or required tests remain pending.**

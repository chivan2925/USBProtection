# USBShield — Pre-Ubuntu Code Review (2026-11-02)

**Source baseline:** `main` at `0fb91dfac488c709784f9bb13b1b7c84db4c5b79`.
**Scope:** Source-level readiness review before **Ubuntu 24.04 LTS amd64 native testing**; no previous Week 4 Windows acceptance is reopened.
**Decision:** **CHANGES REQUIRED BEFORE UBUNTU INSTALL SMOKE**. This overlay addresses the issues below. The overlay itself has **not** received a new owner Maven/Windows run or real Ubuntu sign-off.

## Patches and evidence

1. **P0 integration risk — USBGuard daemon IPC rights for `usbshield`.** Previous `postinst` only attempted group membership; USBGuard authorizes commands using `IPCAllowedUsers`/`IPCAllowedGroups` and `IPCAccessControlFiles`, with distinct Devices, Policy and Parameters privileges. The installer now provisions root-owned `0600` user ACL under the canonical directory with `Devices=list,listen`, `Policy=list,modify`, `Parameters=list`, `Exceptions=listen`; rejects conflicting preexisting ACL/config/symlinks rather than overwriting; and restarts daemon after updating ACL. It tests list/parameter access as the **actual `usbshield` user** before returning success. No privileges are granted to normal desktop users. **Linux runtime remains UNVERIFIED.** See [USBGuard documentation](https://usbguard.github.io/documentation/configuration).
2. **P0 install safety — base rule verification.** Previously, a nonempty but incompatible existing `/etc/usbguard/rules.conf` was left untouched and postinst could succeed even when the required non-storage allow was missing. The updated installer rejects missing canonical base rules without rewriting arbitrary administrator rules. Existence is not proof of safe ordering or absence of foreign conflicting Allows; verify with native tests.
3. **P1 packaging interoperability — CRLF on Windows.** Linux `.service`, `.desktop`, YAML and USBGuard base-rule file can be CRLF after ZIP export. Builder explicitly normalizes line endings in its **staging copies** and `.gitattributes` declares LF for these artifacts.

## Checks executed during this review (not Ubuntu E2E)

- `bash -n` passed for patched `postinst`, `prerm` and `build-deb.sh`.
- A sandboxed copy of the ACL provisioning block passed **three isolated checks**: new user ACL creation (`0600`), repeat execution without rewriting, and rejection of customized ACL content. This did **not** contact USBGuard daemon.
- `dpkg-deb --root-owner-group --build` assembled a structural Debian candidate using an **already-built JAR supplied in an earlier conversation ZIP**; the Maven build step was intentionally stubbed **inside the sandbox**. The result is **packaging-structure evidence only**, not a current-main JAR or Ubuntu runtime assertion.
- Extracted Debian control and data files passed inspection; seven Linux-sensitive files (maintainer scripts, launcher, service, desktop entry, installed YAML, base rules) contained **zero CR bytes**.
- `systemd-analyze verify` could not validate the dependent `usbguard.service` in this sandbox because that service unit is unavailable here; no PASS is claimed.
- No Java unit tests, frontend build, server/browser suites or physical Ubuntu tests were rerun in this review. The final `.deb` for native testing must be rebuilt from the **merged GitHub main**; do not deploy the structural candidate based on an unverified ZIP snapshot.

## Review scope and limitations

- The known Java Maven 179/179 and M1/M4/M5 browser/server verification belong to **prior candidate evidence** and have not been independently rerun for this overlay.
- The source path and checked-in artifact were inspected, not a running Ubuntu daemon; no real `.deb` install, USB event, IPC authorization or desktop popup has been observed in this review.
- **Existing USBGuard policy is not automatically replaced.** Users must inspect foreign allow rules, rule ordering, usbguard daemon settings, and effective security. A previously customized `IPCAccessControlFiles` path causes an installer error requiring administrator decision.
- **Caution:** `IPCAccessControlFiles` is not the same mechanism as socket permissions; errors can still arise from distro daemon defaults. The installer refuses an unverified success rather than falling back to unsafe world/group-wide permissions.
- Prior history identified a deployment credential in Git history; rotate/re-enroll before real network exposure. An isolated disposable Ubuntu test host with synthetic credentials is the supported first step.
- `admin-server/` and `frontend/` are not modified; M1/M4/M5 Windows S4 evidence covers their unchanged source tree, not Ubuntu native behavior.

## Before native acceptance

1. Apply only the modified files, review the diff and rerun the two normal Client Agent Maven commands if required by branch policy; packaging-only changes do not alter Java code.
2. Build `.deb` **on Linux** from the candidate source/JAR, confirm `dpkg-deb -I` and `dpkg-deb -c`; inspect `DEBIAN/postinst`, `DEBIAN/prerm`, `usbshield-agent.service`, launcher and default config.
3. In an isolated Ubuntu VM with snapshot, perform clean install; verify `usbshield` can run `usbguard get-parameter ImplicitPolicyTarget`, `usbguard list-rules`, `usbguard list-devices`; verify ACL root ownership/`0600` and daemon restart.
4. Run physical USB Mass Storage BLOCK, HID unaffected, watch event attribution, A/B Allow/Revoke, server-down fail-closed, native GUI notification, reboot and installer lifecycle as the blueprint requires. Record pass/fail by test ID; do not infer these results from mocks.

**Verdict:** **Patch prepared / Windows technical evidence inherited / Ubuntu E2E NOT EXECUTED**. Complete native tests before full release sign-off.

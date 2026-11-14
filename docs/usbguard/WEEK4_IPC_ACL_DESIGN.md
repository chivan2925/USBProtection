# USBShield — Week 4 Notifier IPC Design (M2 + M3 review candidate)

**Merged implementation baseline:** `main` `d5e156afdad382dc19d3b0a7198bf681fd36763e` (2026-11-01; PR #50).  
**Owners:** M2 (`notification/**`), M3 (`packaging/**`), review M5, final integration decision M1.  
**Status:** Source merged / **two Agent Maven commands `BUILD SUCCESS` owner-reported** after cross-platform Windows fix; raw test totals, packaging/static verification, cross-owner sign-off and native Ubuntu tests **not supplied/verified on final SHA**. This is **not** owner sign-off or notification runtime acceptance.

## 1. Problem resolved in the candidate code

The previous `/run/usbshield-notifier` directory was root-owned `1777` (sticky) and the GUI user bound `<UID>.sock` mode `0660` in that directory. The system Agent is user `usbshield`, normally not in each GUI user's group, so socket reachability was unproven; another unprivileged user could reserve another UID's socket name (denial of service). The fix does **not** make the socket mode `0666` or grant USBGuard policy privileges to desktop users.

## 2. Candidate contract and lifecycle

1. On a local graphical login, **XDG autostart** invokes the helper as the real user `UID >= 1000`. `XDG_RUNTIME_DIR` must be **exactly** `/run/user/<UID>` and owned by that UID, with no symlink.
2. The helper creates `/run/user/<UID>/usbshield-notifier/` as the GUI user, initial mode `0700` and verifies ownership. Other users cannot pre-create the pathname inside a logind-owned private runtime tree.
3. The helper calls `/usr/bin/setfacl` (Debian dependency `acl`) to grant named user `usbshield` **traversal (`--x`) only** on the user's runtime directory and the private subdirectory. This intentionally changes effective ACL/mask on `/run/user/<UID>`; compatibility must be tested with the target desktop/logind stack.
4. It binds `notice.sock` under the private subdirectory and sets file mode `0600` before granting `u:usbshield:rw-`. Read the **effective ACL**, not just `stat` mode bits. The GUI user's process never receives USBGuard modification privileges.
5. The Agent checks all directory/socket owners are the target UID, rejects symlinks and non-socket inodes, then checks **Linux `SO_PEERCRED` helper user** matches the owner's user principal before transmitting UID/title/body. The helper independently checks `SO_PEERCRED` sender is `usbshield`, the target UID and the exact constant notification strings.
6. If the helper, ACL, peer verification or session is missing, the publisher returns `SKIPPED_NO_GUI` or `FAILED`. The event pipeline and local USBGuard enforcement remain independent. `DELIVERED` means handed to a helper, **not confirmed on-screen popup**.
7. Runtime directory and socket live only within the GUI user's session. Existing socket pathname is never blindly unlinked at startup; a leftover stale socket causes explicit failure and requires operator inspection. The former global sticky directory is retired by `postinst` without deleting nonempty content or replacing a customized tmpfiles rule. After upgrading while already logged in, the GUI helper might need a **fresh logout/login** to start with the new JAR.

### Security and operational limits

- The GUI user can tamper with their **own** socket/ACL, suppress their own popup or terminate their helper; notifications are informational, not an enforcement/security authorization boundary. A root administrator remains powerful. Effective USBGuard policy must remain fail-closed independently.
- Named ACL behavior, `SO_PEERCRED`, loginctl session identity, notify-send/DBus and packaging/systemd startup must be tested **on Ubuntu**; Java unit tests on Windows do not validate them.
- Adding traverse access to `/run/user/<UID>` may conflict with applications that require strict `0700` mode. Test GNOME/Wayland and desired desktop variants. If incompatible, return to design review rather than broadening the shared directory or guessing at a permission workaround.
- This code does **not** guarantee that `notify-send` painted a popup, merely that the helper ran a command. A real blocked USB plus GUI observation remains required.
- The initial M2 rule parser intentionally validates a bounded rule subset; unknown foreign `if`/attributes fail closed. Existing foreign device-specific Allows can still affect effective policy. These are separate from IPC.

## 3. Windows verification evidence (partial)

```powershell
mvn -B -f client-agent/pom.xml clean test
mvn -B -f client-agent/pom.xml -DskipTests package
powershell -NoProfile -ExecutionPolicy Bypass -File packaging/scripts/prepare-staging.ps1 -Clean
powershell -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week3-windows.ps1 -CheckBuiltJar -CheckStaging
powershell -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week4-windows.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File packaging/tests/test-staging-verifier.ps1
```

Owner reported the first two Maven commands `BUILD SUCCESS` after patching, but did not attach raw output/exit-code evidence to this document. Additional static/staging commands above are **not reported as executed for PR #50**, and native permission behavior remains unverified. Preserve historical S3 174 Agent tests as S3-only evidence. Record final Agent JAR checksum and any targeted test totals before signing.

## 4. Native Ubuntu — owner/M5 acceptance (DEFERRED)

After building and installing an owner-reviewed `.deb` on an isolated Ubuntu 24.04 test host:

```bash
# Normal graphical user, not sudo:
id -u
printf '%s\n' "$XDG_RUNTIME_DIR"
pgrep -af DesktopNotificationClientMain
getfacl -p "$XDG_RUNTIME_DIR" "$XDG_RUNTIME_DIR/usbshield-notifier" "$XDG_RUNTIME_DIR/usbshield-notifier/notice.sock"

# Administrator: evaluate effective service-user access without broad permissions:
sudo bash packaging/tests/test-notifier-ipc-ubuntu.sh <GUI_UID>
sudo systemctl status usbshield-agent usbguard --no-pager
sudo journalctl -u usbshield-agent -n 80 --no-pager
```

Capture: (1) UID/owner/mode/ACL for each path, (2) helper/Agent identity, (3) no path collision from a distinct user, (4) actual class-08 BLOCK & one popup to correct active GUI UID, (5) no popup ALLOWED/HID/Remove/UNKNOWN, (6) helper absent/peer invalid does not stop event/enforcement, (7) logout/login, switch user and reboot. Test `SO_PEERCRED` with another nonprivileged local user and confirm rejection. **No result is claimed here.**

## 5. Owner decisions required

- **M2:** review both Java sides (peer identity, UID routing, failure isolation), PR #40 findings dispositions, contract with M3; confirm no M1 API change.
- **M3:** approve `acl` dependency, session runtime path, XDG startup, former tmpfiles retirement, package script/syntax and actual permissions; own `.deb` build when Linux/CI available.
- **M4:** confirm no frontend source/API change requiring rework; M5 has S4 browser/Agent–Server evidence before this patch.
- **M5:** rerun affected tests after owner commit, record exact SHA, distinct mock/native boundaries and candidate status; do not sign for M2/M3/M1.
- **M1:** make final go/no-go only after dispositions and administrator credential attestation; a pre-release is distinct from full Ubuntu release.

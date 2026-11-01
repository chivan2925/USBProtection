# USBShield — M2 Week 4: M3/M5 Handoff & Ubuntu Verification Checklist

**Owner:** M2 · **Work IDs:** W4-M2-06 and W4-M2-07 (P1)  
**Current source review baseline:** `main` @ `d5e156afdad382dc19d3b0a7198bf681fd36763e` (PR #50); earlier M2 evidence remains at its original SHA.  
**Verdict:** HANDOFF READY; M3 IPC review and native Ubuntu E2E **PENDING / NOT EXECUTED**.

## Ownership boundaries

- **M2:** `usbguard/`, `session/`, `notification/`, M2 event enrichment and tests; USBGuard core documentation.
- **M3:** `acl` Debian dependency and `postinst` legacy-retirement, XDG autostart, `systemd`, Agent sync/ACK, outbox and `.deb` installer; the GUI helper (M2-owned Java) provisions `/run/user/<UID>/usbshield-notifier/notice.sock` and named-user ACLs. Coordinate modifications to either owner's files.
- **M5:** record actual end-to-end results, capture evidence, publish release gate with separate Windows/Ubuntu verdicts; no synthetic-to-native promotion.

## M3 IPC/permission review — action required

**Historical risk (pre PR #50):** the global root-owned `1777` directory and GUI-owned `0660` `<UID>.sock` could prevent the `usbshield` service user connecting and allow a second local user to occupy another UID's pathname. This is no longer the intended source design.

**Merged candidate (`d5e156a`, PR #50):** Java now routes to `/run/user/<UID>/usbshield-notifier/notice.sock` with named-user POSIX ACLs (`u:usbshield:--x` on runtime/private directories; `u:usbshield:rw-` on the socket), refuses symlinks/wrong owners/stale socket, and validates Linux peer credentials. Packaging declares `acl` and retires known legacy tmpfiles; desktop autostart requires exact `XDG_RUNTIME_DIR`. **Two Maven commands BUILD SUCCESS are owner-reported; not native Ubuntu evidence.**

**Review needed from M2 + M3:** actual effective ACL mask and uid/session ownership; whether logind runtime ACL change affects the desktop; installed `setfacl` executable; old helper during package upgrade and first fresh login; static Debian staging/source check; malicious peer, name collision and stale socket handling. Do not use `chmod 666`, share USBGuard modify privileges with GUI users or silently accept `DELIVERED` as a real popup. [Design and constraints](WEEK4_IPC_ACL_DESIGN.md). Record signed dispositions in [group template](../rc/WEEK4_OWNER_SIGNOFF_TEMPLATE.md).

## Post-Week-4 Ubuntu acceptance cases

| Case | Scenario | Expected result | Evidence | Status |
|---|---|---|---|---|
| M2-U01 | `list-rules` includes base `allow with-interface none-of { 08:*:* }`, default target `block` | Canonical base and implicit block verified; no foreign rules silently treated as safe | `usbguard list-rules`, parameter output | DEFERRED |
| M2-U02 | Unknown Mass Storage (class 08), keyboard/mouse HID | Unknown storage BLOCK, HID operational, correct original event | USBGuard journal, `lsblk`, hardware log | DEFERRED |
| M2-U03 | A has USB X grant; B has no grant | A ALLOW, B BLOCK, after revoke A BLOCK | Two endpoints' policies and Admin history | DEFERRED |
| M2-U04 | Remove two managed rules where runtime IDs are reassigned | No stale grant; no unrelated foreign/base rule removed | Before/after rule list, policy ACK status | DEFERRED |
| M2-U05 | USBGuard watch restarts and daemon unavailable then resumes | No hang, bounded retry, correct event stream; local enforcement independent | Agent/USBGuard logs and timeline | DEFERRED |
| M2-U06 | Switch/logout, remote-only, ambiguous sessions and tty-only | Exact local user when unique; `UNKNOWN` otherwise; tty no GUI popup | `loginctl`, event row, timing | DEFERRED |
| M2-U07 | Native popup for blocked class-08 insertion | Exactly one popup in correct active GUI user's session; no popup for HID/ALLOWED/Remove | Real desktop screenshot + helper/DBus logs | DEFERRED |
| M2-U08 | Kill/helper unavailable, socket permission denied or peer invalid | No false popup; clear failure status; event still reaches Server; protection still blocks | Agent logs, socket owner/mode, server event | DEFERRED |
| M2-U09 | Offline/reboot and policy persistence | No fail-open; Agent + USBGuard come back with correct rules | systemd journal, effective rule list | DEFERRED |

## Evidence discipline

Record `commit SHA`, `Agent JAR/.deb checksum`, Ubuntu/kernel/USBGuard version, times, device classification, expected and observed states, commands and exit codes. Redact token, serial, hash where unnecessary and individual users' personal details. For physical-USB tests, retain screenshots/logs from *actual* Ubuntu execution; Windows Maven result is separate.

## Gate and dependencies

- **M2 Part 3 source:** review + Windows Maven/JUnit required before owner commit; `SO_PEERCRED` and notifier routing need native Ubuntu check.
- **M3 P1 dependency:** a signed-off socket permission / peer trust plan plus packaging updates, not silently included in an M2-only ZIP.
- **M5:** keeps release verdict `UBUNTU E2E DEFERRED` until scenarios are performed and independently accepted.

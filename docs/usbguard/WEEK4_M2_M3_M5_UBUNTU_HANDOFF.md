# USBShield — M2 Week 4: M3/M5 Handoff & Ubuntu Verification Checklist

**Owner:** M2 · **Work IDs:** W4-M2-06 and W4-M2-07 (P1)  
**Baseline:** `main` @ `176e547f5bf093ef8c9aebdfabd29cab7177d73e`  
**Verdict:** HANDOFF READY; M3 IPC review and native Ubuntu E2E **PENDING / NOT EXECUTED**.

## Ownership boundaries

- **M2:** `usbguard/`, `session/`, `notification/`, M2 event enrichment and tests; USBGuard core documentation.
- **M3:** `/run/usbshield-notifier` provisioning, POSIX ACL and socket startup, XDG autostart, `systemd`, Agent sync/ACK, outbox and `.deb` installer. M2's patch does not modify M3 files.
- **M5:** record actual end-to-end results, capture evidence, publish release gate with separate Windows/Ubuntu verdicts; no synthetic-to-native promotion.

## M3 IPC/permission review — action required

Current deployed-source inspection (2026-11-01): `packaging/agent-deb/DEBIAN/postinst` creates shared `/run/usbshield-notifier` as root-owned `1777`; M2 user helper binds `/run/usbshield-notifier/<UID>.sock` and chmods it `0660` (GUI user's default group); Agent process runs under system user `usbshield` according to `packaging/systemd/usbshield-agent.service`.

**Risk 1 — connectivity:** `usbshield` may have no group write permission to the user's socket. **Risk 2 — availability:** in a shared sticky directory another local user can pre-create the socket pathname and prevent the legitimate helper from binding. **Risk 3 — authenticated sender:** M2 now checks peer UID-derived username with Linux `SO_PEERCRED`, requires service user `usbshield`, and checks target UID. These are necessary but do not themselves resolve socket ACL or name-squatting.

M3 must choose and document a secure, reproducible provisioning/ACL design. Avoid `chmod 666` as an unreviewed workaround, do not give normal users USBGuard policy privileges, and preserve the per-user D-Bus isolation. Confirm with M2 before changing M2-owned Java, and with M5 before marking the test PASS.

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

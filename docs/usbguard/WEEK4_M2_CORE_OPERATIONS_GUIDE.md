# USBShield — M2 Week 4: USBGuard, Session & Notification Core Guide

**Owner:** M2 · **Scope:** W4-M2-05 · **Base source:** `main` @ `176e547f5bf093ef8c9aebdfabd29cab7177d73e` (2026-11-01)  
**Scope of evidence:** Java 21 isolated checks and source review. Native Ubuntu/USBGuard/service/GUI checks remain **DEFERRED AFTER WEEK 4**.

## 1. Control plane versus enforcement plane

- **USBGuard daemon and its local rules are the enforcement plane.** The daemon decides which devices are authorized at the Ubuntu host. Unknown Mass Storage devices must not become allowed merely because the network or Admin Server is down.
- **Agent is the integration plane:** watches USBGuard events, resolves active local session, sends event/heartbeat, pulls desired endpoint policy, reconciles local USBGuard rules and publishes a best-effort desktop notification.
- **Admin Server/Web are the management plane:** store desired per-endpoint whitelist, event history and applied-version ACK. Desired policy is **not** proof that the local daemon applied it; M3 must not ACK if M2 reconcile throws.
- Normal Linux users without administrative rights must not modify USBGuard policy or stop protection. Root/sudo can stop or alter the system; USBShield makes **no root-proof guarantee**.

## 2. Interface class 08, non-storage base and ordering

The canonical base non-storage rule is:

```usbguard
allow with-interface none-of { 08:*:* }
```

Together with daemon parameter `ImplicitPolicyTarget=block`, this permits devices **without** a class-08 Mass Storage interface and blocks an unmatched Mass Storage device. A composite device with any `08:*:*` interface does **not** match the base rule. This is an intended conservative policy; keyboard/mouse real-world safety must still be checked with native devices.

USBGuard evaluates rules **in sequence (first matching rule wins)**. A preceding `block`/`reject` may override a later device-specific managed Allow, and a preceding broad Deny may also block a HID before the base rule. Reconciliation rejects these known/possible order conflicts; it does not silently reorder administrator-owned foreign rules. Foreign device-specific Allows may still authorize devices outside the desired USBShield whitelist: the reconciler preserves them and cannot assert that the *entire arbitrary foreign policy* is fail-closed.

## 3. Identity and managed whitelist reconciliation

- Preferred per-device fingerprint: USBGuard **hash**; alternatively **serial + VID/PID**. VID:PID alone identifies a product family, not an individual USB. A hash-only serialless device is supported; a serialless device without a usable hash is not safely whitelisted.
- M2-created grants use `label "usbshield-managed"`. The synchronizer accepts only a single consistent definition per identity, compares full definitions and validates actual rules after applying changes.
- Rule numbers from `usbguard list-rules` are **runtime IDs**, not stable identifiers. Reconciliation **re-lists rules before each removal**, including rollback cleanup, because another removal can renumber the remaining IDs. Rule number and allowed/fingerprint definition must never be conflated.
- Before any mutation, the synchronizer checks the `ImplicitPolicyTarget=block`, canonical base rule, managed/foreign conflicts, rule order and the **entire recognized syntax of each numeric rule line**. Unsupported USBGuard rule attributes/conditions (for example `if ...`) cause a conservative **FAIL** pending operator review. This parser is intentionally a limited verifier, *not* the full upstream USBGuard grammar.
- Unknown trailing tokens and malformed numeric headers (`2: permit ...`) are rejected. Do not rewrite an unsupported foreign rule automatically; inspect the complete policy with an administrator first.
- An append error can mean the rule was applied but the CLI reply was lost. The synchronizer attempts cleanup and throws on failure; neither the caller nor the server should report a successful ACK.
- There is no lock shared with arbitrary administrators/other USBGuard clients: a concurrent out-of-band change after the last `list-rules` check is a remaining race, to be mitigated operationally by controlled policy ownership and native acceptance testing.

## 4. Watch stream, reconnect and data integrity

- M2's listener runs `usbguard watch` only on Linux, restarts after a watch failure (normal production delay: 5 seconds), bounds each line/section and prevents duplicate short-window Insert/Remove reports.
- Only `PresenceChanged` leads to `CONNECTED`/`DISCONNECTED`. `PolicyChanged` and `PolicyApplied` do **not** create physical insertion events.
- Per-device Insert dedup resets appropriately on Remove. Reconnect discards incomplete parsing state but preserves the short dedup window.
- Notification and event transport are independent of one another; a failed popup is not permission to allow a USB or a reason to suppress the server event.
- Windows fixture tests are valuable for parser/reconnect behavior, but they do **not** establish native USBGuard daemon resilience or real kernel authorization.

## 5. Active Ubuntu user versus physical person

- User attribution is based on `loginctl` / systemd-logind active **local** session. A unique trustworthy local session can yield username, UID, session ID, type and seat.
- Remote-only sessions, missing/disappearing sessions, malformed/duplicate identifiers and unresolved ambiguities yield `UNKNOWN`, not a cached or invented user.
- A unique active local TTY may be attributed in the event, but a desktop notification requires a **graphical** X11/Wayland session and a normal-user UID (>= 1000)/valid session.
- The active session is **not forensic proof of the human who physically plugged in the USB**.

## 6. Desktop BLOCK notification

Native notification remains **best-effort feedback** after a USBGuard `PresenceChanged/Insert` of Mass Storage with `block` or `reject` target. No popup for `allow`, HID/non-storage, Remove or an untrusted/unknown/non-graphical user. An independent cooldown suppresses repeats; explicit Remove allows a new insertion notification. The listener uses the **PresenceChanged target**, not an inconsistent `device_rule` state, when classifying the notification.

The listener resolves a UID/session, then M2's notification publisher attempts to connect over a per-UID **local Unix socket** to the GUI session helper. The helper checks the target UID/title/body *and now verifies the connecting process via Linux `SO_PEERCRED` as user `usbshield`*. `ClientNotificationPublisher.DELIVERED` means message passed to the helper, **not proof that a popup was displayed**. A missing helper, D-Bus or notification daemon must leave enforcement/event transport unaffected.

**Post-merge IPC candidate (`d5e156a`, PR #50):** The former global `1777` directory is retired. The GUI helper now binds `/run/user/<UID>/usbshield-notifier/notice.sock` in a logind-owned UID runtime directory, grants only `u:usbshield:--x` search permission on its parent/private directories and `u:usbshield:rw-` on the socket via `setfacl` (`acl` package), and refuses stale/symlink or wrong-owner paths. Both peers validate Linux `SO_PEERCRED`; this does not prove a visual popup or successful Ubuntu connectivity. M2 and M3 must still review effective ACL masks, GNOME/Wayland compatibility, startup after upgrade, and native denial/availability tests. See [IPC design](WEEK4_IPC_ACL_DESIGN.md). The old `1777`/`0660` risk was a **prior-source observation**, not current checked-in behavior.

## 7. Inspection commands for post-Week-4 Ubuntu test

Run on a controlled **Ubuntu 24.04** test endpoint with administrative permission. These commands are examples, **not execution evidence**:

```bash
sudo usbguard get-parameter ImplicitPolicyTarget
sudo usbguard list-rules
sudo usbguard list-devices
sudo systemctl status usbguard usbshield-agent
sudo journalctl -u usbguard -u usbshield-agent --since '10 minutes ago'
loginctl list-sessions --no-legend
loginctl show-session <session-id> -p Name -p User -p Active -p Remote -p Type -p Seat
# Normal GUI account in the graphical session:
printf '%s\n' "$XDG_RUNTIME_DIR"
stat -c '%n %U:%G %a' "$XDG_RUNTIME_DIR" "$XDG_RUNTIME_DIR/usbshield-notifier" "$XDG_RUNTIME_DIR/usbshield-notifier/notice.sock"
getfacl -p -- "$XDG_RUNTIME_DIR" "$XDG_RUNTIME_DIR/usbshield-notifier" "$XDG_RUNTIME_DIR/usbshield-notifier/notice.sock"
id usbshield
```

For a two-endpoint policy-isolation test, use *distinct* Ubuntu Clients A and B plus Admin Server: allow USB X on A, verify B remains blocked, revoke on A, check effective daemon rules on each. Protect enrollment tokens and session information in captured logs.

## 8. Sources

- USBShield Blueprint: class 08, per-endpoint whitelist, `loginctl`, notification and non-root protection.
- Week 4 plan: W4-M2-01…07 and post-Week-4 Ubuntu E2E deferment.
- USBGuard upstream rule language: https://usbguard.github.io/documentation/rule-language
- USBGuard rules manual (supported device attributes/conditions): https://manpages.debian.org/testing/usbguard/usbguard-rules.conf.5.en.html

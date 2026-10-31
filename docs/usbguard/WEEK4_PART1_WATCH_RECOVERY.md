# USBShield — M2 Week 4, Part 1: USBGuard Watch/Recovery Hardening

**Baseline:** `main` `1614f9b4e325e8cb42da87946601810d3c5e53de`  
**Owner:** M2 · **Work ID:** `W4-M2-01` (P0)  
**Status:** Implementation patch prepared; Windows Maven checks **NOT EXECUTED in package creation environment**; owner execution required.

## Scope and observed gaps

Before Part 1, `UsbGuardEventListener` already restricted event emission to `PresenceChanged` and enforced an in-memory deduplication window. However, its watch-loop reconnect reset cleared dedup history, so the same Insert replayed immediately after a watch reconnection could send a duplicate event/notification. The read loop also used unbounded `BufferedReader.readLine()`, which could allocate an arbitrarily large Java string before per-section rejection. The recent-event map could exceed its nominal cap when many distinct events arrived within the same short window.

This patch is **not** a USBGuard daemon fix. It hardens the Agent's input and reconnect behavior.

## Delivered files

| Repository-relative path | Purpose |
| --- | --- |
| `client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardEventListener.java` | Preserve short-window dedup history across watch restart; bound incoming line size to 32,768 characters; cap recent-event keys at 1,024; package-private watch launcher/delay injection for deterministic Windows tests. |
| `client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardWeek4WatchStressTest.java` | Regression for watch replay, Remove/Reinsert, PolicyChanged/Applied noise, malformed/oversized frames, bounded state, source line limits and daemon-unavailable recovery. |
| `docs/usbguard/WEEK4_M2_THREE_PART_PLAN.md` | Work split and owner boundaries for the three parts. |
| `docs/usbguard/WEEK4_PART1_WATCH_RECOVERY.md` | This handoff and owner validation checklist. |

## Operational contract

- Production startup still starts `usbguard watch` **only on Linux**, through `ProcessBuilder("usbguard", "watch")`; default retry is **5 seconds**. Test injection does not expose a new production endpoint.
- Watch stream is accepted one bounded line at a time. An oversized line forces the watch connection to be discarded and retried, rather than allocating an unbounded line or accepting a partial event.
- Reconnect clears partial section parsing state but retains recent Insert/Remove keys for the existing **1,500 ms** dedup window. It does not create a permanent suppression cache. Remove invalidates its matching Insert key and vice versa.
- The in-memory dedup key cache is capped at **1,024** distinct recent keys. Under bursts beyond the cap, the oldest key is evicted: this prioritizes bounded memory over guaranteed deduplication for all devices at unbounded throughput.
- `PolicyChanged` / `PolicyApplied` are **not** interpreted as Insert/Remove. Malformed or oversized sections cannot poison the next well-formed section.
- USBGuard enforcement remains independent of event delivery and desktop notification. No change is made to the USBGuard rule set, policy reconciliation, or M3's Agent transport.

## Owner Windows validation

From the repository root, after extracting the patch into a clean checkout of the baseline commit:

```powershell
 git branch --show-current
 git rev-parse HEAD
 mvn -B -f client-agent/pom.xml clean test
 mvn -B -f client-agent/pom.xml -DskipTests package
 git diff --check
 git status --short
```

The first Maven command must run the new `UsbGuardWeek4WatchStressTest` and all existing Client Agent tests. Preserve the full output, including errors/failures/skips. Do **not** mark PASS based on compilation alone. If a test fails, report the output before committing.

## Post-Week-4 Ubuntu acceptance, NOT EXECUTED

- Verify real `usbguard watch` disconnect/restart/reconnect with events still delivered; correlate journal and server event records.
- Exercise repeated physical Insert/Remove, short reconnect during a device event, and daemon unavailable/recovered transitions. Ensure local enforcement remains fail-closed.
- Check blocked Mass Storage desktop popup on the **correct graphical UID**; no popup for ALLOWED, HID, Remove, or uncertain session; notifier loss must not block transport.
- Collect OS/USBGuard version, commands, exact timestamps, event ID/endpoint and sanitized logs. Do not include enrollment credentials or full personal identifiers.

## Integration notes

M3: no change to `EventSenderService`, `HeartbeatCoordinator`, IPC ACL, or installer.  
M5: this package contains tests only; record their actual result and SHA.  
M1: no API/DTO changes. Part 2 and Part 3 are pending in the three-part plan.

**Suggested commit message:** `fix(usbguard): harden Week 4 watch reconnect and bounded event input`

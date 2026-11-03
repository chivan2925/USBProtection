# USBShield — Reliability Phase 2 (R06/R07/R08/R12/R13)

Baseline: `vinhkhang` commit `d0c07f1a14f5a9d08af20835e8ddee198f6808c3` (2026-11-03). This is a Windows-test candidate, **not an Ubuntu E2E acceptance**.

## Change boundaries

| Finding | Behavior after patch | Ownership |
| --- | --- | --- |
| R06 | Watch reconnection never performs synchronous `list-devices` IPC; a coalesced recovery worker verifies pending policy/insertion state. Query generations still reject stale snapshots. | M2 (`usbguard/`) |
| R07 | A queued event belonging to another enrolled endpoint goes to `dataDir/spool/quarantine/`, preserving its bytes; valid later events can drain. No foreign event is sent using current credentials. | M3 (`outbox/`) |
| R08 | The outbox rejects new writes on exceeding configured event/file-byte quotas; **neither active nor quarantined evidence is evicted automatically**. Audit failure is logged; enforcement remains separate. | M3 (`outbox/`, config) |
| R12 | Newly spooled events use monotonically increasing logical millisecond filename keys, sorted alongside legacy timestamp filenames; the max pending key is recovered after restart. | M3 (`outbox/`) |
| R13 | Malformed JSON is moved to restricted `quarantine/` rather than deleted. If the move fails, drain pauses rather than deleting evidence. | M3 (`outbox/`) |

## Outbox limits and operational response

Configuration in `client-agent/src/main/resources/application.yml` (overridable via environment):

- `USBSHIELD_OUTBOX_MAX_BYTES` — default `67108864` bytes (64 MiB).
- `USBSHIELD_OUTBOX_MAX_EVENTS` — default `10000` files.

Both apply to active spool files **and quarantined evidence**. Non-event files count toward the byte budget too. Reaching either limit causes `spool(...)` to return `false` and logs `[OUTBOX-CAPACITY]`. The producer must not interpret a rejected write as successfully persisted. USBGuard remains the enforcement authority, but new audit telemetry may be unavailable until storage is recovered. These limits are not filesystem quotas shared across processes; the service runs with a single active Agent instance.

On Ubuntu, check capacity and anomalies using `journalctl -u usbshield-agent -g 'OUTBOX-CAPACITY\|OUTBOX-QUARANTINE'`, `du -sh /var/lib/usbshield-agent/spool`, and `find /var/lib/usbshield-agent/spool/quarantine -maxdepth 1 -type f`. Back up quarantined files with restricted access before investigation. **Do not move them directly into the active spool or delete them without operator review**: foreign endpoint events must never be rebound to new credentials. Quarantine is intentionally not retried automatically.

The `quarantine/` directory uses owner-only POSIX access (`0700`) and files use `0600` where supported; Windows inherits ACLs from the spool parent. Confirm Windows ACLs before using the package with sensitive audit evidence. A non-regular active event entry (for example, a symlink) blocks draining for inspection instead of being dereferenced.

## Windows regression and acceptance

Run from the repository root on the exact baseline above **after applying the package**:

```powershell
mvn -f admin-server/pom.xml clean test
mvn -f client-agent/pom.xml clean test
git diff --check
git status --short
```

Required scenarios: (1) USBGuard list-devices IPC blocks while watch reconnects; (2) foreign identity followed by valid event; (3) malformed JSON followed by valid event, bytes retained; (4) capacity rejection with no eviction; (5) same-millisecond FIFO and service restart; (6) existing Phase 1 USBGuard watcher and pending-insert tests remain green.

This patch does not include R09–R11 (Performance), does not introduce HTTPS (S01), and is **not** evidence of Ubuntu native hardware acceptance (S03). Full Ubuntu validation is deferred by owner decision. The package does not push, create a PR, or merge branches.

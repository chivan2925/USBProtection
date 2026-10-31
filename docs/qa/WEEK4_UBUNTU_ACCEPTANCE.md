# Week 4 — Ubuntu acceptance recording sheets

Owner: M5; technical owners: M1/M2/M3/M4. Prepared: 2026-10-28; updated after pull: **2026-10-30 (Asia/Bangkok)**. Work item: **W4-M5-07**; input to W4-M5-01/02/04/06. **Every runtime case below is NOT EXECUTED / DEFERRED until after Week 4.** This document prepares execution; it contains no new Linux, physical USB, daemon, GUI, package or reboot result.

Sources: [Blueprint T01–T28 and DoD](../../USBShield_Project_Blueprint_Week2_Client_Notification_Updated(1).md), [test inventory](WEEK4_TEST_INVENTORY.md), [release validation](WEEK4_RELEASE_VALIDATION.md), [Client Installation Guide](../CLIENT_INSTALLATION.md), [packaging/retry handoff](../client-agent/WEEK3_PACKAGING_AND_RETRY.md), [M2 core](../usbguard/WEEK3_CORE_HARDENING.md) and [M2 session/notification handoff](../usbguard/WEEK3_PART3_SESSION_NOTIFICATION_HANDOFF.md). Current post-pull source: **`751387bf166c528326ae91c6a16a820a6d8fc24a`**; not a frozen candidate. Replace it with the actual accepted source/artifact hashes when testing. The 28 October Windows evidence keeps its original `2013ce7` baseline; [30 October post-pull evidence](week4-evidence/2026-10-30/post-pull-regression/README.md) is separate. [Week 3 Windows acceptance](WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md) does not close these cases.

## Before execution

Use isolated Ubuntu 24.04 amd64 clients A/B with local console/recovery access, approved disposable test storage, an independent keyboard/mouse and two normal GUI accounts for switching. Record real USB VID:PID, serial presence, USBGuard hash and interfaces; pseudonymize identifiers consistently in shared evidence. A/B isolation requires the **same physical device** on two clients, not two HTTP fixtures. The server/Web must use an isolated QA database and owner-provided credentials. Do not use personal USB contents or change production endpoint policy.

M1 supplies the frozen API/DTO contract, Admin Server/React artifact, hashes and the exact launch/stop/restart commands for its isolated server. M2 confirms the base `allow with-interface none-of { 08:*:* }`, exact implicit block, rule order, hash identity and any foreign rules. M3 supplies a real `.deb`, staging/build log, checksum, protected configuration, USBGuard IPC rights/RuleFile and notifier provisioning. [M4's shared-frontend review](evidence/M4_WEEK4_HANDOFF.md) has been [technically acknowledged by M5](evidence/M5_WEEK4_POST_PULL_ACK_20261030.md) for unchanged frontend source; this does not verify real Ubuntu enforcement/ACK or select the final candidate. Missing inputs keep affected cases **NOT EXECUTED**, with the owner/input written in the case record.

The checked-in installer starts/enables services during installation. Its addition of group `usbguard` is not proof that the actual daemon ACL permits every required command. M2/M3 must verify device list/listen, policy list/modify and parameter-list rights as service user before treating apply as operational. Do not repair a failing test with a global allow, VID:PID-only allow, weakened default policy or broad file permissions. Preserve foreign/base rules. Use the endpoint-scoped Admin workflow to exercise the real Agent.

The installation guide contains historical examples and raw `endpoint.json` inspection. That file contains the Agent token: do not copy it into public evidence. Review current scripts and actual results when guide text differs. In particular, `Requires=usbguard.service` does **not** itself establish that restarting USBGuard will automatically revive a stopped Agent; T24 must measure recovery.

| Role | Test activity |
|---|---|
| Lab administrator / M3 | Package installation, protected config, service faults/recovery and reboot on disposable clients; sensitive evidence stays private |
| M2 | Validate USBGuard rules/IPC, physical behavior, attribution and native notifications |
| Normal user (`student01`/`student02`, no administrative group/sudo) | GUI login, USB insert/remove, normal-user denial checks; never supply administrative authentication to a denial test |
| M1 | Start/stop/recover only the isolated Admin Server, confirm API status/ACK semantics |
| M4/M5 | Admin Web actions, A/B desired/applied versions, event/evidence correlation |

## Environment and command groups

Commands below are **instructions, not commands already run**. Run in Bash on the named machine/role. Record each command, start/end time and exit code; collect failure output too. An interactive `watch`/`journalctl -f` terminated with Ctrl+C is an observation stream, not a failed assertion. Put raw private recordings in a new date/run directory, then publish only sanitized copies and hashes through the [Week 4 evidence index](week4-evidence/README.md). Do not overwrite earlier weeks.

**C0 — build/provenance (Linux build checkout; M3).** The repository script invokes Maven with `-DskipTests`; run the test-bearing build separately. Its script recreates `packaging/staging`, so first confirm that path contains only generated staging from this checkout.

```bash
git rev-parse HEAD
git status --short
cat /etc/os-release
uname -a
java -version
mvn -version
dpkg-deb --version
mvn -B -f client-agent/pom.xml clean package
bash packaging/scripts/build-deb.sh
sha256sum client-agent/target/usbshield-agent.jar packaging/dist/usbshield-agent_1.0.0_amd64.deb
dpkg-deb --info packaging/dist/usbshield-agent_1.0.0_amd64.deb
dpkg-deb --contents packaging/dist/usbshield-agent_1.0.0_amd64.deb
```

**C1 — install/configure (administrator; clean client).** Copy the approved package into the current directory and compare its hash with M3's manifest. Review USBGuard configuration before installation because `postinst` can start the daemon. Provision protected settings privately; do not paste enrollment secrets into a transcript. This is a new test even if an older PoC once installed successfully.

```bash
sha256sum usbshield-agent_1.0.0_amd64.deb
sudo apt update
sudo apt install ./usbshield-agent_1.0.0_amd64.deb
sudoedit /etc/usbshield-agent/application.yml
sudo systemctl restart usbshield-agent
dpkg-query -W usbshield-agent usbguard openjdk-21-jre-headless libnotify-bin
```

In the protected config, M3 sets `usbshield.agent.server-url` to M1's QA server, the QA enrollment credential, absolute `data-dir`/`config-dir`, `simulation-enabled: false` and `notification-enabled: true`. The default enrollment value is empty; an unconfigured package is not an enrollment PASS. Temporary shell environment variables are not automatically inherited by systemd.

**C2 — service/policy/permissions snapshot (administrator; A and B).**

```bash
date --iso-8601=seconds
hostnamectl
id usbshield
sudo systemctl is-enabled usbguard usbshield-agent
sudo systemctl status usbguard usbshield-agent --no-pager
sudo systemctl show usbshield-agent -p User -p Group -p ExecStart -p MainPID -p NRestarts -p ActiveState -p SubState
sudo usbguard get-parameter ImplicitPolicyTarget
sudo usbguard list-rules
sudo usbguard list-devices
sudo -u usbshield usbguard get-parameter ImplicitPolicyTarget
sudo -u usbshield usbguard list-rules
sudo -u usbshield usbguard list-devices
sudo stat -c '%U:%G %a %n' /opt/usbshield-agent /opt/usbshield-agent/usbshield-agent.jar /etc/usbshield-agent /etc/usbshield-agent/application.yml /var/lib/usbshield-agent /run/usbshield-notifier
sudo journalctl -u usbguard -u usbshield-agent --since '10 minutes ago' --no-pager
sudo ls -la /var/lib/usbshield-agent/spool
```

The service user must be `usbshield`, live profile explicit, implicit target exactly `block`, and unknown storage must not match an unscoped foreign allow. Record root and service-user IPC results; root success alone is insufficient. Inspect registration/applied/pending ACK privately through M3's approved tooling; export only endpoint ID/version metadata, not the token or full file. Journal output may need redaction.

**C3 — sessions/notifier (normal GUI user; each tested session).** Replace `SESSION_ID` with a session from `loginctl`; its identity is evidence, not a hard-coded `student01` assumption.

```bash
id
loginctl list-sessions --no-legend --no-pager
loginctl show-session SESSION_ID -p Name -p User -p Active -p Remote -p Seat -p Type --no-pager
pgrep -a -f DesktopNotificationClientMain
ls -ld /run/usbshield-notifier
ls -l /run/usbshield-notifier
/usr/bin/notify-send 'USBShield QA helper probe' 'Manual GUI probe; not a blocked USB event'
```

The manual `notify-send` probe tests this session's display; only a real blocked Insert through Agent proves the product popup path. Record UID, socket ownership/mode, session type and visible output. If XDG autostart was installed after login, log out/in normally. M3 can diagnose `/opt/usbshield-agent/usbshield-notifier-launcher.sh` in the same GUI session; avoid starting duplicate helpers.

**C4 — event correlation (administrator; separate terminals).**

```bash
sudo usbguard watch
sudo journalctl -u usbshield-agent -f
sudo usbguard list-devices
lsusb
lsblk -o NAME,TRAN,TYPE,MOUNTPOINTS
```

Before/after each physical insert/reconnect/remove, capture C2/C3 as applicable, source time and the corresponding Admin Event History row/API output through an authenticated QA session. Record event ID, endpoint ID, USB hash, event type, decision, occurredAt, username/UID/session/seat and popup count. `lsusb` enumeration does not prove storage usable; correlate USBGuard target and block-device availability. Do not mount/write personal storage to manufacture a result.

**C5 — controlled fault/recovery (administrator; disposable client).** Collect C2/C4 before/during/after. Use only the subsection required by the case.

```bash
# T16: crash only the Agent main process and observe Restart=on-failure.
sudo systemctl kill --kill-whom=main --signal=SIGKILL usbshield-agent
sudo systemctl show usbshield-agent -p MainPID -p NRestarts -p ActiveState -p SubState
sudo journalctl -u usbshield-agent --since '5 minutes ago' --no-pager

# T24: daemon outage; record dependency effects before recovery.
sudo systemctl stop usbguard
sudo systemctl status usbguard usbshield-agent --no-pager
sudo systemctl start usbguard
sudo systemctl start usbshield-agent

# T11/T12: reboot after saving pre-reboot evidence.
sudo reboot
```

For T17, M1 records and uses the **actual launch/stop/restart command for the isolated server** from its candidate handoff; no server unit name is invented here. Stop that process, verify unreachability from the client, insert unknown test USB, collect local policy/spool/Agent logs and restore the same server/database. Observe delivery using agreed heartbeat/retry intervals. Record whether this tested server process loss, network loss, or both. Do not change client USBGuard policy during this test.

**C6 — normal-user denial (normal account, no sudo).** Never supply administrative credentials. If a stop succeeds, retain the failure and let the administrator restore the service.

```bash
id
systemctl --no-ask-password stop usbshield-agent
systemctl --no-ask-password stop usbguard
test -w /etc/usbshield-agent/application.yml
test -w /etc/usbguard/rules.conf
test -w /var/lib/usbshield-agent/endpoint.json
```

Expect nonzero status for stop attempts and for each `test -w`; capture policy/ACL details if results differ. These checks avoid modifying files. Compare service state afterward with C2. A user in a privileged USBGuard/admin group is not a valid denial-test subject.

**C7 — auth rejection (QA client; no cookies).** Set `QA_SERVER_URL` to the isolated origin and `QA_ENDPOINT_ID` to a real QA endpoint shown by Web. Both are nonsecret; the token below is deliberately invalid. Do not replace it with a real token in published transcripts.

```bash
curl -i "$QA_SERVER_URL/api/agent/policy" -H "X-Endpoint-Id: $QA_ENDPOINT_ID" -H 'X-Agent-Token: deliberately-invalid-qa-token'
curl -i "$QA_SERVER_URL/api/admin/events"
```

M1 confirms the frozen status contract (current tests expect 401 for invalid Agent credential and unauthenticated Admin read). Preserve exact status and verify no protected data leaked. Valid browser login/CSRF coverage belongs to integrated server/UI tests.

**C8 — removal/reinstall (administrator; disposable client only).** After other cases, privately back up protected config/registration/policy/spool with owner-approved permissions and record hashes/metadata. Retention is a behavior to measure; no upgrade-runtime result is proven. Do not use the guide's optional `rm -rf` cleanup for this test.

```bash
sudo apt remove usbshield-agent
sudo systemctl status usbshield-agent --no-pager
sudo systemctl status usbguard --no-pager
sudo ls -ld /opt/usbshield-agent /etc/usbshield-agent /var/lib/usbshield-agent
sudo apt install ./usbshield-agent_1.0.0_amd64.deb
```

Record package file removal, Agent stop/disable, residual config/data/rules and endpoint identity after reconfiguration/restart. Missing directories/service units after removal can be expected; capture exit codes. Same-package reinstall is not a version upgrade. A distinct owner-built upgrade/rollback artifact and documented retention behavior are needed for that extension; do not purge credentials to make reinstall look clean.

## Per-case recording register

Copy the record template below for each row and execute cited command groups plus its physical/UI action. Shared command groups collect evidence; snapshots alone do not complete behavior tests. Windows tests and historical PoC captures do not substitute for Actual.

| Case | Owner / DoD | Action and expected result | Required evidence | Execution / Actual |
|---|---|---|---|---|
| T01 | M3/M1; DoD-02 | C1/C2; configured real Agent enrolls and endpoint appears once in Web | Package/source hashes, sanitized enroll/registration metadata, endpoint/server record | **NOT EXECUTED / DEFERRED**; no actual result |
| T02 | M3/M1; DoD-02 | Wait configured heartbeat cycles; lastSeen advances, ONLINE comes from accepted heartbeat | Agent/server timestamps, endpoint ID, before/after lastSeen/status | **NOT EXECUTED / DEFERRED**; no actual result |
| T03 | M2/M3; DoD-03 | C3/C4 with active `student01`; insert unknown class-08 storage; actual BLOCK/REJECT, storage unusable | Device identity, USBGuard stream/target, block-device observation, user/time | **NOT EXECUTED / DEFERRED**; no actual result |
| T04 | M5/M1/M2/M3; DoD-03 | Find T03 event in Web; endpoint/user/USB/BLOCKED/source time match | Agent event, server ID/payload and UI screenshot correlated to T03 | **NOT EXECUTED / DEFERRED**; no actual result |
| T05 | M2; DoD-04 | C4; reconnect keyboard/mouse and operate them; non-storage remains usable | Actual HID target/interface and usability capture, storage still blocked | **NOT EXECUTED / DEFERRED**; no actual result |
| T06 | M1/M4/M5; DoD-05 | Admin → endpoint A → Allow specific USB; desired version increases only A | A/B desired/applied/whitelists before/after, UI/API endpoint scope | **NOT EXECUTED / DEFERRED**; no actual result |
| T07 | M2/M3/M1; DoD-05 | Real Agent pull/reconcile/save/ACK; A becomes ALLOWED after local apply | Specific managed rule, Agent logs, durable version, server ACK/applied | **NOT EXECUTED / DEFERRED**; no actual result |
| T08 | M2/M3; DoD-05 | Safely remove/reconnect same device on A; stays ALLOWED | Stable identity, new presence events, target/storage availability | **NOT EXECUTED / DEFERRED**; no actual result |
| T09 | All; DoD-06 | Move same device to B with no B Allow; B stays BLOCKED | A/B policy snapshots, B target/user/event, common hash | **NOT EXECUTED / DEFERRED**; no actual result |
| T10 | All; DoD-05/06 | Revoke only A, real apply/ACK, reconnect on A; BLOCK again and B unchanged | Rules/versions/ACK, physical BLOCK, A/B events | **NOT EXECUTED / DEFERRED**; no actual result |
| T11 | M3/M2; DoD-07 | C5 reboot, C2 after login; services auto-start, live non-root Agent | Boot/time, enabled/active, ExecStart/profile, journal/heartbeat | **NOT EXECUTED / DEFERRED**; no actual result |
| T12 | M2/M3; DoD-07 | Keep specific A allow and unknown test device before T11; verify ALLOW/BLOCK after reboot | Pre/post rules/implicit target, credential/version metadata, physical events | **NOT EXECUTED / DEFERRED**; no actual result |
| T13 | M2/M3/M5; DoD-03 | GUI switch `student01` → `student02`, C3, reinsert | Fresh session snapshot, new user UID/session in Agent/server/UI; no stale user | **NOT EXECUTED / DEFERRED**; no actual result |
| T14 | M2/M3; DoD-03 | Log out GUI users, keep remote admin access; confirm no local active session, insert | loginctl, UNKNOWN/reason, event delivered, no wrong-user popup | **NOT EXECUTED / DEFERRED**; no actual result |
| T15 | M2/M3/M5 | C4; remove device after recorded Insert | One correlated DISCONNECTED event, no false Insert or BLOCK popup | **NOT EXECUTED / DEFERRED**; no actual result |
| T16 | M3; DoD-07 | C5 Agent SIGKILL; observe restart, credential reuse, resumed heartbeat/watch | Old/new PID, NRestarts/journal, same endpoint, no lost saved spool/ACK | **NOT EXECUTED / DEFERRED**; no actual result |
| T17 | M2/M3/M1; DoD-09 | C5 server outage; unknown storage inserted, daemon active; recover | Server unreachable, unchanged fail-closed/actual BLOCK, private spool, delivered event/ACK | **NOT EXECUTED / DEFERRED**; no actual result |
| T18 | M3; DoD-08 | C6 normal-user stop attempts on both services | id/groups, denied commands/status, services still active | **NOT EXECUTED / DEFERRED**; no actual result |
| T19 | M3/M2; DoD-08 | C6 writable checks on rules/config/registration | Nonzero checks, owner/mode/ACL, no content mutation | **NOT EXECUTED / DEFERRED**; no actual result |
| T20 | M1; DoD-02 | C7 invalid token on real QA endpoint; reject | Redacted status/body and matching server time, no protected data | **NOT EXECUTED / DEFERRED**; no actual result |
| T21 | M1/M4; DoD-01 | C7 unauthenticated Admin read; reject | HTTP 401/403 per frozen route contract, no data; browser Login where applicable | **NOT EXECUTED / DEFERRED**; no actual result |
| T22 | M3; DoD-02 | C0/C1 fresh image; dependencies/files/users/modes/profile/service correct | Real `.deb`/staging provenance, apt output/exit, C2, actual enrollment | **NOT EXECUTED / DEFERRED**; no actual result |
| T23 | M3; DoD-02 | C8 remove after other tests, inspect retention and reinstall | Package/service/files, documented vs actual endpoint/config/rule retention, base policy | **NOT EXECUTED / DEFERRED**; no actual result |
| T24 | M2/M3/M1 | C5 stop USBGuard; observe true daemon/Agent/server state; restart both | Failure/degraded evidence, dependency effect, no false apply/ACK, actual recovery | **NOT EXECUTED / DEFERRED**; no actual result |
| T25 | M2/M3/M5; DoD-11 | C3/C4 active GUI `student01`, unknown storage Insert → BLOCK and one native popup for UID | Video/screenshot with UID/time, watch/event, socket/helper metadata | **NOT EXECUTED / DEFERRED**; no actual result |
| T26 | M2/M3; DoD-11 | Allowed storage Insert, HID Insert and Remove; no BLOCK popup | Each action time/popup count, target, user/helper and events | **NOT EXECUTED / DEFERRED**; no actual result |
| T27 | M2/M3; DoD-11 | No GUI; separately unavailable helper with GUI; unknown storage still blocked/event retained | Session/helper/socket state, failure/no wrong-session popup, USBGuard/events | **NOT EXECUTED / DEFERRED**; no actual result |
| T28 | M2/M3; DoD-11 | Real blocked Insert with PolicyChanged/Applied; Remove/reinsert | Watch sections: one event/popup per eligible insertion; later insertion can notify again | **NOT EXECUTED / DEFERRED**; no actual result |

T24's Blueprint wording expects Agent/Admin **DEGRADED**. Actual health/status display and dependency effects need M1/M2/M3's frozen-contract confirmation. If only OFFLINE/UNKNOWN/logs are available, record that behavior and file the discrepancy; do not rename it DEGRADED or silently PASS. For T27, stop only M3's identified per-user helper process using its recorded PID, or use a dedicated test GUI account without autostart, then restore it. Disabling notification globally is not a transport-failure test.

## Additional runtime extensions and DoD coverage

T06/T20/T21 already have fresh Windows HTTP acceptance evidence in the [Week 4 inventory](WEEK4_TEST_INVENTORY.md). Their rows here are **deployment reruns on Ubuntu**, not a reversal of those Windows results. They remain NOT EXECUTED only for the deployment environment described by these sheets.

Every extension is also **NOT EXECUTED / DEFERRED** and uses the recording template.

| Extension | Related case / owner | Required execution and evidence |
|---|---|---|
| U29 — no-serial storage | T03/T06–10; M2/M3/M5 | Real empty-serial storage/stable hash, reconnect, Allow/Revoke A and BLOCK B; no VID:PID-only rule. Missing hardware leaves NOT EXECUTED. |
| U30 — ambiguous/remote-only sessions | T13/T14/T27; M2/M3 | Record all candidates/selector result; no arbitrary user. Mocked ambiguity stays unit-only evidence if lab cannot reproduce simultaneous active local sessions. |
| U31 — pending ACK/outbox across restart | T11/T12/T16/T17; M3/M1 | Controlled connection fault after apply, saved ACK/event metadata, restart/reboot and delivery; no false ACK after apply/save failure. Owner provides fault procedure; do not corrupt registration/fill host disk. |
| U32 — upgrade/rollback | T22/T23; M3 | Two versioned artifacts/manifests, protected backup, exact commands, retained endpoint/config/policy/spool. Same-version reinstall does not complete this. |
| U33 — narrated Ubuntu demonstration | T01–17/T25–28; all | [Demo Script](../DEMO_SCRIPT.md) with physical devices/GUI, source/artifact hashes, presenter/reviewer, start/end and sanitized video checksum. |
| U34 — bounded input and watch reconnect | T15/T16/T24/T28; M2/M3 | On a disposable lab client, use M2's approved fault/fixture procedure to interrupt watch output/IPC, observe restart and inject malformed/oversized synthetic input separately from physical events. Keep the default block/base rules. Capture stream boundary, restart delay, process/memory behavior, dropped-input diagnostics and recovery to the next valid physical event. No unfinished pre-reconnect section may become a new event; replay within the 1.5-second dedup window must not duplicate telemetry/popup, while Remove→Insert stays distinct. |

U34 is **NOT EXECUTED / DEFERRED**. The current source bounds watch lines/sections at 32,768 characters and recent-event state at 1,024 entries; default watch retry delay is 5 seconds. These are implementation parameters, not measured Ubuntu runtime results or a guarantee that replay outside the dedup window is suppressed. M2 supplies the isolated injection procedure and expected counters before this extension runs; do not feed synthetic records into the production daemon or present fixture input as physical USB evidence. Reconcile actual session, policy and notifier expectations with M2's remaining core handoff and M3's final guide.

The 30 October Windows Agent run passed **136/136** tests, including the **9/9** new watch/stress tests; current JAR/static verification and [post-pull evidence validation](week4-evidence/2026-10-30/post-pull-regression/validation.json) also **PASS**. Those results support preparation of U34; they do not execute its Ubuntu commands, daemon recovery, actual USB/session behavior or popup checks. M1's final candidate decision, M2's remaining core/contract handoff and M3's final package/guide remain open.

DoD-01: real server startup, Web login and H2 survival over controlled restart using M1's actual commands/artifacts (T21 plus server/UI evidence). DoD-02: T01/T02/T22/T23. DoD-03: T03/T04/T13/T14. DoD-04: T05. DoD-05: T06/T07/T08/T10. DoD-06: T09/T10. DoD-07: T11/T12/T16 and U31. DoD-08: T18/T19. DoD-09: T17. DoD-10: owner review of [Test Plan](../TEST_PLAN.md), guides, API/protocol and [Demo Script](../DEMO_SCRIPT.md); existence is not proof that commands ran. DoD-11: T25–28. Link executed records to the [inventory](WEEK4_TEST_INVENTORY.md) before proposing full release.

## Copy once per executed case

```text
Case / Work ID:
Execution status: NOT EXECUTED / DEFERRED (replace only after execution)
Owner / operator / reviewer:
Start/end date and timezone:
Candidate Git SHA / working-tree changes / source manifest:
Server JAR / Agent JAR / DEB / frontend hashes:
Ubuntu / kernel / Java / USBGuard versions:
Endpoint A / B identifiers (pseudonymized consistently):
Device identity and interface / session snapshot:
Preconditions and linked command groups:
Exact commands / actions / exit codes:
Expected:
Actual: NOT RECORDED
Agent / USBGuard / server event and ACK correlation:
Evidence paths / SHA256 / redactions:
Verdict: NOT EXECUTED (PASS / FAIL only with evidence)
Issue / limitation / dependency owner:
Cleanup and restored service/policy/helper state:
Reviewer sign-off / date:
```

The full Ubuntu release gate stays open until required runtime cases run, failures are resolved/dispositioned and owners accept the exact candidate. A finished checklist or Windows PASS does not authorize `FULL RELEASE PASS`.

# USBShield — Pre-Ubuntu code review, branch `vinhkhang`

**Reviewed commit:** `2dafa7b59e633d4d78f70940899d3e135134f99b` (2026-11-02)  
**Target:** Ubuntu 24.04 LTS amd64; M1 Admin Server, M2 USBGuard/session/notification, M3 Agent/packaging, M4 Admin Web, M5 QA.  
**Verdict:** **CHANGES RECOMMENDED BEFORE NATIVE TESTS**; this patch addresses concrete Agent reliability/privacy findings. Native USBGuard, clean `.deb` lifecycle, systemd, GUI, hardware and LAN connectivity remain **NOT EXECUTED**.

## Evidence boundaries

- GitHub `vinhkhang` is the source baseline. `admin-server/`, `frontend/`, and `client-agent/` trees match `main` at `0fb91dfac488c709784f9bb13b1b7c84db4c5b79`. The branch's eight changes concern packaging and documents. No code-change claim is made for the already reviewed backend/frontend trees.
- The owner reports two Client Agent Maven commands **BUILD SUCCESS** on the original branch (before this patch). Their result is NOT inherited by modified source. The previous Windows M1/M4/M5 browser/backend/QA results remain historical, not new Ubuntu proof.
- In this audit, Java 21 isolated compilation of the three patched classes **PASS** using the repository's existing executable JAR dependencies and manual logging replacement for Lombok. A direct Java harness reproduced an endpoint-identity failure with the old class and passed after the fix: 2 pre-enrollment events sent after binding to the durable endpoint ID, a foreign-endpoint event preserved without forwarding, atomic spool files/cleanup. A loopback server deliberately withholding responses confirmed a bounded HTTP read timeout (~5.1s). These are **isolated checks, not Maven JUnit or native integration**.
- Packaging branch content was matched against exact Git blob SHA from GitHub. Debian `control`, service/desktop paths, maintainer-script syntax and assembled `.deb` structure checked under Linux. The structural candidate used the previously supplied JAR, NOT a newly built application binary or installed Ubuntu artifact.

## Corrected findings

| ID | Severity | Evidence / cause | Correction | Verification |
|---|---|---|---|---|
| UPR-01 | HIGH | `UsbGuardEventListener` may spool `POC-UNENROLLED-<hostname>` before enrollment; `EventSenderService.emitUsbEvent` can spool `UNENROLLED`. After the Agent enrolls, the spool previously transmitted these unchanged as `endpointId` while presenting new authenticated endpoint headers. Server identity binding rejects the event; a failed head-of-queue blocks later FIFO delivery. | Rebind **only these two documented placeholder formats** to the durable enrolled endpoint before transmission. Preserve queued events with any *other actual* endpoint ID and flag for operator review; never silently reassign a real identity. | Old behavior reproduced; new isolated runtime harness PASS; JUnit regression cases added, **Maven rerun required**. |
| UPR-02 | HIGH | Default Spring `SimpleClientHttpRequestFactory` was constructed without explicit connect/read limits. A non-responsive Server could hold the heartbeat or USBGuard event-listener call indefinitely. | Set 3-second connect timeout and 5-second read timeout on production `AdminServerClient`; injection constructor remains unchanged for mock/integration suites. | Java 21 compile PASS; stalled loopback peer timed out around 5.1s; full Maven rerun required. |
| UPR-03 | MEDIUM | Old outbox wrote directly to the visible `event_*.json`. A crash during write could produce a partial file later classified as corrupted and deleted. | Serialize into a private same-directory temporary file, sync its file descriptor, then atomically rename when supported (fallback to normal rename if atomic move unsupported). | Isolated write/reload PASS; JUnit regression added. Disk/VM power-loss guarantee still requires native testing. |
| UPR-04 | MEDIUM | Agent INFO journal messages included active username/full USB device payload, possibly serial/hash. Unavailable outbox logging also printed the event. | Minimize these INFO/ERROR messages to event type, decision, generic failure and spool filename; retain details only in authorized transport/persistence. | Source inspection; Maven rerun required. |
| UPR-05 | MEDIUM | The Debian `postinst` base-rule preflight used an unanchored pattern, so a narrower rule such as `allow with-interface none-of { 08:*:* } if false` could pass installer validation despite not being the canonical non-storage allow rule; USBGuard policy reconciliation would then fail or the rule would not protect peripheral usability. | Anchor the installer pattern to the end of the rule (allow an optional trailing comment only), rejecting extra rule attributes/conditions. Do not overwrite operator USBGuard rules. | `bash -n` PASS; targeted grep fixtures: canonical PASS, canonical + comment PASS, narrower/extra condition REJECTED. Native USBGuard acceptance still required. |

## Review coverage and remaining native risks

- **Client Agent:** inspected all 59 main Java paths by local inventory/static search and reviewed policy-reconcile, watch parser/recovery, loginctl active-user selection, notification publisher/peer/ACL, enrollment, heartbeat, policy ACK, HTTP, outbox. Existing 179 JUnit tests are historical; only new tests address above regressions.
- **Packaging:** `postinst`, `prerm`, `control`, systemd unit, XDG launcher, default policy, `build-deb.sh`, Windows static verifier, notifier Ubuntu ACL probe inspected; the base-rule preflight in `postinst` was tightened in this overlay. USBGuard user ACL `Devices=list,listen`, `Policy=list,modify`, `Parameters=list` matches documented command needs; actual effective access on Ubuntu must be checked using service identity. Shell syntax/structural package checks do **not** prove `apt install`, ACL or service startup.
- **Admin Server:** reviewed auth/agent identity binding and policy API; server default `prod` binds `127.0.0.1` and enables Secure session cookie. A LAN Client **cannot** reach this default address. The operator must explicitly configure a reachable bind/proxy/TLS and point the Client's `USBSHIELD_SERVER_URL` to that address. Do not disable the Secure cookie for an exposed production deployment.
- **Frontend:** reviewed production API/CSRF adapters and demo/mock separation. No new evidence of a frontend blocker from examined paths; unchanged branch tree and historical browser tests do not prove Ubuntu hardware flow.
- **USBGuard live policy vs existing device:** `append-rule`/`remove-rule` changes the rule set, but must not be assumed to immediately authorize/deauthorize an already-present USB device. Record **current device state** both immediately and after physical re-insertion; if the expected immediate behavior is mandatory, it requires separate design/permissions (`Devices=modify`) and tests. Do not silently change USBGuard privilege grants without that decision.
- **Fresh Ubuntu USBGuard:** the distro may generate a nonempty existing `/etc/usbguard/rules.conf`; `postinst` deliberately refuses a file lacking the canonical non-storage base rule rather than replacing administrator policy. This is a safe stop requiring an operator backup/review. No auto-rewrite was introduced. Perform clean VM/snapshot testing before touching a real workstation; USBGuard policy errors can affect input devices.
- **Data-dir / credentials:** legacy fallback `./data` in endpoint/outbox initialization remains a conditional durability risk if `/var/lib/usbshield-agent` is inaccessible. Check ownership and service logs. Historical Git credential exposure still requires real rotation/re-enrollment before public deployment.

## Execution gate for the owner

After applying this overlay to branch `vinhkhang`, rerun **both** commands from the repository root on Windows:

```powershell
mvn -B -f client-agent/pom.xml clean test
mvn -B -f client-agent/pom.xml -DskipTests package
```

Do not claim these new tests PASS until their actual output is available. If successful, review the exact diff, commit/PR/merge the patch, and build a fresh `.deb` from the merged SHA on Linux. The only verified outputs here are structural checks and isolated Java harness checks. Then proceed with the existing `docs/qa/WEEK4_UBUNTU_ACCEPTANCE.md` matrix on an isolated Ubuntu 24.04 VM.

**Minimum operational checks during Ubuntu tests:**

1. Inspect `dpkg-deb --info`/`--contents`, preserve current USBGuard configs, perform first install/upgrade/remove tests on a VM snapshot; check `systemctl status usbguard usbshield-agent` and persistent journal.
2. Verify **as the service user**: `sudo runuser -u usbshield -- usbguard list-rules`, `list-devices`, `get-parameter ImplicitPolicyTarget`; read actual ACLs on the notifier socket with `getfacl`. No broad chmod workaround.
3. Confirm Admin Server is reachable from each Ubuntu endpoint (not `127.0.0.1` of the Client). Use a secure deployment for Admin session and enrollment credentials.
4. Insert unknown USB storage and HID; verify effective USBGuard block/allow, actual event and active login session, and one native popup for the correctly active GUI user. Repeat with GUI absent, Server down, Server restored and reboot.
5. Verify policy *desired*, ACK and *effective USB authorization* separately, on **two endpoints** both with and without a physical USB reconnect.

**Release criterion:** Do **not** label `FULL RELEASE PASS` before the real Ubuntu gate and credential remediation have been verified. No review can guarantee a zero-error runtime outcome in advance.

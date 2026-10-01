# USBGuard Proof of Concept — Week 1

> **Project:** USBShield Ubuntu  
> **Role:** Member 2 — Linux/USBGuard Core + Active User Attribution Engineer  
> **Scope:** Week 1  
> **Guest:** Ubuntu 24.04 LTS on VMware Workstation  
> **PoC status:** PASS for the core Week 1 gate, with explicitly documented unexecuted/not-captured cases

---

## 1. Environment

- **Host OS:** Windows 11
- **VMware:** VMware Workstation 26H1u1
- **Guest OS:** Ubuntu 24.04.5 LTS (Noble Numbat)
- **Hostname:** `usbshield-lab`
- **Kernel:** `7.0.0-34-generic`
- **Architecture:** `x86_64`
- **VMware Tools:** `13.0.10.0 (build-25056151)`
- **USBGuard package version:** `1.1.2+ds-6build2` (captured with `dpkg-query`).

Environment evidence:

```text
PRETTY_NAME="Ubuntu 24.04.5 LTS"
VERSION_ID="24.04"
VERSION="24.04.5 LTS (Noble Numbat)"

Linux usbshield-lab 7.0.0-34-generic #34~24.04.1-Ubuntu ... x86_64 GNU/Linux

Architecture:
x86_64

VMware Tools:
13.0.10.0 (build-25056151)

USBGuard package:
usbguard 1.1.2+ds-6build2
```

---

## 2. Goal

Week 1 PoC aims to prove the following:

1. Detect USB devices through USBGuard.
2. Identify USB Mass Storage by interface class `08`.
3. Block unknown USB Mass Storage.
4. Allow USB devices that are not Mass Storage.
5. Permanently allow a specific USB Mass Storage device.
6. Verify policy behavior after reconnect, USBGuard service restart, and system reboot.
7. Observe USBGuard device/event information.
8. Resolve the active local Ubuntu user/session using `loginctl`.
9. Combine endpoint, active user, USB information, decision, and timestamp.
10. Parse `usbguard list-devices` output with a Java PoC.
11. Produce a sample Agent event contract.

The active-user result represents the **active local Ubuntu session at the time of the event**. It does not claim to prove which physical person inserted the USB device.

---

## 3. Base Policy v0.1

Project policy:

```text
# USBShield base policy v0.1
# Allow USB devices that do not expose Mass Storage class 08.
# Unknown Mass Storage will fall through to ImplicitPolicyTarget=block.

allow with-interface none-of { 08:*:* }
```

Runtime implicit policy:

```text
ImplicitPolicyTarget=block
```

Current runtime rules:

```text
1: allow with-interface none-of { 08:*:* }
```

### Policy behavior

```text
USB without class 08
    -> matches none-of { 08:*:* }
    -> ALLOW

USB containing class 08
    -> base allow rule does not match
    -> falls through
    -> ImplicitPolicyTarget=block
    -> BLOCK
```

---

## 4. Test USB Devices

### USB A — Kingston DataTraveler 2.0

- **Type:** USB Mass Storage
- **Name:** `DataTraveler 2.0`
- **Vendor:** Kingston Technology
- **VID:** `0951`
- **PID:** `1665`
- **Serial:** `C81F660E8BE8FFA14601FEF6`
- **Hash:** `xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=`
- **Interface:** `08:06:50`
- **Mass Storage:** `true`
- **Unknown-device state:** `BLOCK`
- **Permanent-whitelist state:** `ALLOW`

Observed USBGuard device line while unknown:

```text
10: block id 0951:1665 serial "C81F660E8BE8FFA14601FEF6" name "DataTraveler 2.0" hash "xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=" parent-hash "Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=" via-port "3-2" with-interface 08:06:50 with-connect-type "unknown"
```

`lsusb` also identifies the physical device:

```text
Bus 003 Device 006: ID 0951:1665 Kingston Technology Digital DataTraveler SE9
```

### USB B — Physical USB Optical Mouse

- **Type:** USB HID mouse
- **Name:** `USB Optical Mouse`
- **VID:** `093a`
- **PID:** `2510`
- **Serial:** empty
- **Hash:** `L46bZEAKg59EA+puzjHMh8D4jLliJqK4sYxKSd/DRz8=`
- **Interface:** `03:01:02`
- **Mass Storage:** `false`
- **Observed state:** `ALLOW`

Evidence:

```text
10: allow id 093a:2510 serial "" name "USB Optical Mouse" hash "L46bZEAKg59EA+puzjHMh8D4jLliJqK4sYxKSd/DRz8=" parent-hash "Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=" via-port "3-2" with-interface 03:01:02 with-connect-type "unknown"
```

The VM also exposes a VMware virtual USB mouse:

```text
allow id 0e0f:0003 ... name "VMware Virtual USB Mouse" ... with-interface 03:01:02
```

The physical mouse above is the relevant HID passthrough evidence for this PoC.

---

## 5. Results

| Test | Expected | Actual | Result |
|---|---|---|---|
| Unknown Flash Disk | `BLOCK` | Kingston DataTraveler 2.0 reported `block` | **PASS** |
| Mass Storage interface | contains class `08` | `08:06:50` | **PASS** |
| Specific permanent allow | `ALLOW` | Permanent rule added for `0951:1665` | **PASS** |
| Reconnect unknown Flash Disk | consistently `BLOCK` | All 3 reconnect attempts were blocked | **PASS** |
| USBGuard service restart | policy remains effective | Flash Disk remained blocked after service restart test | **PASS** |
| Reboot persistence | permanent allow remains | Device remained `ALLOW` after reboot | **PASS** |
| Physical USB mouse | `ALLOW`, class `03` | `USB Optical Mouse`, `03:01:02`, `allow` | **PASS** |
| Multiple USB Mass Storage | independent handling | Only one USB Mass Storage device was available | **NOT EXECUTED** |
| Active local user | correct username/UID/session | `usbdev`, UID `1000`, local active Wayland session | **PASS** |
| Resolver not hard-coded | second user resolves correctly | `student01`, UID `1001`, session `19` | **PASS** |
| Resolver ambiguity | return `UNKNOWN`, do not guess | Two simulated equally valid candidates returned `UNKNOWN` | **PASS (SIMULATED LOGIC TEST)** |
| Combined Agent event | endpoint + user + USB + decision | Sample contract produced for physical USB mouse | **PASS (PoC)** |
| Java USBGuard parser | parse core fields and detect Mass Storage | DataTraveler parsed with `massStorage = true` | **PASS** |
| USBGuard event stream evidence | Remove/Insert/policy events captured | `Remove → Insert → PolicyChanged → PolicyApplied`, final target `block` | **PASS** |

---

## 6. Unknown Flash Evidence

USBGuard identified the Kingston DataTraveler as blocked:

```text
10: block id 0951:1665 serial "C81F660E8BE8FFA14601FEF6" name "DataTraveler 2.0" hash "xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=" parent-hash "Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=" via-port "3-2" with-interface 08:06:50 with-connect-type "unknown"
```

The blocked-device-only query returned the same device:

```text
10: block id 0951:1665 serial "C81F660E8BE8FFA14601FEF6" name "DataTraveler 2.0" hash "xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=" parent-hash "Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=" via-port "3-2" with-interface 08:06:50 with-connect-type "unknown"
```

Relevant observations:

```text
state     = block
VID:PID   = 0951:1665
interface = 08:06:50
```

Therefore the device contains Mass Storage class `08` and does not match the generic non-storage allow rule.

---

## 7. Whitelist Evidence

After permanently allowing the Kingston device, USBGuard rules contained a specific allow rule:

```text
1: allow with-interface none-of { 08:*:* }
11: allow id 0951:1665 serial "C81F660E8BE8FFA14601FEF6" name "DataTraveler 2.0" hash "xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=" parent-hash "Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=" with-interface 08:06:50 with-connect-type "unknown"
```

This demonstrates that:

```text
generic non-storage rule
+
specific permanent allow rule for the tested DataTraveler
```

can coexist in the policy.

---

## 8. Reboot Evidence

### Before reboot

The Kingston DataTraveler was permanently whitelisted through a specific USBGuard allow rule.

### After reboot

The rules were still present:

```text
1: allow with-interface none-of { 08:*:* }
2: allow id 0951:1665 serial "C81F660E8BE8FFA14601FEF6" name "DataTraveler 2.0" hash "xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=" parent-hash "Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=" with-interface 08:06:50 with-connect-type "unknown"
```

Observed result:

```text
Specific DataTraveler remained ALLOW after reboot.
```

**Result: PASS**

> The numeric USBGuard policy-rule ID changed between observations. The rule content, not the numeric ID, is the persistent identity relevant to this check.

---

## 9. HID Evidence — USB Mouse

Physical USB mouse evidence:

```text
10: allow id 093a:2510 serial "" name "USB Optical Mouse" hash "L46bZEAKg59EA+puzjHMh8D4jLliJqK4sYxKSd/DRz8=" parent-hash "Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=" via-port "3-2" with-interface 03:01:02 with-connect-type "unknown"
```

Interpretation:

```text
state     = allow
interface = 03:01:02
class     = 03 (HID)
class 08  = absent
```

Therefore the device matches:

```text
allow with-interface none-of { 08:*:* }
```

and is allowed.

**Result: PASS**

---

## 10. USBGuard Event Evidence

The event stream was captured with:

```bash
sudo usbguard watch
```

while disconnecting and reconnecting the physical Kingston DataTraveler.

Captured output:

```text
[IPC] Connected
[device] PresenceChanged: id=10
event=Remove
target=block
device_rule=block id 0951:1665 serial "C81F660E8BE8FFA14601FEF6" name "DataTraveler 2.0" ... with-interface 08:06:50 ...

[device] PresenceChanged: id=11
event=Insert
target=block
device_rule=block id 0951:1665 serial "C81F660E8BE8FFA14601FEF6" name "DataTraveler 2.0" ... with-interface 08:06:50 ...

[device] PolicyChanged: id=11
target_old=block
target_new=block
...

[device] PolicyApplied: id=11
target_new=block
...
```

The complete raw output is stored in:

```text
docs/usbguard/evidence/13-usbguard-watch-full.txt
```

Interpretation:

```text
physical USB removed
→ PresenceChanged / Remove

physical USB reinserted
→ PresenceChanged / Insert

USBGuard evaluates policy
→ PolicyChanged

policy is enforced
→ PolicyApplied
→ target_new=block
```

The inserted device is the same physical Kingston DataTraveler:

```text
VID:PID   = 0951:1665
interface = 08:06:50
target    = block
```

**Result: PASS**

---

## 11. Active User Attribution Evidence

### Session listing

```text
2 1000 usbdev seat0 tty2 active no -
```

### Session detail

```text
User=1000
Name=usbdev
Seat=seat0
Remote=no
Type=wayland
Active=yes
```

### Resolver output

```text
status=RESOLVED
username=usbdev
uid=1000
sessionId=2
sessionType=wayland
seat=seat0
```

Resolved `ActiveUserInfo`:

```text
username    = usbdev
uid         = 1000
sessionId   = 2
sessionType = wayland
seat        = seat0
remote      = no
active      = yes
```

This satisfies the Week 1 resolver rule:

```text
Active=yes
AND Remote=no
```

with graphical local session on `seat0`.

### Second-user test

A second graphical login was also tested:

```text
status=RESOLVED
username=student01
uid=1001
sessionId=19
sessionType=wayland
seat=seat0
```

This demonstrates that the resolver is not hard-coded to `usbdev`.

### Ambiguity behavior

A simulated resolver-logic test was executed with two equally valid candidates:

```text
10|student01|1001|wayland|seat1
11|student02|1002|wayland|seat2
```

Observed output:

```text
status=UNKNOWN
reason=ambiguous-active-local-sessions
candidate=10|student01|1001|wayland|seat1
candidate=11|student02|1002|wayland|seat2
```

**Result: PASS (SIMULATED LOGIC TEST)**

This verifies:

```text
more than one equally valid candidate
→ UNKNOWN
→ do not guess
```

This is a simulated logic test, not a real multi-session integration test.

Reproducible script:

```text
docs/usbguard/active-user-poc/test-ambiguity.sh
```

Evidence:

```text
docs/usbguard/evidence/resolver-ambiguity.txt
```

---

## 12. Combined Agent Event Contract

### Manual combined input

Captured endpoint/user context:

```text
=== HOSTNAME ===
usbshield-lab

=== ACTIVE USER ===
status=RESOLVED
username=usbdev
uid=1000
sessionId=21
sessionType=wayland
seat=seat0

=== TIMESTAMP ===
2026-09-28T14:10:16+07:00
```

At that time the physical USB mouse was present and allowed.

### Sample Agent event JSON

```json
{
  "endpointId": "POC-UNENROLLED-LAB-PC-01",
  "hostname": "usbshield-lab",
  "linuxUsername": "usbdev",
  "linuxUid": 1000,
  "sessionId": "21",
  "sessionType": "wayland",
  "seat": "seat0",
  "device": {
    "vendorId": "093a",
    "productId": "2510",
    "serial": "",
    "name": "USB Optical Mouse",
    "hash": "L46bZEAKg59EA+puzjHMh8D4jLliJqK4sYxKSd/DRz8=",
    "interface": "03:01:02"
  },
  "eventType": "CONNECTED",
  "decision": "ALLOWED",
  "occurredAt": "2026-09-28T14:10:16+07:00"
}
```

Notes:

- `POC-UNENROLLED-LAB-PC-01` is a **Week 1 placeholder**, not a production endpoint ID.
- The sample contract currently represents the physical USB mouse (`ALLOWED`), not the blocked DataTraveler event.
- Full Agent → Admin Server delivery is outside Week 1 scope.

### Consistency note

The packaged sample JSON has been aligned to the manually captured combined context (`sessionId=21`, timestamp `2026-09-28T14:10:16+07:00`).

However, the Week 1 payload is still a **manual PoC contract**, not an event generated atomically by the Agent.

For Week 2 implementation, `UsbGuardEventListener` and `ActiveUserResolver` must build one `ClientUsbEvent` from the same event-time context.

---

## 13. USBGuard Parser PoC

The Java parser extracts:

- `runtimeId`
- `state`
- `VID`
- `PID`
- `name`
- `serial`
- `hash`
- `massStorage`

Relevant DataTraveler parse result:

```text
--------------------------------
runtimeId   = 14
state       = block
vid         = 0951
pid         = 1665
name        = DataTraveler 2.0
serial      = C81F660E8BE8FFA14601FEF6
hash        = xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=
massStorage = true
raw         = 14: block id 0951:1665 serial "C81F660E8BE8FFA14601FEF6" name "DataTraveler 2.0" hash "xfCC0uOLcks7yZarco6Jd3mAdADNaNbVBilp2NDHNyY=" parent-hash "Lw/Cdah32MiEGYi1D+rX5Vcs8544WKd6bqSOuVKqKn4=" via-port "3-2" with-interface 08:06:50 with-connect-type "unknown"
```

This demonstrates:

```text
USBGuard device line
-> parser
-> VID/PID/device identity
-> current authorization state
-> Mass Storage classification
```

The parser also correctly classifies non-storage controllers/HID devices as:

```text
massStorage = false
```

**Result: PASS**

---

## 14. Problems and Fixes

### Problem 1 — `usbguard --version` did not provide a usable version string

- **Symptom:** the original environment capture contained USBGuard CLI help/usage instead of a version string.
- **Resolution:** query the installed Debian package directly.

Captured result:

```text
usbguard 1.1.2+ds-6build2
```

Command:

```bash
dpkg-query -W -f='${Package} ${Version}
' usbguard
```

**Status: RESOLVED**

### Problem 2 — Multiple USB storage test cannot be executed

- **Symptom:** only one USB Mass Storage device is available.
- **Resolution:** keep the case `NOT EXECUTED`; do not fabricate a PASS.

### Problem 3 — Real multi-session ambiguity is difficult to reproduce

- **Context:** a normal single-seat Ubuntu VM does not easily provide two real, simultaneously active, equally valid graphical sessions.
- **Resolution:** validate the fail-safe resolver branch with a simulated logic test containing two equally valid candidates.
- **Observed behavior:** `UNKNOWN` with `reason=ambiguous-active-local-sessions`.

```text
Result: PASS (SIMULATED LOGIC TEST)
```

This validates the decision logic without claiming a real multi-session integration test.

### Problem 4 — Week 1 Agent event is manually assembled

- **Context:** Week 1 validates the event contract and active-user resolution, not the production Agent pipeline.
- **Resolution for committed PoC:** `sample-usb-event.json` is normalized to the saved `sessionId=21` active-user snapshot.
- **Week 2 target:** build the event atomically through:

```text
UsbGuardEventListener
→ ActiveUserResolver
→ ClientUsbEvent
→ EventSender
```

---

## 15. Conclusion

Week 1 successfully demonstrates the core USBShield assumptions:

```text
Unknown USB Mass Storage
-> class 08 detected
-> BLOCK

Specific permanently whitelisted DataTraveler
-> ALLOW
-> survives reboot

Physical USB HID mouse
-> class 03
-> ALLOW

Active local Ubuntu graphical session
-> resolved through loginctl

Second Ubuntu user
-> resolver returns the new user
-> no hard-coded username

USBGuard device output
-> Java parser
-> massStorage classification works

Endpoint + user + USB + decision
-> sample Agent event contract produced
```

The following case remains explicitly outside the demonstrated PASS set:

```text
Multiple USB Mass Storage:
NOT EXECUTED — only one storage device available.
```

Resolver ambiguity is **PASS (SIMULATED LOGIC TEST)**: two equally valid candidates correctly produced `UNKNOWN`.

USBGuard `watch` event-stream evidence is captured and **PASS**.

### Week 1 status

**PASS — core USBGuard policy, Mass Storage blocking, permanent allow/reboot persistence, HID allow, USBGuard event stream, active-user attribution, second-user resolver test, ambiguity fail-safe logic, parser PoC, and sample Agent event contract were demonstrated.**

The remaining `NOT EXECUTED` multiple-storage case is documented explicitly rather than silently treated as PASS.

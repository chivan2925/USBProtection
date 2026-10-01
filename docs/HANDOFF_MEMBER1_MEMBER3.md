# Member 2 Week 1 — Handoff to Member 1 and Member 3

## Member 1 — Backend/Architecture

Use these artifacts:

```text
packaging/usbguard/rules-v0.1.conf
docs/usbguard/USBGuard_POC.md
docs/usbguard/parser-poc/
docs/usbguard/active-user-poc/
docs/agent-contract/
docs/usbguard/evidence/
```

Contracts to freeze with M2/M3:

```text
UsbGuardGateway
UsbGuardDeviceInfo
UsbGuardRuleInfo
ActiveUserInfo
ClientUsbEvent
```

`ActiveUserInfo` minimum fields:

```text
username
uid
sessionId
sessionType
seat
```

`ClientUsbEvent` minimum fields:

```text
endpointId
hostname
linuxUsername
linuxUid
sessionId
device fingerprint/info
eventType
decision
occurredAt
```

Important semantic rule:

> User attribution means the active local Ubuntu session at USB event time; it does not prove physical human identity.

## Member 3 — Client Agent/System/Packaging

Read/listen inputs used by the Agent:

```text
usbguard list-devices
usbguard list-rules
usbguard watch

loginctl list-sessions
loginctl show-session
```

USBGuard policy path:

```text
/etc/usbguard/rules.conf
```

USBGuard daemon config:

```text
/etc/usbguard/usbguard-daemon.conf
```

Week 2 integration target:

```text
UsbGuardEventListener
→ ActiveUserResolver
→ ClientUsbEvent
→ EventSender
→ Admin Server
```

The resolver behavior and ambiguity test are in:

```text
docs/usbguard/active-user-poc/resolve-active-user.sh
docs/usbguard/active-user-poc/test-ambiguity.sh
```

Important fallback:

```text
no unique active local session
→ UNKNOWN
→ do not guess
```

## Week 1 evidence status

Captured and ready for Member 1 / Member 3:

```text
USBGuard package: 1.1.2+ds-6build2
Mass Storage class 08 detection: PASS
Unknown DataTraveler BLOCK: PASS
Reconnect x3 BLOCK: PASS
Permanent allow: PASS
Reboot persistence: PASS
Physical mouse class 03 ALLOW: PASS
usbguard watch Remove/Insert/PolicyApplied: PASS
Active-user resolver usbdev: PASS
Second-user resolver student01: PASS
Resolver ambiguity fail-safe: PASS (SIMULATED LOGIC TEST)
Java parser: PASS
Sample ClientUsbEvent contract: PASS (PoC)
```

Remaining documented case:

```text
Multiple USB Mass Storage: NOT EXECUTED (only one storage device)
```

Resolver ambiguity:

```text
PASS (SIMULATED LOGIC TEST)
two equally valid candidates
→ UNKNOWN
→ do not guess
```

See:

```text
docs/usbguard/USBGuard_POC.md
docs/usbguard/evidence/TODO_CAPTURE.md
```

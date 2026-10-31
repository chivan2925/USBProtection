# USBShield — M2 Week 4, Part 2: Policy & Active-Session Correctness

**Owner:** M2  
**Work IDs:** `W4-M2-02` (USBGuard policy), `W4-M2-03` (active-user attribution)  
**Base commit:** `ab346931d8d0994b5c4e015feb823b4cf8df5a2f` (`main`, 2026-10-30)  
**Status:** Source/test implementation prepared. Full Maven/JUnit checks on the updated tree **NOT YET VERIFIED**; Ubuntu USBGuard daemon/hardware/session/GUI checks **DEFERRED UNTIL AFTER WEEK 4**.

## Implemented policy hardening

- The strict base non-storage rule is exactly `allow with-interface none-of { 08:*:* }`. An appended `hash`, port, or condition can narrow that Allow and must not falsely satisfy base-policy verification. The daemon default still must be `ImplicitPolicyTarget=block`.
- `UsbGuardRuleBuilder` rejects a `VID:PID` pair masquerading as a standalone hash and rejects identity attributes without alphanumeric content. Valid hash-only USB identities are still accepted when serial is absent; `VID:PID` alone never suffices to whitelist an individual USB.
- Desired whitelist entries sharing a hash but specifying contradictory VID/PID or serial definitions fail *before* USBGuard commands mutate rules. An identical repeated entry remains idempotent.
- Reconciliation compares the **entire M2-managed Allow definition**, not merely its hash key. A changed VID/PID/serial or an unexpected extra constraint forces remove+reappend; a mismatching post-apply rule cannot be reported as successfully applied. After an ambiguous append failure, attempted new grants are cleaned up as a best effort and an exception prevents a successful policy ACK. A cleanup failure is retained as a suppressed exception rather than silently ignored.
- A malformed/unverifiable foreign Allow rule or a numeric rule header that cannot be parsed causes a safe failure without automatically deleting the foreign rule. Device-specific foreign rules that are valid and unrelated are preserved by design; generic unscoped foreign Allows are refused.
- USBGuard policy is **first-match, sequential**. A Deny before the base non-storage rule is refused. For a proposed managed Allow, a potentially overlapping prior `block`/`reject` is refused, including when a stale managed rule would be reappended after a Deny. Unrelated device-specific Deny rules are preserved. These checks are conservative and may require an administrator to resolve ambiguous external rules.

**Important limitation:** Preserving an unrelated, device-specific **foreign Allow** means that a physical device outside USBShield's desired whitelist *might still match that pre-existing foreign rule*. The reconciler does not silently overwrite it, and this package does not claim that an arbitrary external policy is globally fail-closed. Before a real deployment, inspect the effective USBGuard policy and remove unauthorized foreign Allows through an approved administrator procedure.

## Implemented active-user hardening

- A selected local session must have a bounded/safe session ID, a nonblank non-`UNKNOWN` username without control characters, and a decimal UID that fits Java `long`.
- `loginctl` enumeration with malformed/duplicate session IDs, disappearing sessions, errors or an ambiguous local snapshot returns `UNKNOWN`; no cached prior identity is guessed after logout or switch.
- A **unique active local tty session** can still be correctly attributed by username even without GUI. It must **not** be treated as proof that a desktop notification was delivered. Graphical `seat0` takes priority only when it is uniquely selected among multiple active local sessions.

## File changes

| Repository path | Purpose |
|---|---|
| `client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardRuleBuilder.java` | Fingerprint validation, disallow VID:PID as hash-only identity |
| `client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardRuleInfo.java` | Strict canonical base rule |
| `client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardPolicySynchronizer.java` | Desired definition collision, full rule verification, rule ordering, foreign-rule fail-safe, rollback |
| `client-agent/src/main/java/com/group/usbshield/agent/session/ActiveSessionSelector.java` | Identity validation before attributed user selection |
| `client-agent/src/main/java/com/group/usbshield/agent/session/LoginctlActiveUserResolver.java` | Fail closed on malformed enumerated session IDs |
| `client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardWeek4PolicyIntegrityTest.java` | 12 focused policy tests |
| `client-agent/src/test/java/com/group/usbshield/agent/session/Week4ActiveSessionSafetyTest.java` | 8 focused session tests |

## Evidence and verification boundary

The patch was compiled on Java 21 and exercised with a **local lightweight JUnit-API replacement** against 63 isolated policy/session behavioral cases (including selected Week 3 tests): 63 passed, 0 failed. This does **not** count as a Maven/Spring Boot/JUnit test run. The local harness is not a committed production test tool. Actual owner Maven/JUnit and package build results remain `NOT EXECUTED` for the updated patch until captured.

Recommended Windows checks, from repository root after applying the ZIP:

```powershell
mvn -B -f client-agent/pom.xml clean test
mvn -B -f client-agent/pom.xml -DskipTests package
git diff --check
```

M5 should capture the exact commit/diff base, Windows environment, command outputs, test totals and exit status. Do not backfill PASS for Ubuntu hardware or USBGuard runtime.

## Post-Week-4 Ubuntu checks (not executed)

1. Validate the actual policy order and `ImplicitPolicyTarget=block` with USBGuard CLI; document any existing foreign Allows/Deny rules before enforcement tests.
2. Insert unknown Mass Storage (class 08) and HID; verify BLOCK for unknown storage, ALLOW for keyboard/mouse and no regression for composite devices containing class 08.
3. Allow a serialless/hash-identified flash drive only on Endpoint A; verify Endpoint B stays blocked; revoke and confirm blocked again.
4. Switch active local graphical users, logout, test remote session and tty-only state; correlate the attribution in real USBGuard/Agent event and Admin event history.
5. Repeat after policy reload/reboot and while Admin Server is offline; verify local enforcement. Record actual daemon/IPC outputs and sanitized evidence.

## Cross-owner handoff

- **M3:** No changes to Agent transport/ACK protocol; a reconciliation exception continues to signal non-applied policy. Review deployed USBGuard external rules before packaging acceptance.
- **M5:** Do not mark physical USB or native session tests PASS from Windows mocks.
- **M1:** The REST/DTO contract is unchanged.

**Official USBGuard rule evaluation reference:** https://usbguard.github.io/documentation/rule-language

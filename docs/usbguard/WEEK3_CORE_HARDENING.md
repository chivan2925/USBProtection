# USBShield — Member 2, Week 3: Core Hardening

**Repository:** `chivan2925/USBProtection`  
**Base `main`:** `fc7aea53398c085b671efca80693da0adeb74f40` (23/10/2026)  
**Owner:** M2 — `usbguard/`, `session/`, `notification/` and associated tests  
**Acceptance scope:** Windows Maven tests; Ubuntu daemon/hardware/GUI/systemd verification postponed until **after Week 4**.

## 1. Three sequential parts

| Part | Work | Source area | Owner gate |
|---|---|---|---|
| **1 — Parser + serialless identity** | Harden USBGuard device/watch parsing, malformed IDs/sections, class-08/composite detection, and strict fingerprint parsing. Hash-only is valid; VID:PID without hash/serial is not an individual USB identity. | `usbguard/UsbGuardEventParser.java`, `UsbGuardPresenceParser.java`, `UsbGuardRuleBuilder.java`, new regression tests | **This package**; owner tests, commits, and pushes first |
| **2 — Policy/gateway + reconnect** | Review subprocess timeout/exit handling, daemon-unavailable and restart/reconcile, base/foreign-rule preservation, idempotent allow/revoke, fail-closed and no false ACK contract. Preserve existing `UsbGuardGateway` API for M3. | `usbguard/UsbGuardCommandRunner.java`, `UsbGuardCliGateway.java`, `AdaptiveUsbGuardGateway.java`, `UsbGuardPolicySynchronizer.java`, tests | Start only after Part 1 has been pushed; rebase on new `main` |
| **3 — Sessions + notifications + handoff** | Strengthen `loginctl` active-local session handling, switch/logout/remote/no GUI/ambiguity; review listener dedup and notifier fail-independence; complete M3 IPC/installer contract notes. | `session/`, `usbguard/UsbGuardEventListener.java`, `notification/` where necessary, `packaging/usbguard/`, unit tests, M2 documentation | Start only after Part 2 has been pushed; rebase on new `main` |

No Java files owned by M3 (`sync/`, `endpoint/`, `outbox/`, `packaging/systemd/`, `packaging/desktop/`) are changed in Part 1.

## 2. Part 1 — changes

| File | Status | Changes |
|---|---|---|
| `client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardEventParser.java` | Modified | Parse numbered and watch device records safely; ignore unrecognized lines and overflow runtime IDs; recognize composite class 08 while not interpreting `with-interface` inside quoted device names; don't mistake the base `none-of` rule for a physical storage interface. |
| `client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardPresenceParser.java` | Modified | Reject malformed/overflow IDs, invalid targets, duplicate fields and mixed watch sections rather than emitting an invented event. |
| `client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardRuleBuilder.java` | Modified | Validate the **entire** legacy whitelist expression; reject unknown/duplicate attributes, injected suffixes and unsafe values; retain hash-only and VID:PID+serial identities, including VID:PID+hash without serial. |
| `client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardWeek3ParsingAndIdentityTest.java` | New | Nine regression cases: missing serial, hash-only, composite storage, malformed identity, duplicate attributes, overflow, invalid target and watch sections. |
| `docs/usbguard/WEEK3_CORE_HARDENING.md` | New | Part plan, scope, checks, and M3 handoff notes. |

**Explicit non-goals:** no new REST fields/routes, no changes to `UsbGuardGateway`, no broadened allow rules, no unauthorized filesystem writes, no fake USB events, no Ubuntu PASS claims.

## 3. Owner verification on Windows 11 (PowerShell, repository root)

Ensure working tree is clean and `main` matches or follows the stated base without conflicting changes. Extract the delta ZIP at the repository root before running:

```powershell
java -version
mvn -B -f client-agent/pom.xml clean test
mvn -B -f client-agent/pom.xml -DskipTests package
git status --short
```

Expected: Maven test run ends with `BUILD SUCCESS`, zero failures/errors; test count is expected to increase from the prior 61 because nine new test methods were added. **Do not commit if any test fails.** Windows tests exercise mock/fixture behavior, not a physical USB device.

Verification performed while preparing this package: Java 21 compiled the affected standalone core classes; a temporary local JUnit-compatible harness ran **20/20 selected assertions PASS** (9 new + 11 existing parser/policy assertions). This does **not** constitute a full project Maven/JUnit or Ubuntu E2E run. Temporary harness/stubs are not included in the ZIP.

## 4. M2 → M3 and M5 contracts

- An individual USB fingerprint must include a stable USBGuard hash or the combination of VID:PID and non-empty serial; VID:PID alone and device name alone are insufficient.
- No-serial USB can be specifically allowed by **hash**, without falling back to a model-wide VID:PID allow.
- Invalid rule text must fail before `UsbGuardPolicySynchronizer` changes local rules. M3 must never ACK if local apply failed; this contract is unchanged.
- Malformed watch sections are dropped; a valid `PresenceChanged / Insert or Remove` remains the only event source. `PolicyChanged` and `PolicyApplied` never masquerade as physical insertion/removal.
- USBGuard service, actual persistence after reboot, user switching, XDG notifications and all hardware/systemd tests remain **DEFERRED UNTIL AFTER WEEK 4**. M5 must keep the execution status `NOT EXECUTED` until real evidence is collected.

## 5. Owner handoff / commit

**Suggested commit:**

```text
feat(usbguard): harden Week 3 parsing and serialless identity
```

After the Windows Maven tests succeed, commit/push **Part 1 only**, then use the new `main` commit as the baseline for **Part 2**. Do not reapply this package after other owners have edited the same files; review the diff first.

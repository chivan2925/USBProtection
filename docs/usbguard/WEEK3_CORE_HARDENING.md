# USBShield — Member 2, Week 3: Core Hardening

**Repository:** `chivan2925/USBProtection`  
**Original Part 1 base `main`:** `fc7aea53398c085b671efca80693da0adeb74f40` (23/10/2026)  
**Part 2 base `vinhkhang`:** `5653d9eb4be45bd24c8fed0667da1286de32f3cd` (23/10/2026)  
**Owner:** M2 — `usbguard/`, `session/`, `notification/` and associated tests  
**Acceptance scope:** Windows Maven tests; Ubuntu daemon/hardware/GUI/systemd verification postponed until **after Week 4**.

## 1. Three sequential parts

| Part | Work | Source area | Owner gate |
|---|---|---|---|
| **1 — Parser + serialless identity** | Harden USBGuard device/watch parsing, malformed IDs/sections, class-08/composite detection, and strict fingerprint parsing. Hash-only is valid; VID:PID without hash/serial is not an individual USB identity. | `usbguard/UsbGuardEventParser.java`, `UsbGuardPresenceParser.java`, `UsbGuardRuleBuilder.java`, new regression tests | **This package**; owner tests, commits, and pushes first |
| **2 — Policy/gateway + reconnect** | Harden subprocess input/output, exact fail-closed policy parameter, daemon restart reconciliation, base/foreign-rule preservation, safe revoke-before-grant, no false ACK, and best-effort rollback. Preserve existing `UsbGuardGateway` API for M3. | `usbguard/UsbGuardCommandRunner.java`, `UsbGuardCliGateway.java`, `UsbGuardPolicySynchronizer.java`, tests | **Part 2 package based on `vinhkhang` at `5653d9e`** |
| **3 — Sessions + notifications + handoff** | Strengthen `loginctl` session selection, switch/logout/remote/no GUI/ambiguity; isolate watch reconnect and notification failure; complete M3 handoff. | `session/`, `usbguard/UsbGuardEventListener.java`, `notification/`, unit tests, M2 docs | **Part 3 package based on `vinhkhang` at `9e4cd23`** |

No Java files owned by M3 (`sync/`, `endpoint/`, `outbox/`, `packaging/systemd/`, `packaging/desktop/`) are changed in Parts 1–2.

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

## 6. Part 2 — Gateway, policy reconciliation and daemon recovery

**Baseline:** `vinhkhang` at `5653d9eb4be45bd24c8fed0667da1286de32f3cd` (Part 1 already pushed). This is a **source change prepared for owner verification**, not an Ubuntu runtime PASS claim.

| File | Changes |
|---|---|
| `usbguard/UsbGuardCommandRunner.java` | Validate CLI arguments before spawning; retain argv-only `ProcessBuilder`, no shell/sudo; keep deadline/exit handling; bound combined output to 1 MiB and fail if excessive. |
| `usbguard/UsbGuardCliGateway.java` | Do not equate `unblock` or other text containing `block` with safe default; require exact `block`; retain M3's boolean `applyWhitelist(List<String>)` and exceptional failure behavior. |
| `usbguard/UsbGuardPolicySynchronizer.java` | Serialize local reconciliations; validate all desired identities before mutation; require exact implicit block + non-storage base rule; abort on unparseable/duplicate managed rules and conflicting foreign allow; revoke stale entries **before** granting new entries; re-read and verify the exact managed key set and default block before returning; try removing newly added rules if the apply/verification fails. |
| `usbguard/UsbGuardWeek3PolicyAndGatewayTest.java` | Eleven JUnit tests for daemon restart/reapply, idempotency, base/foreign policy preservation, fail-closed parameter validation, invalid/duplicate rules, remove/apply/list failures, revoke-before-grant, ambiguous append response and argument validation. |

**Conservative behavior and limitations:** If USBGuard is unreachable, reports an unsafe implicit target, changes its policy unexpectedly, or rejects a mutation, reconciliation throws; M3 must **not ACK**. A partial revoke can leave a previously allowed device blocked until retry, which is safer than granting an unverified device. Rollback is best-effort and itself may fail during an IPC outage: the exception remains visible, not a successful apply. The existing baseline rule `allow with-interface none-of { 08:*:* }` must remain. Foreign rules are never rewritten or deleted.

**Persistence contract for M3:** USBGuard's `append-rule` creates permanent rules by default (unless `--temporary` is specified in versions supporting it); `remove-rule` removes a rule from the policy. M3 is responsible for setting `RuleFile`, writable policy storage for the daemon, correct IPC authorization (`Policy=list,modify` and `Parameters=list`), daemon restarts and packaging. No direct edits of `/etc/usbguard/rules.conf` and no privileged `sudo` are introduced in Java. Verify persistence on real Ubuntu after Week 4.

### Part 2 test and commit commands (PowerShell, repository root)

Start from the **`vinhkhang` branch**, not `main`; verify the base before extracting this delta ZIP:

```powershell
git switch vinhkhang
git fetch origin
git status --short
git rev-parse HEAD
```

Expected starting HEAD: `5653d9eb4be45bd24c8fed0667da1286de32f3cd`. The working tree should be clean. Extract Part 2 ZIP over repository root, then run:

```powershell
mvn -B -f client-agent/pom.xml clean test
mvn -B -f client-agent/pom.xml -DskipTests package
git diff --check
git status --short
```

After successful verification, commit only Part 2:

```powershell
git add client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardCommandRunner.java
git add client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardCliGateway.java
git add client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardPolicySynchronizer.java
git add client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardWeek3PolicyAndGatewayTest.java
git add docs/usbguard/WEEK3_CORE_HARDENING.md
git commit -m "feat(usbguard): harden Week 3 policy reconciliation and recovery"
git push origin vinhkhang
```

### Verification performed while preparing Part 2

- Java 21 `javac` compiled the changed gateway/synchronizer/command runner, Part 1 parser dependencies and new test class.
- Temporary, self-contained JUnit-compatible smoke harness executed **16/16 selected methods PASS** (11 new Part 2 and 5 existing policy tests) on simulated USBGuard CLI responses.
- **Not run here:** complete Maven/Spring/JUnit suite, actual daemon IPC, Linux reconnect, reboot persistence, and hardware USB tests. The owner must run the Maven commands above on Windows; Ubuntu tests remain **DEFERRED UNTIL AFTER WEEK 4**.
- No `//` comments were added to the Java files in Part 2.

### Part 3 handoff boundary

Part 3 will cover `session/` user-switch/ambiguity, `UsbGuardEventListener` watch reconnect/dedup and `notification/` fail-independent behavior. Begin only after Part 2 is committed and pushed to `vinhkhang`; use its **new HEAD** as the next baseline.

## 7. Part 3 — Active-session attribution, watch recovery, notification

**Baseline:** branch `vinhkhang` at `9e4cd2317fd2f1714d3a3ab73c2991db0d61edff` (Parts 1 and 2 already pushed). Part 3 completes M2's **Windows verification scope** pending owner Maven tests. Do not report Ubuntu/hardware PASS.

- `ActiveSessionSelector` provides one shared selection algorithm for `LoginctlActiveUserResolver` and `LinuxActiveUserResolver` compatibility candidates. It preserves unique local active attribution, prefers a unique graphical `seat0` when multiple sessions exist, rejects ambiguity, duplicate session IDs, invalid UID and malformed identity, and does not keep an old user after logout or switch.
- `UsbGuardEventListener` discards incomplete watch sections after reconnect, caps section memory, never emits telemetry from `PolicyChanged` or `PolicyApplied`, and distinguishes an immediate Insert–Remove–Insert sequence from duplicate watch entries. On `UNKNOWN` it still sends telemetry but does not initiate popup delivery. Notification failure does not block event sending.
- `BlockNotificationPolicy` and `DesktopNotificationService` reset per-device cooldown upon Remove, including removals without a resolvable user. Valid graphical sessions are routed by their actual UID; a unique resolved non-`seat0` session is not arbitrarily rejected.
- New Windows JUnit tests: `Week3ActiveSessionTransitionTest` (9 tests), `Week3NotificationPolicyTest` (6 tests), `UsbGuardWeek3WatchRecoveryTest` (7 tests).
- Detailed M3 contract and deferred Ubuntu scenarios: [`WEEK3_PART3_SESSION_NOTIFICATION_HANDOFF.md`](WEEK3_PART3_SESSION_NOTIFICATION_HANDOFF.md).

### Owner gate for Part 3

```powershell
git switch vinhkhang
git rev-parse HEAD
mvn -B -f client-agent/pom.xml clean test
mvn -B -f client-agent/pom.xml -DskipTests package
git diff --check
git status --short
```

Commit only Part 3 files after `BUILD SUCCESS`:

```text
feat(usbguard): harden active sessions and watch notification recovery
```

**Local preparation checks:** Java 21 selected-core compilation PASS; 15/15 self-contained session/notification assertions PASS and 6/6 watch-loop smoke assertions PASS with temporary dependencies. Complete Maven/JUnit was not executed here; the owner must run it on Windows. These temporary harness files are not included in the ZIP.

**Ubuntu acceptance:** Actual `loginctl`, user switching, physical USB/USBGuard watcher, `systemd`, `.deb`, GUI notification and reboot remain **DEFERRED UNTIL AFTER WEEK 4**. The group QA owner must not label these cases PASS based on mock tests.

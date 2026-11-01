# USBShield — M2 Week 4, Part 3: Notification / Documentation / Handoff + PR #40 Remediation

**Owner:** M2 · **Work IDs:** `W4-M2-04`, `W4-M2-05`, `W4-M2-06`, `W4-M2-07 (P1)`  
**Base:** `main` `176e547f5bf093ef8c9aebdfabd29cab7177d73e` (2026-11-01)  
**Patch status:** IMPLEMENTED; **Windows Maven test + package on owner's working tree are still pending**.  
**Ubuntu daemon/physical USB/native notification/systemd/reboot/`.deb`:** DEFERRED until post-Week-4 per team schedule.

## 1. Copilot PR #40 findings addressed in code

| PR #40 discussion | Severity | Defect before fix | Corrective implementation | Evidence still needed |
|---|---|---|---|---|
| [#4431653759](https://github.com/chivan2925/USBProtection/pull/40#discussion_r4431653759) | HIGH | Removing multiple managed rules based on stale numeric rule IDs can delete the wrong rule after renumbering | Re-read and re-resolve a managed rule by stable identity before **every removal**, including rollback; preserve base/foreign rules | `UsbGuardWeek4CopilotReviewRegressionTest`: two stale grants, mixed replacement, multi-grant rollback; owner Maven and native policy test |
| [#4431653772](https://github.com/chivan2925/USBProtection/pull/40#discussion_r4431653772) | HIGH | A foreign `allow` line with an unknown trailing token could be parsed as valid | Added `UsbGuardRuleSyntax`: validate *complete* numeric rule definitions before all reconciliation mutations, refuse unrecognized/unsupported attributes and conditions | Regression for unknown suffix, known foreign attributes, unsupported condition; owner Maven and Ubuntu rules inspection |
| [#4431653784](https://github.com/chivan2925/USBProtection/pull/40#discussion_r4431653784) | MEDIUM | Header counter skipped `2: permit ...`, letting an unknown target disappear | Validate and count **all** numeric rule headers independent of target; throw on unknown target or syntax | Regression for `permit` and `deny` headers; owner Maven |

All three are **code-fix prepared / review pending**, not independently closed or confirmed on real USBGuard yet.

## 2. Notification correctness

- `BlockNotificationPolicy`: bounded 1024-key cooldown state, safe handling of wall-clock rollback, removal/switch logic, preserved `Insert + class 08 + block/reject` filter and normal desktop UID >=1000.
- `UsbGuardEventListener`: pass the **effective PresenceChanged target** to the desktop notifier by constructing a notification-only immutable `UsbDeviceInfo` copy if `device_rule` state disagrees; original event transport still uses the real target. Existing two-argument notifier contract remains intact.
- `DesktopNotificationService`: report failure and queue overflow without throwing into the USB event transport thread; success only indicates handoff to helper, not a rendered popup.
- `DesktopNotificationClient`: verify Linux peer credentials (`SO_PEERCRED`) and require Unix peer username `usbshield` before accepting target UID/title/body. Full interprocess trust and socket ACL require M3 review and native Ubuntu validation.
- Regression: `Week4NotificationCorrectnessTest`, `Week4DesktopNotifierPeerSafetyTest`, `UsbGuardWeek4NotificationEventTargetTest`.

## 3. Technical documentation and handoffs

- `WEEK4_M2_CORE_OPERATIONS_GUIDE.md`: class-08/base rule and first-match ordering, hash/serialless, session attribution, watch recovery, notifier/IPC and inspection commands.
- `WEEK4_M2_TECHNICAL_QA.md`: technical answers and demo narrative, including root limitations and inability to prove the physical person who inserted the USB.
- `WEEK4_M2_M3_M5_UBUNTU_HANDOFF.md`: specific post-Week-4 native test cases and **M3 socket-ACL follow-up**.
- `WEEK4_M2_THREE_PART_PLAN.md`: Part 1/2 history, Part 3 scope/status and Windows versus Ubuntu gate separation.

## 4. Validation provenance — do not overstate

During patch preparation, Java 21 compiled focused production policy, session and notification classes using local interface/framework stubs and ran **43 selected isolated checks (43 passed, 0 failed)**. It also compiled the listener with stubbed transport/framework dependencies and passed a separate end-to-end-in-process smoke of `PresenceChanged target=block` with stale `device_rule=allow`, notification target and outgoing BLOCKED event. This is **NOT a genuine Maven/Spring/JUnit run**; the local stubs and harness are **not shipped** in this patch. Owner must run the repository Maven tests and packaging commands after applying the patch and record actual totals/exit codes in the QA record. The new Mockito-based event-target JUnit test is unexecuted in the local isolated harness.

The parser intentionally accepts only a **verified subset** of USBGuard rules. Real policies using unsupported `if` conditions or future attributes will produce a reconciliation failure requiring manual review; refusing to mutate is safer than accepting an unverifiable Allow. This is *not* a claim of complete USBGuard upstream grammar support.

## 5. Release/acceptance boundary

- `W4-M2-04`: implementation + regression prepared; Windows Maven signoff and native GUI pending.
- `W4-M2-05`: core documentation prepared.
- `W4-M2-06`: Q&A and M3/M5 handoff prepared.
- `W4-M2-07`: M2 review recorded, **M3 permission/ACL design and runtime signoff OPEN (P1)**. In current installer source `/run/usbshield-notifier` is `1777`, user socket is `0660`, system Agent runs as `usbshield`; GUI user's default group may not give Agent access.
- **No FULL RELEASE PASS** is claimed. Desired state remains `WEEK 4 WINDOWS/RELEASE-CANDIDATE PREPARATION; UBUNTU E2E DEFERRED` until all relevant gates are completed.

## 6. Changed-file inventory

| Path | Purpose |
|---|---|
| `client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardRuleSyntax.java` | New full-line numeric-rule syntax verifier (conservative) |
| `client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardPolicySynchronizer.java` | Live rule-ID resolution on removal/rollback and syntax validation |
| `client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardEventListener.java` | Notification uses effective watch target |
| `client-agent/src/main/java/com/group/usbshield/agent/notification/BlockNotificationPolicy.java` | Bounded cooldown and clock rollback safety |
| `client-agent/src/main/java/com/group/usbshield/agent/notification/DesktopNotificationService.java` | Best-effort failure observability/backpressure |
| `client-agent/src/main/java/com/group/usbshield/agent/notification/DesktopNotificationClient.java` | Peer authentication with `SO_PEERCRED` |
| `client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardWeek4CopilotReviewRegressionTest.java` | PR #40 regression for policy rules |
| `client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardWeek4NotificationEventTargetTest.java` | Watch effective target + event transport |
| `client-agent/src/test/java/com/group/usbshield/agent/notification/Week4NotificationCorrectnessTest.java` | Classification/dedup/publisher failure cases |
| `client-agent/src/test/java/com/group/usbshield/agent/notification/Week4DesktopNotifierPeerSafetyTest.java` | Required agent peer identity |
| `docs/usbguard/WEEK4_M2_CORE_OPERATIONS_GUIDE.md` | M2 technical guide |
| `docs/usbguard/WEEK4_M2_TECHNICAL_QA.md` | Demo Q&A |
| `docs/usbguard/WEEK4_M2_M3_M5_UBUNTU_HANDOFF.md` | Test checklist and M3 ACL caveat |
| `docs/usbguard/WEEK4_M2_PART3_FINAL_CLOSEOUT.md` | This report |
| `docs/usbguard/WEEK4_M2_THREE_PART_PLAN.md` | Updated part statuses |
| `docs/usbguard/WEEK4_PART2_POLICY_SESSION.md` | Follow-up notes for PR #40 review findings |

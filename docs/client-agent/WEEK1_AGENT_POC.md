# USBShield Client Agent — Week 1 Proof of Concept (PoC) Report

**Module:** `client-agent`  
**Artifact:** `com.group.usbshield:client-agent` (Spring Boot 3.3.4, Java 21)  
**Role:** Member 3 (Client Agent & System Integration Lead)  
**Branch:** `hbao`  
**Date:** 2026-10-12  

---

## 1. Executive Summary

During Week 1, Member 3 designed, implemented, and verified the core foundation of the **USBShield Client Agent**, responsible for device-level enforcement coordination, event telemetry reporting, offline spooling resilience, and centralized policy synchronization on Ubuntu client machines.

Week 1 validated the Agent shell and HTTP transport against a running Admin Server on Windows using **simulated USB events and a mock Linux user**. The Java classes initially created by M3 under `client-agent/.../usbguard/` and `client-agent/.../session/` are **transferred intact to M2 for Week 2**; M2 will review and modify the existing classes in place. This is an ownership handoff, not a second implementation. Client desktop notifications are a Week 2 requirement, not a retroactive Week 1 acceptance criterion.

---

## 2. Core Week 1 Deliverables & Architecture

```
+----------------------------------------------------------------------------+
|                            USBShield Client Agent                          |
|                                                                            |
|  +--------------------+     +-------------------+    +------------------+  |
|  |  EndpointManager   |     |   PolicySync      |    | OutboxSpool      |  |
|  |  - machine-id HWID |     |   Service         |    | Service (FIFO)   |  |
|  |  - credential store|     |  - apply rules    |    | - local disk dir |  |
|  +---------+----------+     |  - ACK version    |    +--------+---------+  |
|            |                +---------+---------+             |            |
|            +--------------------------+-----------------------+            |
|                                       |                                    |
|                         +-------------v-------------+                      |
|                         |    AdminServerClient      |                      |
|                         |    (HTTP REST Client)     |                      |
|                         +-------------+-------------+                      |
+---------------------------------------|------------------------------------+
                                        | (HTTP /api/agent/*)
                                        v
                          +---------------------------+
                          |   USBShield Admin Server  |
                          |   (Spring Boot / DB)      |
                          +---------------------------+
```

### 2.1. Endpoint Management & Dynamic Enrollment
- **Hardware ID & Hostname Extraction (`EndpointManager`)**:
  - Reads `/etc/machine-id` on Linux systems (guaranteeing unique endpoint identity across OS reboots); gracefully falls back to deterministic machine attributes on Windows development environments.
  - Captures local network IP and hostname for asset tracking.
- **Dynamic Enrollment Flow**:
  - On first boot, the agent detects the absence of `endpoint.json`.
  - Sends a registration request to `POST /api/agent/enroll` with shared authorization header.
  - Receives and persists an `endpointId` (e.g., `EP-BA26FD9F`) and unique `agentToken` to `endpoint.json` with restricted filesystem permissions (`0600`).

### 2.2. Scheduled Heartbeat & Policy Synchronization
- **Heartbeat Coordinator (`HeartbeatCoordinator`)**:
  - Emits periodic pulses (`@Scheduled`, 5-second interval) to `POST /api/agent/heartbeat`.
  - Transmits local policy version and machine status.
  - Admin Server updates endpoint `last_seen` timestamp and responds with the current server-side policy version.
- **Policy Synchronization (`PolicySyncService`)**:
  - Detects when `serverPolicyVersion > localPolicyVersion`.
  - Downloads latest device rules from `GET /api/agent/policy` with endpoint identity supplied in request headers (the current `AdminServerClient` implementation).
  - Applies whitelist via `UsbGuardGateway.applyWhitelist(rules)`.
  - **Error Safety Guarantee (Lead Feedback #5 Fixed)**: If applying rules to the local filesystem or reloading USBGuard fails, policy version update is **aborted**, skipping the ACK step to prevent state divergence between Admin Server and endpoint.

### 2.3. Resilient Event Ingestion & Offline Spooling
- **Immediate Delivery**: Real-time USB connection events are sent directly via `POST /api/agent/events`.
- **Offline Outbox Spooling (`OutboxSpoolService`)**:
  - If the Admin Server is unreachable (network partition, server maintenance), events are serialized as atomic JSON records into `./data/spool/` (or `/var/lib/usbshield/spool/`).
  - During subsequent successful heartbeat cycles (server back ONLINE), the agent automatically drains the spool in chronological FIFO order and removes processed files upon successful server ACK.

### 2.4. Desktop Notification Prototype — **Week 2, not Week 1 Gate**
- A preliminary Java `DesktopNotificationService` and packaging/sudoers setup already exist in the repository, but **successful popup delivery on a real Ubuntu graphical session is not verified by the Week 1 evidence**.
- Per the owner-approved handoff, **M2 owns Java notification/event classification and the existing `notification/`, `usbguard/`, and `session/` code from Week 2**. M3 owns systemd, `.deb`, user-session startup and least-privilege IPC integration, and must coordinate changes through the agreed interfaces instead of editing M2-owned Java packages.
- The current `notify-send`/`sudo` and `bash -c` prototype is **not a finalized privilege/IPC design**. Security and graphical-session delivery validation remain Week 2 tasks. Notification failures must not affect USBGuard enforcement or event delivery.

### 2.5. Debian Packaging Skeleton & Systemd Daemon
- Location: `packaging/agent-deb/`
- Included assets:
  - Systemd service unit: `systemd/usbshield-agent.service` (`Restart=always`, `User=usbshield`).
  - Package metadata: `DEBIAN/control` (dependencies: `default-jre-headless | openjdk-21-jre-headless`, `usbguard`, `libnotify-bin`).
  - Pre/post lifecycle scripts: `postinst` (creates system user `usbshield`, adds to `usbguard` group, sets directory permissions `0750`, registers sudoers rule), `prerm` (stops and disables service).

---

## 3. Week 2 Ownership Handoff to Member 2 (Lead Feedback #3)

To unblock the Week 1 Agent/Server HTTP PoC, M3 had already committed preliminary **Java** classes in the packages below. **The approved decision is to keep those files as they are and transfer ownership to M2 at the start of Week 2. M3 must not continue modifying them.**

| Location / responsibility | Owner from Week 2 | Required action |
|---|---|---|
| `client-agent/src/main/java/com/group/usbshield/agent/usbguard/` and corresponding tests | **M2** | Review/refactor the *existing* gateway, parser, event listener and USBGuard policy adapters; do not create parallel/duplicate implementations. |
| `client-agent/src/main/java/com/group/usbshield/agent/session/` and corresponding tests | **M2** | Review/refactor existing `ActiveUserResolver`, `LinuxActiveUserResolver`, session models and ambiguity handling in place. |
| `client-agent/src/main/java/com/group/usbshield/agent/notification/` and corresponding tests | **M2** | Take over preliminary Java notifier; own BLOCKED Mass Storage classification, deduplication and user-session notification delivery logic. This feature starts Week 2. |
| `client-agent/.../endpoint/`, `event/`, `outbox/`, `sync/`, `config/`, `system/` | **M3** | Continue enrollment, heartbeat, HTTP event transport, outbox, desired-policy fetch/version/ACK coordination and Agent lifecycle. |
| `packaging/agent-deb/`, `packaging/systemd/`, desktop helper startup/permissions/IPC provisioning | **M3** | Coordinate deployment with M2 via interfaces, without editing M2-owned Java packages or PoC files. |
| `docs/usbguard/`, `packaging/usbguard/`, `docs/agent-contract/sample-usb-event.json` | **M2** | Preserve original Week 1 USBGuard PoC, base policy and sample contract ownership. |

- Previously authored Java files are **handoff material**, not an M3-maintained reference implementation after Week 1. There is **no separate C++/CLI production parser** in this Java 21/Spring Boot design.
- `docs/HANDOFF_MEMBER1_MEMBER3.md` records the original Week 1 technical interface needs; it does **not** supersede the subsequently agreed Week 2 ownership transfer.
- Cross-package integration changes must be coordinated: **M2 edits M2-owned Java code; M3 edits M3-owned code.** Future changes to shared interfaces require agreement before implementation.

---

## 4. Key Fixes Applied (Lead Feedback Items #4 & #5)

| Feedback Item | Problem Identified | Resolution Applied | Verification |
|---|---|---|---|
| **Event Deduplication (#4)** | `usbguard watch` can output 3 sections per insert (`PresenceChanged`, `PolicyChanged`, `PolicyApplied`), previously risking duplicate POSTs. | Added `WatchSection` handling in `UsbGuardEventListener` and a 1500 ms recent-event cache. **M2 now owns this class.** | `UsbGuardEventListenerTest` contains a simulated multi-section watch-stream assertion. Physical USB/Ubuntu rerun is **not** established by Week 1 evidence. |
| **Policy Sync Error Propagation (#5)** | `AdaptiveUsbGuardGateway` previously swallowed filesystem/reload exceptions, causing possible false ACK. | `UsbGuardGateway.applyWhitelist(...)` now returns `boolean`; `PolicySyncService` skips local-version update and ACK if apply fails. **M2 owns the gateway; M3 owns sync orchestration.** | `PolicySyncServiceTest` contains mocked success/failure checks. ACK failure handling after a successful local apply remains a Week 2 item. |

---

## 5. End-to-End Verification & Evidence

### 5.1. Agent Unit Test Inventory and Execution Evidence

**Source inventory verified against the attached repository package:** There are **15 methods annotated with `@Test` in 6 actual test classes** under `client-agent/src/test/java/`:

| Existing class | `@Test` methods | Coverage present in source (not equivalent to runtime proof) |
|---|---:|---|
| `ClientAgentTest` | 1 | Spring context / bean initialization |
| `LinuxActiveUserResolverTest` | 4 | Single user, no user, graphical preference, ambiguous candidates |
| `UsbGuardEventParserTest` | 3 | HID mouse, blocked Mass Storage, watch-device rule parsing |
| `UsbGuardEventListenerTest` | 2 | Payload dispatch, multi-section watch-stream deduplication |
| `PolicySyncServiceTest` | 2 | Apply success → version/ACK; apply failure → no version/ACK |
| `DesktopNotificationServiceTest` | 3 | Notification invocation, UNKNOWN user and disabled notification paths (mocked / simulated) |
| **Total** | **15** | **6 files** |

**Correction:** `EndpointManagerTest` and `OutboxSpoolServiceTest` were mentioned in the earlier report but **do not exist** in the supplied repository. Dedicated unit-test coverage for these two components is **not demonstrated** by this 15-test inventory; their real-JAR flows are only partially covered by the Windows HTTP PoC below.

**Execution status:** M3 previously reported `mvn test` → **15 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**. However, the supplied package contains **no** `client-agent/target/surefire-reports/` or committed Agent `mvn test` output to independently corroborate that run. `docs/member1/evidence/agent-build.log` reports **`Tests are skipped.`** and is *not* unit-test evidence. The 4-test server summary in `docs/member1/evidence/test-summary.txt` belongs to **Admin Server**, not Agent. Accordingly, record the 15/15 result as **M3-reported, not independently evidenced**. This documentation-only fix does not claim a new test execution.

To capture reproducible Agent test evidence on a machine with JDK 21 and Maven installed, run from the repository root (PowerShell):

```powershell
mvn -B -f client-agent/pom.xml test 2>&1 | Tee-Object -FilePath docs/client-agent/evidence/02-agent-unit-tests.log
if ($LASTEXITCODE -ne 0) { throw 'Client Agent unit tests failed' }
Get-ChildItem client-agent/target/surefire-reports/TEST-*.xml | Select-Object -ExpandProperty Name
```

Commit the command output as evidence **only after the command succeeds**, review logs for secrets/local identifiers, and keep generated `target/` artifacts out of the update package.

### 5.2. Live Agent ↔ Admin Server HTTP PoC (Lead Feedback #2)

Both Spring Boot JARs were running and exchanged **real HTTP requests on Windows 11**. The USB insertion and active Linux user were **simulated**; this is not a physical USB/Ubuntu integration test.

- **Existing evidence:** `docs/client-agent/evidence/01-agent-server-e2e-live-run.txt` (M3-captured application logs).
- **Observed in the captured logs:**
  1. **Dynamic enrollment:** Admin Server assigned endpoint `EP-BA26FD9F`.
  2. **Heartbeat:** Server received periodic heartbeats approximately every 5 seconds.
  3. **Simulated event transport:** `EventSimulationRunner` generated a mock `CONNECTED / BLOCKED` Kingston DataTraveler 3.0 event for mock user `student01`; the Agent sent it over HTTP and the server logged persistence as event ID `1`.
- **Not verified by this PoC:** USBGuard enforcement on Ubuntu, correct active Linux user attribution at physical insertion, local policy sync, desktop popup delivery, and production Agent authentication.

### 5.3. Week 2 Follow-up (not a Week 1 reopening)

- **M2:** Validate/refactor the handed-off `usbguard/` and `session/` implementation with real Ubuntu `usbguard watch` and `loginctl` traces. Check event classification/dedup against actual stream sections and device identity. Take ownership of Java notification code; remove unsafe shell-command construction and deliver notification only to the correct active graphical session.
- **M3:** Harden policy-sync lifecycle (including failed ACK/retry without misreporting local vs server-applied versions) and implement/configure least-privilege IPC, user-session helper startup and Debian packaging. Coordinate with M2 instead of editing M2-owned packages.
- **M1/M3:** Verify authenticated enrollment/event/heartbeat on the final Ubuntu deployment and real Admin Server contract.

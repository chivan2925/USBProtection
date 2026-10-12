# USBShield Client Agent — Week 1 Proof of Concept (PoC) Report

**Module:** `client-agent`  
**Artifact:** `com.group.usbshield:client-agent` (Spring Boot 3.3.4, Java 21)  
**Role:** Member 3 (Client Agent & System Integration Lead)  
**Branch:** `hbao`  
**Date:** 2026-10-12  

---

## 1. Executive Summary

During Week 1, Member 3 designed, implemented, and verified the core foundation of the **USBShield Client Agent**, responsible for device-level enforcement coordination, event telemetry reporting, offline spooling resilience, and centralized policy synchronization on Ubuntu client machines.

In addition to meeting all Week 1 target deliverables, foundational adapter interfaces for Week 2 integration with Member 2 (USBGuard low-level parser and loginctl user resolver) and Member 1 (Admin Server API) were prepared and validated via end-to-end integration runs.

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
  - Downloads latest device rules from `GET /api/agent/policy/{endpointId}`.
  - Applies whitelist via `UsbGuardGateway.applyWhitelist(rules)`.
  - **Error Safety Guarantee (Lead Feedback #5 Fixed)**: If applying rules to the local filesystem or reloading USBGuard fails, policy version update is **aborted**, skipping the ACK step to prevent state divergence between Admin Server and endpoint.

### 2.3. Resilient Event Ingestion & Offline Spooling
- **Immediate Delivery**: Real-time USB connection events are sent directly via `POST /api/agent/events`.
- **Offline Outbox Spooling (`OutboxSpoolService`)**:
  - If the Admin Server is unreachable (network partition, server maintenance), events are serialized as atomic JSON records into `./data/spool/` (or `/var/lib/usbshield/spool/`).
  - During subsequent successful heartbeat cycles (server back ONLINE), the agent automatically drains the spool in chronological FIFO order and removes processed files upon successful server ACK.

### 2.4. Real-time Desktop Notification
- **Desktop Popup Service (`DesktopNotificationService`)**:
  - Dispatches non-intrusive desktop warnings via `notify-send` when an unauthorized USB Mass Storage device is blocked.
  - Resolves active graphical session bus (`DBUS_SESSION_BUS_ADDRESS`, `WAYLAND_DISPLAY` / `DISPLAY`) across Wayland (GNOME/Ubuntu) and X11 sessions.
  - Configured with passwordless execution via `/etc/sudoers.d/usbshield`.

### 2.5. Debian Packaging Skeleton & Systemd Daemon
- Location: `packaging/agent-deb/`
- Included assets:
  - Systemd service unit: `systemd/usbshield-agent.service` (`Restart=always`, `User=usbshield`).
  - Package metadata: `DEBIAN/control` (dependencies: `default-jre-headless | openjdk-21-jre-headless`, `usbguard`, `libnotify-bin`).
  - Pre/post lifecycle scripts: `postinst` (creates system user `usbshield`, adds to `usbguard` group, sets directory permissions `0750`, registers sudoers rule), `prerm` (stops and disables service).

---

## 3. Scope Clarification & Member 2 Bridge (Lead Feedback #3)

To ensure rapid testing and verify the end-to-end telemetry pipeline during Week 1 without waiting for hardware environments:
- Member 3 implemented **working reference adapters**:
  - `UsbGuardGateway` / `AdaptiveUsbGuardGateway`: Bridge to write rules to `/etc/usbguard/rules.conf` and issue reload commands, with simulation fallback for Windows.
  - `ActiveUserResolver` / `AdaptiveActiveUserResolver`: Bridge calling `loginctl list-sessions` / `show-session` with fallback for testing.
  - `UsbGuardEventListener`: IPC watch thread reading device events.
- **Contract Boundary**:
  - These classes implement the contracts agreed in `docs/HANDOFF_MEMBER1_MEMBER3.md` (`UsbGuardGateway`, `ActiveUserInfo`, `UsbGuardDeviceInfo`).
  - In Week 2, Member 2's specialized C++/CLI parser and enhanced session resolver plug directly into these adapter slots as the primary production engine. Member 3's code serves as the host harness.

---

## 4. Key Fixes Applied (Lead Feedback Items #4 & #5)

| Feedback Item | Problem Identified | Resolution Applied | Verification |
|---|---|---|---|
| **Event Deduplication (#4)** | `usbguard watch` outputs 3 sections per insert (`PresenceChanged`, `PolicyChanged`, `PolicyApplied`), triggering 3 duplicate POST requests. | Implemented `WatchSection` state machine in `UsbGuardEventListener`. Gated trigger strictly on `POLICY_APPLIED` (insertion) and `Remove` (removal) with a 1500ms sliding deduplication window. | `UsbGuardEventListenerTest` passed. Single event emitted per physical action. |
| **Policy Sync Error Propagation (#5)** | `AdaptiveUsbGuardGateway` previously swallowed filesystem/reload exceptions, causing `PolicySyncService` to update version and send false ACK. | Updated `UsbGuardGateway.applyWhitelist(...)` to return `boolean`. Gateway returns `false` on any I/O or reload failure. `PolicySyncService` aborts version bump and skips ACK. | `PolicySyncServiceTest` passed with mock failure scenario. |

---

## 5. End-to-End Verification & Evidence

### 5.1. Unit Test Suite
All 15 automated unit tests pass cleanly:
```bash
mvn test
# Results:
# [INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
```
Key tests include:
- `EndpointManagerTest`: Machine ID generation, credential load/save.
- `OutboxSpoolServiceTest`: Offline spool creation, FIFO draining, server error retry.
- `PolicySyncServiceTest`: Successful sync with ACK; failed sync aborting version bump.
- `UsbGuardEventListenerTest`: Multi-line watch stream deduplication.
- `DesktopNotificationServiceTest`: OS detection, argument building, notification dispatch.

### 5.2. Live E2E Agent $\leftrightarrow$ Admin Server Integration Run (Lead Feedback #2)
Live test executed between running `usbshield-admin-server.jar` and `usbshield-agent.jar`:
- **Evidence Log Path:** `docs/client-agent/evidence/01-agent-server-e2e-live-run.txt`
- **Results:**
  1. **Dynamic Enrollment:** Endpoint `EP-BA26FD9F` registered on Admin Server database.
  2. **Periodic Heartbeat:** Cycles acknowledged every 5000ms.
  3. **Event Telemetry Ingestion:** USB Mass Storage device insertion (`0951:1666 Kingston DataTraveler 3.0`) ingested, attributed to user `student01`, marked `BLOCKED`, and persisted into server database as event ID #1.

# USBShield Client Agent — Linux IPC & Permission Architecture Research

**Document ID:** `WEEK1_IPC_PERMISSION_RESEARCH`  
**Target Environment:** Ubuntu Desktop 22.04 LTS / 24.04 LTS (systemd, Wayland / X11)  
**Role:** Member 3 (Client Agent & System Integration Lead)  
**Date:** 2026-10-12  

---

## 1. Security Context & Privilege Separation Strategy

The USBShield Client Agent runs as a resident background service responsible for:
1. Interacting with the low-level `usbguard-daemon` to observe and enforce USB peripheral policies.
2. Interacting with `systemd-logind` to resolve the logged-in desktop user.
3. Communicating over HTTPS/REST with the centralized Admin Server.
4. Displaying real-time notifications on the user's graphical desktop session.

### Why Avoid Running as Root?
Running the entire Java Virtual Machine (JVM) process as `root` introduces significant security risks:
- Memory safety vulnerabilities or dependency bugs in third-party libraries (e.g., HTTP clients, JSON parsers) could lead to complete system compromise.
- Remote code execution (RCE) vectors via server communication channels would operate with unrestricted superuser privileges.

**Chosen Strategy: Dedicated Service User (`usbshield`)**  
The agent runs under a dedicated, unprivileged system account `usbshield:usbshield`, granted strictly necessary capabilities through Linux group memberships and granular `sudoers` rules.

---

## 2. USBGuard IPC Architecture & Access Control

`usbguard-daemon` manages USB device authorization in the Linux kernel via the netlink/udev subsystem. It provides an IPC interface over a UNIX domain socket.

### 2.1. IPC Socket Location & Daemon Configuration
The daemon configuration is stored at `/etc/usbguard/usbguard-daemon.conf`:

```ini
# IPC access control configuration
IPCAllowedUsers=root usbshield
IPCAllowedGroups=usbguard
IPCAccessControlFiles=/etc/usbguard/IPCAccessControl.d/
```

- **Socket Path:** `/run/usbguard/usbguard-ipc.socket` (or `/var/run/usbguard.sock`).
- By adding the `usbshield` user to the `usbguard` group during package installation (`usermod -aG usbguard usbshield`), the agent obtains read/write access to the IPC socket without requiring root privileges.

### 2.2. IPC Access Rights
Granular permissions in `/etc/usbguard/IPCAccessControl.d/usbshield` (or via `IPCAllowedUsers`):
- `Devices.list`, `Devices.listen`: Required for monitoring real-time device insertion/removal (`usbguard watch` and `list-devices`).
- `Policy.list`: Required for auditing active whitelist/blacklist rules (`usbguard list-rules`).
- `Policy.modify`: Required for dynamically applying runtime authorization rules.

### 2.3. Rules File Access (`/etc/usbguard/rules.conf`)
Permanent policy persistence requires writing to `/etc/usbguard/rules.conf`:
- File permissions: Owned by `root:usbguard`, mode `0660` (or `0640` with group write).
- Reload mechanism: The agent issues `systemctl reload usbguard` or invokes `usbguard install-rule` through the IPC socket.

---

## 3. Active User Resolution via `systemd-logind`

To attribute USB insertion events to the specific local user currently sitting at the workstation, the agent queries `systemd-logind`.

### 3.1. Permission Requirements
- `systemd-logind` exposes an unprivileged D-Bus API under `org.freedesktop.login1`.
- Any local user (including `usbshield`) can query session states using `loginctl`:
  ```bash
  loginctl list-sessions --no-legend
  loginctl show-session <sessionId> -p Name -p Id -p User -p Type -p Seat -p State -p Active
  ```
- No privilege elevation or `sudo` is required to inspect session metadata.

### 3.2. Session Selection Logic
1. Filter for sessions where `Seat == "seat0"` (physical console).
2. Filter for sessions where `State == "active"` and `Active == "yes"`.
3. Filter for graphical sessions (`Type == "wayland"` or `Type == "x11"`).
4. If exactly one session matches $\rightarrow$ extract `Name` (username) and `User` (UID).
5. If zero or multiple ambiguous sessions match $\rightarrow$ return `UNKNOWN` with fail-safe attribution (as specified in `docs/HANDOFF_MEMBER1_MEMBER3.md`).

---

## 4. Desktop Notification Elevation & Session Bridging

### 4.1. The Isolation Challenge
The agent executes as a system daemon in a background systemd cgroup (`system.slice`). Consequently:
- It has no active display server connection (`DISPLAY` or `WAYLAND_DISPLAY` is unset).
- It does not possess the user's session bus address (`DBUS_SESSION_BUS_ADDRESS`).
- Standard desktop notification utilities (`notify-send`) fail immediately when invoked directly from a system daemon.

### 4.2. Solution Architecture

```
+----------------------------------------------------------------------------+
|                          System Service (usbshield)                        |
|                                                                            |
|  1. Detect BLOCKED event                                                   |
|  2. Resolve active GUI user: username="student01", UID=1001                |
|  3. Construct session bus path: /run/user/1001/bus                         |
+----------------------------------------------------------------------------+
                                     |
                                     | sudo -u student01 notify-send ...
                                     v
+----------------------------------------------------------------------------+
|                       Desktop User Session (student01)                     |
|                                                                            |
|  Environment:                                                              |
|    DBUS_SESSION_BUS_ADDRESS="unix:path=/run/user/1001/bus"                 |
|    WAYLAND_DISPLAY="wayland-0" / DISPLAY=":0"                              |
|                                                                            |
|  Desktop Notification Daemon (GNOME Shell / Dunst)                         |
|  -> Renders Popup: "USB Blocked - Device unauthorized"                     |
+----------------------------------------------------------------------------+
```

### 4.3. Sudoers Configuration (`/etc/sudoers.d/usbshield`)
To allow the `usbshield` service account to dispatch `notify-send` to the target user without prompting for a password:

```sudoers
# /etc/sudoers.d/usbshield
# Allow USBShield daemon to send desktop popup notifications to users
Defaults:usbshield !requiretty
usbshield ALL=(ALL) NOPASSWD: /usr/bin/notify-send
```

File security rules:
- Permissions must be set to `0440` (`chmod 0440 /etc/sudoers.d/usbshield`).
- Owned by `root:root`.

### 4.4. Execution Command in Java Agent
Implemented in `DesktopNotificationService.java`:
```bash
sudo -u <username> \
  DBUS_SESSION_BUS_ADDRESS="unix:path=/run/user/<UID>/bus" \
  DISPLAY=":0" \
  WAYLAND_DISPLAY="wayland-0" \
  /usr/bin/notify-send \
  --urgency=critical \
  --app-name="USBShield" \
  "USB Device Blocked" \
  "Unauthorized USB device (<deviceName>) was blocked by corporate policy."
```

---

## 5. Debian Packaging Automation (`packaging/agent-deb/`)

All permissions and user configurations are automated by the Debian package installer scripts:

### 5.1. `postinst` (Post-Installation Hook)
```bash
#!/bin/sh
set -e

# 1. Create dedicated system user and group
if ! getent group usbshield >/dev/null; then
    addgroup --system usbshield
fi

if ! getent passwd usbshield >/dev/null; then
    adduser --system --ingroup usbshield --home /var/lib/usbshield \
            --no-create-home --shell /bin/false usbshield
fi

# 2. Add to usbguard group for IPC socket access
if getent group usbguard >/dev/null; then
    usermod -aG usbguard usbshield
fi

# 3. Create required directories with least privilege permissions
mkdir -p /var/lib/usbshield/spool /etc/usbshield
chown -R usbshield:usbshield /var/lib/usbshield /etc/usbshield
chmod 0750 /var/lib/usbshield /var/lib/usbshield/spool
chmod 0750 /etc/usbshield

# 4. Deploy sudoers rule for desktop notifications
chmod 0440 /etc/sudoers.d/usbshield

# 5. Reload systemd daemon
systemctl daemon-reload
```

### 5.2. `prerm` (Pre-Removal Hook)
```bash
#!/bin/sh
set -e

# Stop and disable agent service before package uninstallation
systemctl stop usbshield-agent.service || true
systemctl disable usbshield-agent.service || true
```

---

## 6. Summary of Security Attributes

| Asset / Operation | Principle Applied | Permissions / Mechanism |
|---|---|---|
| **JVM Process** | Least Privilege | Runs as non-root user `usbshield` |
| **USBGuard IPC Socket** | Role-Based Access | Group `usbguard`, mode `0660` |
| **Outbox Spool Directory** | Data Confidentiality | `/var/lib/usbshield/spool` mode `0750` |
| **Endpoint Credentials** | Key Secrecy | `/var/lib/usbshield/endpoint.json` mode `0600` |
| **User Session Discovery** | Read-Only D-Bus | `loginctl` standard user queries |
| **Desktop Popups** | Narrow Elevation | Sudoers rule strictly limited to `/usr/bin/notify-send` |

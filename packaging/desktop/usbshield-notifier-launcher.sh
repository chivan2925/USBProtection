#!/bin/bash
# USBShield Desktop Notification Helper Launcher
# Launches Member 2 DesktopNotificationClientMain inside the active user graphical session

USER_UID=$(id -u)

# Guard: only run for regular desktop users (UID >= 1000)
if [ "$USER_UID" -lt 1000 ]; then
    exit 0
fi

# Ensure runtime socket directory exists
if [ ! -d /run/usbshield-notifier ]; then
    mkdir -p /run/usbshield-notifier 2>/dev/null || true
    chmod 1777 /run/usbshield-notifier 2>/dev/null || true
fi

# Locate the agent JAR
JAR_PATH="/opt/usbshield-agent/usbshield-agent.jar"
if [ ! -f "$JAR_PATH" ]; then
    # Fallback for development/testing environments
    JAR_PATH="./target/usbshield-agent.jar"
fi

if [ ! -f "$JAR_PATH" ]; then
    echo "[USBShield Notifier] JAR not found at $JAR_PATH, exiting." >&2
    exit 1
fi

# Execute M2's DesktopNotificationClientMain with numeric UID argument
exec /usr/bin/java -cp "$JAR_PATH" com.group.usbshield.agent.notification.DesktopNotificationClientMain "$USER_UID"

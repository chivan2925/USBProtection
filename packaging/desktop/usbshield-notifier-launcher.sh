#!/usr/bin/env bash
# Launched via XDG autostart IN the graphical user's session, never by the
# privileged Agent. Boot's PropertiesLauncher reads BOOT-INF/classes and nested
# BOOT-INF/lib jars and invokes the M2-owned desktop helper main class.
set -euo pipefail

USER_UID="$(id -u)"
if (( USER_UID < 1000 )); then
    exit 0
fi

# A normal desktop user must not create/chmod the shared IPC directory.
# Its ownership and sticky permissions belong to the root-owned .deb postinst.
if [[ ! -d /run/usbshield-notifier ]]; then
    echo '[USBShield Notifier] Runtime socket directory missing' >&2
    exit 1
fi

JAR_PATH=/opt/usbshield-agent/usbshield-agent.jar
if [[ ! -r "$JAR_PATH" ]]; then
    echo '[USBShield Notifier] Spring Boot agent JAR not readable' >&2
    exit 1
fi

# The normal -cp <fat-jar> <app-class> invocation cannot see BOOT-INF/classes.
# PropertiesLauncher is contained at the archive root in Spring Boot 3.3.
# Pin loader.home to the root-managed installation to avoid user-owned
# loader.properties overriding launcher configuration via the current directory.
exec /usr/bin/java \
    -Dloader.home=/opt/usbshield-agent \
    -Dloader.main=com.group.usbshield.agent.notification.DesktopNotificationClientMain \
    -cp "$JAR_PATH" \
    org.springframework.boot.loader.launch.PropertiesLauncher "$USER_UID"

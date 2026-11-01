#!/usr/bin/env bash
# XDG autostart runs as the graphical user (not as the system Agent).
# The Java helper creates its own per-UID socket below XDG_RUNTIME_DIR,
# applying POSIX named-user ACLs for the usbshield service account.
set -euo pipefail

USER_UID="$(id -u)"
if (( USER_UID < 1000 )); then
    exit 0
fi

# Refuse forged/fallback paths. /run/user/<uid> is managed by pam_systemd.
EXPECTED_RUNTIME="/run/user/${USER_UID}"
if [[ "${XDG_RUNTIME_DIR:-}" != "$EXPECTED_RUNTIME" || ! -d "$EXPECTED_RUNTIME" || -L "$EXPECTED_RUNTIME" ]]; then
    echo '[USBShield Notifier] Missing or unexpected XDG_RUNTIME_DIR' >&2
    exit 1
fi
if [[ "$(stat -c '%u' -- "$EXPECTED_RUNTIME")" != "$USER_UID" ]]; then
    echo '[USBShield Notifier] Runtime directory owner mismatch' >&2
    exit 1
fi
if [[ ! -x /usr/bin/setfacl ]]; then
    echo '[USBShield Notifier] Missing acl package (/usr/bin/setfacl)' >&2
    exit 1
fi

JAR_PATH=/opt/usbshield-agent/usbshield-agent.jar
if [[ ! -r "$JAR_PATH" ]]; then
    echo '[USBShield Notifier] Spring Boot agent JAR not readable' >&2
    exit 1
fi

# PropertiesLauncher is contained at the archive root in Spring Boot 3.3.
exec /usr/bin/java \
    -Dloader.home=/opt/usbshield-agent \
    -Dloader.main=com.group.usbshield.agent.notification.DesktopNotificationClientMain \
    -cp "$JAR_PATH" \
    org.springframework.boot.loader.launch.PropertiesLauncher "$USER_UID"

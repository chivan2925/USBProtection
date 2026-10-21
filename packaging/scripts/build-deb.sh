#!/bin/bash
set -e

# Script to assemble and build usbshield-agent_1.0.0_amd64.deb
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
PACKAGING_DIR="$ROOT_DIR/packaging"
DEB_STAGING="$PACKAGING_DIR/staging"
OUTPUT_DIR="$PACKAGING_DIR/dist"

echo "=== Building Client Agent JAR ==="
cd "$ROOT_DIR/client-agent"
./mvnw clean package -DskipTests || mvn clean package -DskipTests

JAR_FILE="$ROOT_DIR/client-agent/target/usbshield-agent.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: $JAR_FILE not found!"
    exit 1
fi

echo "=== Preparing Debian Package Staging Directory ==="
rm -rf "$DEB_STAGING"
mkdir -p "$DEB_STAGING/DEBIAN"
mkdir -p "$DEB_STAGING/opt/usbshield-agent"
mkdir -p "$DEB_STAGING/etc/usbshield-agent"
mkdir -p "$DEB_STAGING/usr/lib/systemd/system"
mkdir -p "$OUTPUT_DIR"

# Copy DEBIAN control and maintainer scripts
cp "$PACKAGING_DIR/agent-deb/DEBIAN/control" "$DEB_STAGING/DEBIAN/"
cp "$PACKAGING_DIR/agent-deb/DEBIAN/postinst" "$DEB_STAGING/DEBIAN/"
cp "$PACKAGING_DIR/agent-deb/DEBIAN/prerm" "$DEB_STAGING/DEBIAN/"
chmod 755 "$DEB_STAGING/DEBIAN/postinst" "$DEB_STAGING/DEBIAN/prerm"

# Copy JAR
cp "$JAR_FILE" "$DEB_STAGING/opt/usbshield-agent/usbshield-agent.jar"
chmod 755 "$DEB_STAGING/opt/usbshield-agent/usbshield-agent.jar"

# Copy config and default base rules
# The installed service must load the protected, deployment-specific config,
# NOT the developer application.yml (which defaults to ./data on Windows).
cp "$PACKAGING_DIR/agent-deb/etc/usbshield-agent/application.yml" "$DEB_STAGING/etc/usbshield-agent/application.yml"
chmod 640 "$DEB_STAGING/etc/usbshield-agent/application.yml"
if [ -f "$PACKAGING_DIR/usbguard/rules-v0.1.conf" ]; then
    cp "$PACKAGING_DIR/usbguard/rules-v0.1.conf" "$DEB_STAGING/etc/usbshield-agent/rules-v0.1.conf"
    chmod 644 "$DEB_STAGING/etc/usbshield-agent/rules-v0.1.conf"
fi

# Copy systemd service file
cp "$PACKAGING_DIR/systemd/usbshield-agent.service" "$DEB_STAGING/usr/lib/systemd/system/"
chmod 644 "$DEB_STAGING/usr/lib/systemd/system/usbshield-agent.service"

# Copy XDG autostart desktop entry and user-session notification launcher
mkdir -p "$DEB_STAGING/etc/xdg/autostart"
if [ -f "$PACKAGING_DIR/desktop/usbshield-notifier.desktop" ]; then
    cp "$PACKAGING_DIR/desktop/usbshield-notifier.desktop" "$DEB_STAGING/etc/xdg/autostart/"
    chmod 644 "$DEB_STAGING/etc/xdg/autostart/usbshield-notifier.desktop"
fi
if [ -f "$PACKAGING_DIR/desktop/usbshield-notifier-launcher.sh" ]; then
    cp "$PACKAGING_DIR/desktop/usbshield-notifier-launcher.sh" "$DEB_STAGING/opt/usbshield-agent/"
    chmod 755 "$DEB_STAGING/opt/usbshield-agent/usbshield-notifier-launcher.sh"
fi

echo "=== Packaging DEB with dpkg-deb ==="
dpkg-deb --build "$DEB_STAGING" "$OUTPUT_DIR/usbshield-agent_1.0.0_amd64.deb"

echo "=== Successfully built: $OUTPUT_DIR/usbshield-agent_1.0.0_amd64.deb ==="

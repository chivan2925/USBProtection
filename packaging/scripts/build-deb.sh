#!/usr/bin/env bash
# Build an Ubuntu 24.04 amd64 Agent DEB from the checked-out source on Linux.
# Uses the same unique-version / release-manifest convention as build-deb.ps1.
set -euo pipefail
umask 022

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$script_dir/../.." && pwd)"
packaging="$root/packaging"
stage="$packaging/staging/linux-deb"
dist="$packaging/dist"
jar="$root/client-agent/target/usbshield-agent.jar"

for command in dpkg dpkg-deb sha256sum md5sum find sort sed date git; do
    command -v "$command" >/dev/null 2>&1 || { echo "ERROR: missing build tool $command" >&2; exit 1; }
done

if [[ "$(dpkg --print-architecture)" != amd64 ]]; then
    echo 'ERROR: build-deb.sh requires an amd64 Debian/Ubuntu builder.' >&2
    exit 1
fi

stamp="$(date -u +%Y%m%d%H%M%S%3N)"
[[ "$stamp" =~ ^[0-9]{17}$ ]] || { echo 'ERROR: unsupported GNU date millisecond format' >&2; exit 1; }
version="1.0.0+build${stamp}"
filename="usbshield-agent_${version}_amd64.deb"
output="$dist/$filename"
manifest="$dist/usbshield-agent.release.json"

printf '=== Building Client Agent JAR ===\n'
(
    cd "$root/client-agent"
    if [[ -x ./mvnw ]]; then ./mvnw clean package -DskipTests
    else mvn clean package -DskipTests
    fi
)
[[ -s "$jar" ]] || { echo "ERROR: Missing or empty Agent JAR: $jar" >&2; exit 1; }

printf '=== Preparing Linux-only Debian staging directory ===\n'
rm -rf -- "$stage"
install -d -m 0755 "$stage/DEBIAN" "$stage/opt/usbshield-agent" \
    "$stage/etc/usbshield-agent" "$stage/usr/lib/systemd/system" \
    "$stage/etc/xdg/autostart" "$dist"

copy_text() {
    local input="$1" target="$2" mode="$3"
    [[ -f "$input" ]] || { echo "ERROR: missing package input: $input" >&2; exit 1; }
    cp -- "$input" "$target"
    sed -i 's/\r$//' "$target"
    chmod "$mode" "$target"
}

copy_text "$packaging/agent-deb/DEBIAN/control" "$stage/DEBIAN/control" 0644
# Version may be replaced only when the repository's static control version matches.
if [[ "$(grep -cx 'Version: 1.0.0' "$stage/DEBIAN/control")" != 1 ]]; then
    echo 'ERROR: expected one unmodified Version: 1.0.0 in DEBIAN/control' >&2
    exit 1
fi
sed -i "s/^Version: 1\.0\.0$/Version: $version/" "$stage/DEBIAN/control"
grep -qxF "Version: $version" "$stage/DEBIAN/control"
copy_text "$packaging/agent-deb/DEBIAN/postinst" "$stage/DEBIAN/postinst" 0755
copy_text "$packaging/agent-deb/DEBIAN/prerm" "$stage/DEBIAN/prerm" 0755

cp -- "$jar" "$stage/opt/usbshield-agent/usbshield-agent.jar"
chmod 0755 "$stage/opt/usbshield-agent/usbshield-agent.jar"
copy_text "$packaging/agent-deb/etc/usbshield-agent/application.yml" "$stage/etc/usbshield-agent/application.yml" 0640
copy_text "$packaging/usbguard/rules-v0.1.conf" "$stage/etc/usbshield-agent/rules-v0.1.conf" 0644
copy_text "$packaging/systemd/usbshield-agent.service" "$stage/usr/lib/systemd/system/usbshield-agent.service" 0644
copy_text "$packaging/desktop/usbshield-notifier.desktop" "$stage/etc/xdg/autostart/usbshield-notifier.desktop" 0644
copy_text "$packaging/desktop/usbshield-notifier-launcher.sh" "$stage/opt/usbshield-agent/usbshield-notifier-launcher.sh" 0755

# Match the Windows builder's Debian control/md5sums evidence for all data files.
(
    cd "$stage"
    find . -type f ! -path './DEBIAN/*' -printf '%P\n' | LC_ALL=C sort |
        while IFS= read -r relative; do md5sum -- "$relative"; done
) > "$stage/DEBIAN/md5sums"
chmod 0644 "$stage/DEBIAN/md5sums"

# The builder is allowed to run from a shared workspace inheriting setgid.
find "$stage" -type d -exec chmod g-s {} +

printf '=== Packaging DEB with root ownership ===\n'
dpkg-deb --root-owner-group --build "$stage" "$output"
[[ -s "$output" ]] || { echo 'ERROR: dpkg-deb produced no output' >&2; exit 1; }
sha="$(sha256sum "$output" | cut -d ' ' -f1)"
head="$(git -C "$root" rev-parse HEAD)"
[[ "$sha" =~ ^[0-9a-f]{64}$ && "$head" =~ ^[0-9a-f]{40}$ ]] || {
    echo 'ERROR: cannot generate SHA256/Git HEAD release provenance' >&2; exit 1;
}
# Git HEAD identifies the base commit, not uncommitted file modifications.
cat > "$manifest" <<EOF
{
  "version": "$version",
  "filename": "$filename",
  "sha256": "$sha",
  "gitHead": "$head",
  "builtAtUtc": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF
printf 'DEB: %s\nSHA256: %s\nRelease manifest: %s\n' "$output" "$sha" "$manifest"
printf 'Check the package on Ubuntu with dpkg-deb -I/-c and sha256sum before installation.\n'

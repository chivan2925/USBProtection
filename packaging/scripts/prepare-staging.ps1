# PowerShell script to prepare the Debian package staging directory
# Mirrors packaging/scripts/build-deb.sh for environments without bash/dpkg-deb
[CmdletBinding()]
param(
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$packagingDir = Join-Path $root 'packaging'
$debStaging = Join-Path $packagingDir 'staging'
$jarFile = Join-Path $root 'client-agent/target/usbshield-agent.jar'

Write-Host "=== USBShield Staging Preparation ===" -ForegroundColor Cyan

if (-not (Test-Path -LiteralPath $jarFile)) {
    throw "Error: Executable JAR not found at $jarFile. Run 'mvn package -DskipTests' in client-agent first."
}

if ($Clean -or (Test-Path -LiteralPath $debStaging)) {
    Write-Host "Cleaning existing staging directory..." -ForegroundColor Yellow
    Remove-Item -Path $debStaging -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "Creating staging directory tree..." -ForegroundColor Yellow
$dirs = @(
    'DEBIAN',
    'opt/usbshield-agent',
    'etc/usbshield-agent',
    'usr/lib/systemd/system',
    'etc/xdg/autostart'
)
foreach ($d in $dirs) {
    New-Item -ItemType Directory -Path (Join-Path $debStaging $d) -Force | Out-Null
}

Write-Host "Copying packaging files into staging..." -ForegroundColor Yellow

# Control & maintainer scripts
Copy-Item (Join-Path $packagingDir 'agent-deb/DEBIAN/control') (Join-Path $debStaging 'DEBIAN/control')
Copy-Item (Join-Path $packagingDir 'agent-deb/DEBIAN/postinst') (Join-Path $debStaging 'DEBIAN/postinst')
Copy-Item (Join-Path $packagingDir 'agent-deb/DEBIAN/prerm') (Join-Path $debStaging 'DEBIAN/prerm')

# Executable Agent JAR & Desktop Notifier Launcher
Copy-Item $jarFile (Join-Path $debStaging 'opt/usbshield-agent/usbshield-agent.jar')
Copy-Item (Join-Path $packagingDir 'desktop/usbshield-notifier-launcher.sh') (Join-Path $debStaging 'opt/usbshield-agent/usbshield-notifier-launcher.sh')

# Configurations
Copy-Item (Join-Path $packagingDir 'agent-deb/etc/usbshield-agent/application.yml') (Join-Path $debStaging 'etc/usbshield-agent/application.yml')
if (Test-Path (Join-Path $packagingDir 'usbguard/rules-v0.1.conf')) {
    Copy-Item (Join-Path $packagingDir 'usbguard/rules-v0.1.conf') (Join-Path $debStaging 'etc/usbshield-agent/rules-v0.1.conf')
}

# Systemd Unit
Copy-Item (Join-Path $packagingDir 'systemd/usbshield-agent.service') (Join-Path $debStaging 'usr/lib/systemd/system/usbshield-agent.service')

# Desktop Autostart
Copy-Item (Join-Path $packagingDir 'desktop/usbshield-notifier.desktop') (Join-Path $debStaging 'etc/xdg/autostart/usbshield-notifier.desktop')

Write-Host "=== Staging Directory Prepared Successfully at $debStaging ===" -ForegroundColor Green

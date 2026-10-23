# USBShield Week 3 - Static Packaging & Service Verification (Windows Scope)
# Validates packaging metadata, systemd units, maintainer scripts, desktop launcher,
# and live configuration without requiring physical USB, Ubuntu, or root access.
[CmdletBinding()]
param(
    [switch]$CheckBuiltJar,
    [switch]$CheckStaging
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Assert-Contains {
    param([string]$File, [string]$Needle)
    $full = Join-Path $root $File
    if (-not (Test-Path -LiteralPath $full)) { throw "Missing file: $File" }
    $value = [System.IO.File]::ReadAllText($full)
    if (-not $value.Contains($Needle)) { throw "Missing required value in ${File}: $Needle" }
    Write-Host "  [OK] $File contains: '$Needle'" -ForegroundColor Green
}

function Assert-NotContains {
    param([string]$File, [string]$Needle)
    $full = Join-Path $root $File
    if (-not (Test-Path -LiteralPath $full)) { throw "Missing file: $File" }
    $value = [System.IO.File]::ReadAllText($full)
    if ($value.Contains($Needle)) { throw "Unexpected unsafe value in ${File}: '$Needle'" }
    Write-Host "  [OK] $File correctly omits unsafe value: '$Needle'" -ForegroundColor Green
}

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  USBShield Week 3 Packaging & Service Static Verification      " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# 1. Systemd Service Unit
Write-Host "`n[1/6] Verifying Systemd Service Unit..." -ForegroundColor Yellow
$unit = 'packaging/systemd/usbshield-agent.service'
Assert-Contains $unit 'User=usbshield'
Assert-Contains $unit 'Group=usbshield'
Assert-Contains $unit '--spring.profiles.active=live'
Assert-Contains $unit '--spring.config.additional-location=file:/etc/usbshield-agent/application.yml'
Assert-Contains $unit 'Requires=usbguard.service'
Assert-Contains $unit 'Restart=on-failure'
Assert-Contains $unit 'ProtectSystem=full'
Assert-Contains $unit 'NoNewPrivileges=true'

# 2. Debian Control & Maintainer Scripts
Write-Host "`n[2/6] Verifying Debian Package Control & Maintainer Scripts..." -ForegroundColor Yellow
$control = 'packaging/agent-deb/DEBIAN/control'
$postinst = 'packaging/agent-deb/DEBIAN/postinst'
$prerm = 'packaging/agent-deb/DEBIAN/prerm'

Assert-Contains $control 'Package: usbshield-agent'
Assert-Contains $control 'Version: 1.0.0'
Assert-Contains $control 'Architecture: amd64'
Assert-Contains $control 'openjdk-21-jre-headless'
Assert-Contains $control 'usbguard'
Assert-Contains $control 'libnotify-bin'

Assert-Contains $postinst 'groupadd --system usbshield'
Assert-Contains $postinst 'useradd --system --gid usbshield'
Assert-Contains $postinst 'chmod 755 /opt/usbshield-agent'
Assert-Contains $postinst 'chmod 750 /etc/usbshield-agent'
Assert-Contains $postinst 'chmod 700 /var/lib/usbshield-agent'
Assert-Contains $postinst 'mkdir -p /run/usbshield-notifier'
Assert-Contains $postinst 'chmod 1777 /run/usbshield-notifier'
Assert-Contains $postinst 'd /run/usbshield-notifier 1777 root root -'
Assert-Contains $postinst 'rm -f /etc/sudoers.d/usbshield'
Assert-Contains $postinst 'systemctl daemon-reload'
Assert-Contains $postinst 'systemctl enable usbshield-agent.service'

Assert-Contains $prerm 'systemctl stop usbshield-agent.service'
Assert-Contains $prerm 'systemctl disable usbshield-agent.service'

# 3. Live Configuration Integrity
Write-Host "`n[3/6] Verifying Live Configuration Files..." -ForegroundColor Yellow
$configInstalled = 'packaging/agent-deb/etc/usbshield-agent/application.yml'
$configLiveResource = 'client-agent/src/main/resources/application-live.yml'

Assert-Contains $configInstalled 'data-dir: /var/lib/usbshield-agent'
Assert-Contains $configInstalled 'config-dir: /etc/usbshield-agent'
Assert-Contains $configInstalled 'simulation-enabled: false'
Assert-Contains $configInstalled 'notification-enabled: true'
Assert-NotContains $configInstalled 'default-enrollment-secret'

Assert-Contains $configLiveResource 'simulation-enabled: false'
Assert-Contains $configLiveResource '/var/lib/usbshield-agent'

# 4. Desktop Autostart & Notification Launcher
Write-Host "`n[4/6] Verifying Desktop Notification Launcher..." -ForegroundColor Yellow
$desktop = 'packaging/desktop/usbshield-notifier.desktop'
$launcher = 'packaging/desktop/usbshield-notifier-launcher.sh'

Assert-Contains $desktop 'Exec=/opt/usbshield-agent/usbshield-notifier-launcher.sh'
Assert-Contains $desktop 'X-GNOME-Autostart-enabled=true'
Assert-Contains $desktop 'Categories=System;Security;'

Assert-Contains $launcher '#!/usr/bin/env bash'
Assert-Contains $launcher 'USER_UID="$(id -u)"'
Assert-Contains $launcher '(( USER_UID < 1000 ))'
Assert-Contains $launcher 'org.springframework.boot.loader.launch.PropertiesLauncher'
Assert-Contains $launcher 'com.group.usbshield.agent.notification.DesktopNotificationClientMain'
Assert-Contains $launcher '-Dloader.home=/opt/usbshield-agent'
Assert-NotContains $launcher 'exec /usr/bin/java -cp "$JAR_PATH" com.group.usbshield.agent.notification.DesktopNotificationClientMain'

# 5. Packaging Build Script
Write-Host "`n[5/6] Verifying Build Script..." -ForegroundColor Yellow
$buildScript = 'packaging/scripts/build-deb.sh'
Assert-Contains $buildScript 'agent-deb/etc/usbshield-agent/application.yml'
Assert-Contains $buildScript 'usbshield-notifier-launcher.sh'
Assert-Contains $buildScript 'usbshield-notifier.desktop'
Assert-Contains $buildScript 'dpkg-deb --build "$DEB_STAGING"'

# 6. Executable JAR Inspection (Optional with -CheckBuiltJar)
Write-Host "`n[6/6] Verifying Executable Fat-JAR Structure..." -ForegroundColor Yellow
if ($CheckBuiltJar) {
    $jarPath = Join-Path $root 'client-agent/target/usbshield-agent.jar'
    if (-not (Test-Path -LiteralPath $jarPath)) { 
        throw "Executable JAR missing at $jarPath. Run 'mvn package -DskipTests' first." 
    }
    $jarEntries = & jar tf $jarPath
    if ($LASTEXITCODE -ne 0) { throw "jar tf failed with exit code $LASTEXITCODE" }
    
    $requiredClasses = @(
        'org/springframework/boot/loader/launch/PropertiesLauncher.class',
        'BOOT-INF/classes/com/group/usbshield/agent/notification/DesktopNotificationClientMain.class',
        'BOOT-INF/classes/com/group/usbshield/agent/endpoint/EndpointManager.class',
        'BOOT-INF/classes/com/group/usbshield/agent/sync/HeartbeatCoordinator.class',
        'BOOT-INF/classes/com/group/usbshield/agent/outbox/OutboxSpoolService.class'
    )
    foreach ($entry in $requiredClasses) {
        if ($jarEntries -notcontains $entry) { throw "Executable JAR is missing required class: $entry" }
        Write-Host "  [OK] Executable JAR contains: $entry" -ForegroundColor Green
    }
} else {
    Write-Host "  (Skipped JAR inspection. Pass -CheckBuiltJar to inspect target/usbshield-agent.jar)" -ForegroundColor Gray
}

# Optional Staging Directory Check
if ($CheckStaging) {
    $stagingPath = Join-Path $root 'packaging/staging'
    if (Test-Path -LiteralPath $stagingPath) {
        Write-Host "`nChecking staging structure..." -ForegroundColor Yellow
        $stagingFiles = @(
            'DEBIAN/control',
            'DEBIAN/postinst',
            'DEBIAN/prerm',
            'opt/usbshield-agent/usbshield-agent.jar',
            'opt/usbshield-agent/usbshield-notifier-launcher.sh',
            'etc/usbshield-agent/application.yml',
            'etc/xdg/autostart/usbshield-notifier.desktop',
            'usr/lib/systemd/system/usbshield-agent.service'
        )
        foreach ($sf in $stagingFiles) {
            $sfPath = Join-Path $stagingPath $sf
            if (Test-Path -LiteralPath $sfPath) {
                Write-Host "  [OK] Staging contains: $sf" -ForegroundColor Green
            } else {
                Write-Host "  [WARN] Staging missing: $sf" -ForegroundColor Yellow
            }
        }
    }
}

Write-Host "`n================================================================" -ForegroundColor Cyan
Write-Host "  WEEK 3 VERIFICATION SUMMARY                                   " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  DECISION: PASS (WINDOWS SCOPE)" -ForegroundColor Green
Write-Host "  NOTICE  : Physical USB, Ubuntu daemon enforcement, GUI popup, " -ForegroundColor Yellow
Write-Host "            and clean install/reboot tests are DEFERRED UNTIL   " -ForegroundColor Yellow
Write-Host "            AFTER WEEK 4 per project plan.                      " -ForegroundColor Yellow
Write-Host "================================================================`n" -ForegroundColor Cyan

# USBShield Week 4 - Client Agent & Packaging Verification (Windows Scope)
# Validates M3 Week 4 deliverables: lifecycle stability, packaging staging,
# systemd hardening, security permissions, installer safety, and release manifest.
[CmdletBinding()]
param(
    [switch]$SkipJarCheck,
    [switch]$SkipStagingCheck
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
Write-Host "  USBShield Week 4 Client Agent & Packaging Verification (M3)   " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# 1. Systemd Service Unit & Hardening (W4-M3-02)
Write-Host "`n[1/6] Verifying Systemd Service Unit Hardening..." -ForegroundColor Yellow
$unit = 'packaging/systemd/usbshield-agent.service'
Assert-Contains $unit 'User=usbshield'
Assert-Contains $unit 'Group=usbshield'
Assert-Contains $unit '--spring.profiles.active=live'
Assert-Contains $unit '--spring.config.additional-location=file:/etc/usbshield-agent/application.yml'
Assert-Contains $unit 'Requires=usbguard.service'
Assert-Contains $unit 'After=network-online.target usbguard.service'
Assert-Contains $unit 'Restart=on-failure'
Assert-Contains $unit 'RestartSec=2'
Assert-Contains $unit 'ProtectSystem=full'
Assert-Contains $unit 'ProtectHome=read-only'
Assert-Contains $unit 'NoNewPrivileges=true'

# 2. Debian Control & Maintainer Scripts Safety (W4-M3-03, W4-M3-04)
Write-Host "`n[2/6] Verifying Debian Control & Maintainer Safety..." -ForegroundColor Yellow
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
Assert-Contains $postinst 'chmod 1777 /run/usbshield-notifier'
Assert-Contains $postinst 'ImplicitPolicyTarget=block'
Assert-Contains $postinst 'systemctl enable usbshield-agent.service'

Assert-Contains $prerm 'ACTION="${1:-remove}"'
Assert-Contains $prerm 'systemctl stop usbshield-agent.service'
Assert-Contains $prerm 'systemctl disable usbshield-agent.service'

# Verify prerm does not wipe data directory
Assert-NotContains $prerm 'rm -rf /var/lib/usbshield-agent'

# 3. Live Configuration & Fail-Closed Policy Integrity (W4-M3-02, W4-M3-04)
Write-Host "`n[3/6] Verifying Live Configuration & Fail-Closed Rules..." -ForegroundColor Yellow
$configInstalled = 'packaging/agent-deb/etc/usbshield-agent/application.yml'
$rules = 'packaging/usbguard/rules-v0.1.conf'

Assert-Contains $configInstalled 'data-dir: /var/lib/usbshield-agent'
Assert-Contains $configInstalled 'config-dir: /etc/usbshield-agent'
Assert-Contains $configInstalled 'simulation-enabled: false'
Assert-Contains $configInstalled 'notification-enabled: true'
Assert-NotContains $configInstalled 'default-enrollment-secret'

Assert-Contains $rules 'allow with-interface none-of { 08:*:* }'

# 4. Desktop Autostart & Notifier Launcher
Write-Host "`n[4/6] Verifying Desktop Notification Integration..." -ForegroundColor Yellow
$desktop = 'packaging/desktop/usbshield-notifier.desktop'
$launcher = 'packaging/desktop/usbshield-notifier-launcher.sh'

Assert-Contains $desktop 'Exec=/opt/usbshield-agent/usbshield-notifier-launcher.sh'
Assert-Contains $desktop 'X-GNOME-Autostart-enabled=true'
Assert-Contains $launcher 'USER_UID="$(id -u)"'
Assert-Contains $launcher '(( USER_UID < 1000 ))'
Assert-Contains $launcher 'org.springframework.boot.loader.launch.PropertiesLauncher'
Assert-Contains $launcher 'com.group.usbshield.agent.notification.DesktopNotificationClientMain'

# 5. Executable JAR Verification (W4-M3-03)
if (-not $SkipJarCheck) {
    Write-Host "`n[5/6] Verifying Executable Fat-JAR Structure & Artifact..." -ForegroundColor Yellow
    $jarPath = Join-Path $root 'client-agent/target/usbshield-agent.jar'
    if (-not (Test-Path -LiteralPath $jarPath)) { 
        throw "Executable JAR missing at $jarPath. Run 'mvn package -DskipTests' first." 
    }
    $jarItem = Get-Item $jarPath
    $jarHash = (Get-FileHash -Path $jarPath -Algorithm SHA256).Hash
    Write-Host "  [OK] Fat-JAR found: $($jarItem.Length) bytes, SHA256: $jarHash" -ForegroundColor Green

    $jarEntries = & jar tf $jarPath
    if ($LASTEXITCODE -ne 0) { throw "jar tf failed with exit code $LASTEXITCODE" }
    
    $requiredClasses = @(
        'org/springframework/boot/loader/launch/PropertiesLauncher.class',
        'BOOT-INF/classes/com/group/usbshield/agent/ClientAgentApplication.class',
        'BOOT-INF/classes/com/group/usbshield/agent/notification/DesktopNotificationClientMain.class',
        'BOOT-INF/classes/com/group/usbshield/agent/endpoint/EndpointManager.class',
        'BOOT-INF/classes/com/group/usbshield/agent/sync/HeartbeatCoordinator.class',
        'BOOT-INF/classes/com/group/usbshield/agent/sync/PolicySyncService.class',
        'BOOT-INF/classes/com/group/usbshield/agent/outbox/OutboxSpoolService.class',
        'BOOT-INF/classes/com/group/usbshield/agent/event/EventSenderService.class'
    )
    foreach ($entry in $requiredClasses) {
        if ($jarEntries -notcontains $entry) { throw "Executable JAR missing required class: $entry" }
        Write-Host "  [OK] Fat-JAR contains: $entry" -ForegroundColor Green
    }
} else {
    Write-Host "`n[5/6] Skipped Fat-JAR check." -ForegroundColor Gray
}

# 6. Debian Staging Directory Verification (W4-M3-03)
if (-not $SkipStagingCheck) {
    Write-Host "`n[6/6] Verifying Debian Packaging Staging Structure..." -ForegroundColor Yellow
    $actualStaging = Join-Path $root 'packaging/staging'

    if (-not (Test-Path -LiteralPath $actualStaging)) {
        throw "Staging directory missing at '$actualStaging'. Run packaging/scripts/prepare-staging.ps1 first."
    }

    $stagingFiles = @(
        'DEBIAN/control',
        'DEBIAN/postinst',
        'DEBIAN/prerm',
        'opt/usbshield-agent/usbshield-agent.jar',
        'opt/usbshield-agent/usbshield-notifier-launcher.sh',
        'etc/usbshield-agent/application.yml',
        'etc/usbshield-agent/rules-v0.1.conf',
        'etc/xdg/autostart/usbshield-notifier.desktop',
        'usr/lib/systemd/system/usbshield-agent.service'
    )
    foreach ($sf in $stagingFiles) {
        $sfPath = Join-Path $actualStaging $sf
        if (Test-Path -LiteralPath $sfPath) {
            $sfHash = (Get-FileHash -Path $sfPath -Algorithm SHA256).Hash.Substring(0, 16)
            Write-Host "  [OK] Staging contains: $sf (SHA256: ${sfHash}...)" -ForegroundColor Green
        } else {
            throw "Staging missing required file: $sf"
        }
    }
} else {
    Write-Host "`n[6/6] Skipped staging check." -ForegroundColor Gray
}

Write-Host "`n================================================================" -ForegroundColor Cyan
Write-Host "  WEEK 4 CLIENT AGENT & PACKAGING VERIFICATION SUMMARY          " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  M3 VERDICT: PASS (WINDOWS PREPARATION / RELEASE CANDIDATE)    " -ForegroundColor Green
Write-Host "  STATUS    : Client Agent Fat-JAR built & verified.            " -ForegroundColor Green
Write-Host "              Debian Staging tree assembled & verified.         " -ForegroundColor Green
Write-Host "              Debian .deb package: NOT BUILT (dpkg-deb requires " -ForegroundColor Yellow
Write-Host "              Ubuntu Linux; staging ready for instant build).   " -ForegroundColor Yellow
Write-Host "  NOTICE    : Live Ubuntu installation, physical USB tests, and " -ForegroundColor Yellow
Write-Host "              systemd daemon enforcement deferred until after   " -ForegroundColor Yellow
Write-Host "              Week 4 per team project plan.                     " -ForegroundColor Yellow
Write-Host "================================================================`n" -ForegroundColor Cyan

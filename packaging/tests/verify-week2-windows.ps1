# Windows-only source/package-layout regression. No Ubuntu, sudo or USB needed.
[CmdletBinding()]
param([switch]$CheckBuiltJar)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function Assert-Contains {
    param([string]$File, [string]$Needle)
    $full = Join-Path $root $File
    if (-not (Test-Path -LiteralPath $full)) { throw "Missing file: $File" }
    $value = [System.IO.File]::ReadAllText($full)
    if (-not $value.Contains($Needle)) { throw "Missing required value in ${File}: $Needle" }
    Write-Host "PASS: $File contains required setting"
}
function Assert-NotContains {
    param([string]$File, [string]$Needle)
    $value = [System.IO.File]::ReadAllText((Join-Path $root $File))
    if ($value.Contains($Needle)) { throw "Unexpected unsafe/default value in ${File}: $Needle" }
}

$unit = 'packaging/systemd/usbshield-agent.service'
$launcher = 'packaging/desktop/usbshield-notifier-launcher.sh'
$installer = 'packaging/scripts/build-deb.sh'
$config = 'packaging/agent-deb/etc/usbshield-agent/application.yml'

Assert-Contains $unit '--spring.profiles.active=live'
Assert-Contains $unit '--spring.config.additional-location=file:/etc/usbshield-agent/application.yml'
Assert-Contains $unit 'User=usbshield'
Assert-Contains $installer 'agent-deb/etc/usbshield-agent/application.yml'
Assert-NotContains $installer 'cp "$ROOT_DIR/client-agent/src/main/resources/application.yml"'
Assert-Contains $installer 'usbshield-notifier-launcher.sh'
Assert-Contains $config 'data-dir: /var/lib/usbshield-agent'
Assert-Contains $config 'config-dir: /etc/usbshield-agent'
Assert-Contains $config 'simulation-enabled: false'
Assert-NotContains $config 'default-enrollment-secret'
Assert-Contains $launcher 'org.springframework.boot.loader.launch.PropertiesLauncher'
Assert-Contains $launcher 'com.group.usbshield.agent.notification.DesktopNotificationClientMain'
Assert-Contains $launcher '-Dloader.home=/opt/usbshield-agent'
Assert-NotContains $launcher 'exec /usr/bin/java -cp "$JAR_PATH" com.group.usbshield.agent.notification.DesktopNotificationClientMain'

if ($CheckBuiltJar) {
    $jarPath = Join-Path $root 'client-agent/target/usbshield-agent.jar'
    if (-not (Test-Path -LiteralPath $jarPath)) { throw "Build client-agent JAR first: $jarPath" }
    $jarEntries = & jar tf $jarPath
    if ($LASTEXITCODE -ne 0) { throw "jar tf failed with exit $LASTEXITCODE" }
    $expected = @(
        'org/springframework/boot/loader/launch/PropertiesLauncher.class',
        'BOOT-INF/classes/com/group/usbshield/agent/notification/DesktopNotificationClientMain.class'
    )
    foreach ($entry in $expected) {
        if ($jarEntries -notcontains $entry) { throw "Executable JAR missing: $entry" }
        Write-Host "PASS: executable JAR contains $entry"
    }
}

Write-Host 'PASS: Windows-only Week 2 packaging static checks.'

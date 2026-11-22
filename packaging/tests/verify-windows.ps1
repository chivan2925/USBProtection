#requires -Version 7.6
<#
.SYNOPSIS
    Canonical USBShield Windows verification: executable Admin JAR, UI/API,
    source contracts and (unless -AdminOnly) Agent + Windows-built DEB.
.DESCRIPTION
    The Admin runtime gate reuses the existing live browser/HTTP suites on
    PostgreSQL usbshield_browser_test with random temporary schemas.
    It deliberately does not import the historical Week 3 acceptance script.
    No ddl-auto=create, no prod-DB reset and no Ubuntu E2E claim.
.EXAMPLE
    .\packaging\tests\verify-windows.ps1
.EXAMPLE
    .\packaging\tests\verify-windows.ps1 -AdminOnly
.EXAMPLE
    .\packaging\tests\verify-windows.ps1 -SkipBrowserLive
#>
[CmdletBinding()]
param(
    [switch]$SkipTests,
    [switch]$SkipBrowserLive,
    [switch]$SkipDebBuild,
    [switch]$AdminOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
if (-not $IsWindows -or $PSVersionTable.PSEdition -ne 'Core') {
    throw 'Run with PowerShell 7.6+ on Windows.'
}
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$serverJar = Join-Path $root 'admin-server/target/usbshield-admin-server.jar'
$builder = Join-Path $root 'packaging/scripts/build-deb.ps1'
$debManifest = Join-Path $root 'packaging/dist/usbshield-agent.release.json'
$phase = 'startup'
$results = [System.Collections.Generic.List[string]]::new()
$savedTestVars = @{}
$savedProdVars = @{}

function Set-Phase([string]$Name) {
    $script:phase = $Name
    Write-Host "`n=== $Name ===" -ForegroundColor Cyan
    $script:results.Add($Name)
}
function Assert-True([bool]$Condition, [string]$Description) {
    if (-not $Condition) { throw "FAIL: $Description" }
    Write-Host "[OK] $Description" -ForegroundColor Green
}
function Require-Command([string[]]$Names) {
    foreach ($name in $Names) {
        $command = Get-Command -Name $name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $command) { return $command.Source }
    }
    throw "Required executable missing: $($Names -join ', ')"
}
function Invoke-Checked([string]$Name, [string]$Executable, [string[]]$Arguments) {
    Set-Phase $Name
    Push-Location -LiteralPath $root
    try {
        $global:LASTEXITCODE = 0
        & $Executable @Arguments
        if ($LASTEXITCODE -ne 0) { throw "$Name returned exit code $LASTEXITCODE" }
    } finally { Pop-Location }
}
function Source-Contains([string]$Relative, [string]$Fragment) {
    $path = Join-Path $root $Relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing required source: $Relative" }
    if (-not [System.IO.File]::ReadAllText($path).Contains($Fragment)) {
        throw "Source contract $Relative missing expected fragment: $Fragment"
    }
}
function Protect-ProductionDb() {
    # Tests must never inherit a prod datasource through a PowerShell shell.
    foreach ($item in (Get-ChildItem Env:)) {
        if ($item.Name -match '^(USBSHIELD_DB_|SPRING_DATASOURCE_|SPRING_FLYWAY_|SPRING_JPA_|SPRING_SQL_|SPRING_PROFILES_ACTIVE$|SPRING_CONFIG_|SPRING_APPLICATION_JSON$|FLYWAY_)') {
            $script:savedProdVars[$item.Name] = $item.Value
            [System.Environment]::SetEnvironmentVariable($item.Name, $null, 'Process')
        }
    }
}
function Import-TestDbSettings() {
    $file = Join-Path $root '.env'
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw 'Missing untracked .env with PostgreSQL test-DB settings.'
    }
    foreach ($name in @('USBSHIELD_TEST_DB_HOST','USBSHIELD_TEST_DB_PORT','USBSHIELD_TEST_DB_USERNAME','USBSHIELD_TEST_DB_PASSWORD')) {
        $script:savedTestVars[$name] = [System.Environment]::GetEnvironmentVariable($name, 'Process')
    }
    $lineNumber = 0
    foreach ($line in [System.IO.File]::ReadAllLines($file)) {
        $lineNumber++
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        if ($trimmed -notmatch '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            throw "Malformed test .env at line $lineNumber"
        }
        $key = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        # Do not import application credentials from .env (test only).
        if ($script:savedTestVars.ContainsKey($key)) {
            [System.Environment]::SetEnvironmentVariable($key, $value, 'Process')
        }
    }
    Assert-True ($env:USBSHIELD_TEST_DB_USERNAME -ceq 'usbshield_test') 'Isolated test DB role is usbshield_test'
    Assert-True (-not [string]::IsNullOrWhiteSpace($env:USBSHIELD_TEST_DB_PASSWORD) -and
        $env:USBSHIELD_TEST_DB_PASSWORD -cne 'REPLACE_WITH_YOUR_TEST_PASSWORD') 'Test DB password provided'
    $hostName = if ($env:USBSHIELD_TEST_DB_HOST) { $env:USBSHIELD_TEST_DB_HOST } else { '127.0.0.1' }
    $port = if ($env:USBSHIELD_TEST_DB_PORT) { $env:USBSHIELD_TEST_DB_PORT } else { '5432' }
    Assert-True ($hostName -match '^[A-Za-z0-9_.-]+$' -and $port -match '^\d{1,5}$' -and
        [int]$port -ge 1 -and [int]$port -le 65535) 'Test PostgreSQL host and port format'
}
function Assert-JarContents([string]$JarPath, [string]$JarTool) {
    Assert-True (Test-Path -LiteralPath $JarPath -PathType Leaf) 'Admin executable JAR exists'
    $global:LASTEXITCODE = 0
    $items = @(& $JarTool tf $JarPath)
    if ($LASTEXITCODE -ne 0) { throw 'jar tf failed' }
    foreach ($required in @(
        'org/springframework/boot/loader/launch/JarLauncher.class',
        'BOOT-INF/classes/com/group/usbshield/server/UsbShieldServerApplication.class',
        'BOOT-INF/classes/static/index.html',
        'BOOT-INF/classes/db/migration/V1__initial_postgresql_schema.sql',
        'BOOT-INF/classes/db/migration/V2__admin_session_and_usbguard_health.sql'
    )) {
        Assert-True ($items -ccontains $required) "JAR includes $required"
    }
    $distDir = Join-Path $root 'frontend/dist'
    Assert-True (Test-Path (Join-Path $distDir 'index.html')) 'Frontend production index exists'
    $distAssets = @(Get-ChildItem -LiteralPath (Join-Path $distDir 'assets') -File -ErrorAction Stop)
    Assert-True ($distAssets.Count -ge 2) 'Frontend assets generated'
    foreach ($asset in $distAssets) {
        Assert-True ($items -ccontains "BOOT-INF/classes/static/assets/$($asset.Name)") "JAR embeds $($asset.Name)"
    }
}
function Get-ArEntries([string]$File) {
    $entries = @{}
    $reader = [System.IO.BinaryReader]::new([System.IO.File]::OpenRead($File))
    try {
        $magic = [System.Text.Encoding]::ASCII.GetString($reader.ReadBytes(8))
        if ($magic -cne "!<arch>`n") { throw 'Invalid Debian ar header' }
        while ($reader.BaseStream.Position -lt $reader.BaseStream.Length) {
            $head = $reader.ReadBytes(60)
            if ($head.Length -ne 60) { throw 'Truncated .deb ar header' }
            $header = [System.Text.Encoding]::ASCII.GetString($head)
            if (-not $header.EndsWith('`' + "`n")) { throw 'Invalid ar record terminator' }
            $name = $header.Substring(0, 16).Trim().TrimEnd('/')
            [long]$size = 0
            if (-not [long]::TryParse($header.Substring(48, 10).Trim(), [ref]$size) -or $size -lt 0 -or $size -gt 512MB) {
                throw 'Invalid ar member size'
            }
            if ($entries.ContainsKey($name)) { throw 'Duplicate ar entry' }
            $bytes = $reader.ReadBytes([int]$size)
            if ($bytes.Length -ne $size) { throw 'Truncated ar entry' }
            $entries[$name] = $bytes
            if ($size % 2 -ne 0 -and $reader.ReadByte() -ne 10) { throw 'Bad ar alignment padding' }
        }
    } finally { $reader.Dispose() }
    $memberNames = @($entries.Keys | Sort-Object)
    Assert-True (($memberNames -join ',') -ceq 'control.tar.gz,data.tar.gz,debian-binary') 'DEB required archive members'
    Assert-True ([System.Text.Encoding]::ASCII.GetString($entries['debian-binary']) -ceq "2.0`n") 'Debian format 2.0'
    return $entries
}
function Get-TarEntries([byte[]]$Compressed) {
    $files = @{}
    $memory = [System.IO.MemoryStream]::new($Compressed, $false)
    $gzip = [System.IO.Compression.GZipStream]::new($memory, [System.IO.Compression.CompressionMode]::Decompress)
    $tar = [System.Formats.Tar.TarReader]::new($gzip)
    try {
        while ($null -ne ($entry = $tar.GetNextEntry())) {
            if ($entry.EntryType -eq [System.Formats.Tar.TarEntryType]::Directory) { continue }
            $path = $entry.Name -replace '^\./', ''
            if ($path -match '(^/|^\.\.|/\.\./|\\|^[A-Za-z]:)' -or $files.ContainsKey($path)) {
                throw "Unsafe or duplicate tar path: $path"
            }
            if ($entry.Uid -ne 0 -or $entry.Gid -ne 0) { throw "Non-root package ownership: $path" }
            $content = [System.IO.MemoryStream]::new()
            if ($null -eq $entry.DataStream) { throw "Missing tar content: $path" }
            $entry.DataStream.CopyTo($content)
            $files[$path] = [pscustomobject]@{
                Bytes = $content.ToArray()
                Mode = ([int]$entry.Mode -band 0x0FFF)
            }
            $content.Dispose()
        }
    } finally { $tar.Dispose(); $gzip.Dispose(); $memory.Dispose() }
    return $files
}
function Expected-DebBytes([string]$Source, [bool]$Text, [string]$Version) {
    $path = Join-Path $root $Source
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing DEB input: $Source" }
    if (-not $Text) { return ,([System.IO.File]::ReadAllBytes($path)) }
    $value = [System.IO.File]::ReadAllText($path).Replace("`r`n", "`n").Replace("`r", "`n")
    if ($Source -eq 'packaging/agent-deb/DEBIAN/control') {
        $value = $value.Replace('Version: 1.0.0', "Version: $Version")
    }
    return ,([System.Text.UTF8Encoding]::new($false).GetBytes($value))
}
function Assert-DebSource([hashtable]$Archive, [string]$Entry, [string]$Source, [string]$Mode,
                           [bool]$IsText, [string]$Version) {
    Assert-True ($Archive.ContainsKey($Entry)) "Archive has $Entry"
    Assert-True ($Archive[$Entry].Mode -eq [Convert]::ToInt32($Mode, 8)) "Archive mode $Entry = $Mode"
    $expected = Expected-DebBytes $Source $IsText $Version
    $actual = $Archive[$Entry].Bytes
    Assert-True ($actual.Length -eq $expected.Length -and
        [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($actual)) -ceq
        [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($expected))) "Archive payload matches source $Entry"
}
function Assert-DebPackage() {
    Set-Phase 'Inspect Windows-built Agent DEB'
    Assert-True (Test-Path -LiteralPath $debManifest -PathType Leaf) 'Agent release manifest exists'
    $manifest = Get-Content -LiteralPath $debManifest -Raw | ConvertFrom-Json
    Assert-True ($manifest.version -match '^1\.0\.0\+build[0-9]{17}$') 'Agent release version format'
    Assert-True ($manifest.filename -ceq "usbshield-agent_$($manifest.version)_amd64.deb" -and
        $manifest.filename -notmatch '[/\\]') 'Agent release filename is safe and matches version'
    $deb = Join-Path $root "packaging/dist/$($manifest.filename)"
    Assert-True (Test-Path -LiteralPath $deb -PathType Leaf) 'Agent .deb exists'
    Assert-True ((Get-FileHash -LiteralPath $deb -Algorithm SHA256).Hash.ToLowerInvariant() -ceq
        ([string]$manifest.sha256).ToLowerInvariant()) 'Agent .deb SHA-256 matches manifest'
    $git = Require-Command @('git.exe','git')
    $head = (& $git -C $root rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to determine Git HEAD' }
    Assert-True ($manifest.gitHead -ceq $head) 'Agent manifest commit matches checkout'
    $ar = Get-ArEntries $deb
    $control = Get-TarEntries $ar['control.tar.gz']
    $data = Get-TarEntries $ar['data.tar.gz']
    # Keep the original verifier's exact-content and mode guarantees.
    $controlExpected = @(
        @('control','packaging/agent-deb/DEBIAN/control','0644',$true),
        @('postinst','packaging/agent-deb/DEBIAN/postinst','0755',$true),
        @('prerm','packaging/agent-deb/DEBIAN/prerm','0755',$true)
    )
    $dataExpected = @(
        @('opt/usbshield-agent/usbshield-agent.jar','client-agent/target/usbshield-agent.jar','0755',$false),
        @('opt/usbshield-agent/usbshield-notifier-launcher.sh','packaging/desktop/usbshield-notifier-launcher.sh','0755',$true),
        @('etc/usbshield-agent/application.yml','packaging/agent-deb/etc/usbshield-agent/application.yml','0640',$true),
        @('etc/usbshield-agent/rules-v0.1.conf','packaging/usbguard/rules-v0.1.conf','0644',$true),
        @('usr/lib/systemd/system/usbshield-agent.service','packaging/systemd/usbshield-agent.service','0644',$true),
        @('etc/xdg/autostart/usbshield-notifier.desktop','packaging/desktop/usbshield-notifier.desktop','0644',$true)
    )
    Assert-True ($control.Count -eq $controlExpected.Count + 1) 'Control archive has exact expected number of entries'
    Assert-True ($data.Count -eq $dataExpected.Count) 'Data archive has exact expected number of entries'
    foreach ($item in $controlExpected) {
        Assert-DebSource $control $item[0] $item[1] $item[2] $item[3] $manifest.version
    }
    foreach ($item in $dataExpected) {
        Assert-DebSource $data $item[0] $item[1] $item[2] $item[3] $manifest.version
    }
    foreach ($path in @('control', 'postinst', 'prerm', 'md5sums')) {
        Assert-True ($control.ContainsKey($path)) "Control archive contains $path"
    }
    foreach ($path in @(
        'opt/usbshield-agent/usbshield-agent.jar',
        'etc/usbshield-agent/application.yml',
        'etc/usbshield-agent/rules-v0.1.conf',
        'usr/lib/systemd/system/usbshield-agent.service',
        'etc/xdg/autostart/usbshield-notifier.desktop'
    )) {
        Assert-True ($data.ContainsKey($path)) "Data archive contains $path"
    }
    foreach ($path in @('postinst','prerm')) {
        Assert-True ($control[$path].Mode -eq [Convert]::ToInt32('0755', 8)) "$path is executable"
    }
    $md5Map = @{}
    foreach ($line in ([System.Text.Encoding]::UTF8.GetString($control['md5sums'].Bytes) -split "`n")) {
        if (-not $line.Trim()) { continue }
        if ($line -notmatch '^([a-fA-F0-9]{32})  (.+)$') { throw 'Malformed package md5sums' }
        $md5Map[$Matches[2]] = $Matches[1].ToLowerInvariant()
    }
    Assert-True ($md5Map.Count -eq $data.Count) 'DEB md5sums covers all data files'
    foreach ($path in $data.Keys) {
        $actual = [Convert]::ToHexString([System.Security.Cryptography.MD5]::HashData($data[$path].Bytes)).ToLowerInvariant()
        Assert-True ($md5Map.ContainsKey($path) -and $md5Map[$path] -ceq $actual) "DEB integrity: $path"
    }
}

try {
    Set-Phase 'Windows prerequisites and source checks'
    Assert-True ('System.Formats.Tar.TarReader' -as [type] -ne $null) 'PowerShell TarReader available'
    $git = Require-Command @('git.exe','git')
    $mvn = Require-Command @('mvn.cmd','mvn')
    $npm = Require-Command @('npm.cmd','npm')
    $jar = Require-Command @('jar.exe','jar')
    $java = Require-Command @('java.exe','java')
    $global:LASTEXITCODE = 0
    & $java -version
    if ($LASTEXITCODE -ne 0) { throw 'Java runtime failed.' }
    Invoke-Checked 'Unstaged whitespace check' $git @('-c','core.safecrlf=false','diff','--check')
    Invoke-Checked 'Staged whitespace check' $git @('-c','core.safecrlf=false','diff','--cached','--check')
    foreach ($private in @('.env','.env.e2e.local')) {
        $global:LASTEXITCODE = 0
        $tracked = @(& $git -C $root ls-files -- $private)
        if ($LASTEXITCODE -ne 0) { throw 'Git ls-files failed.' }
        Assert-True ($tracked.Count -eq 0) "$private is not committed"
    }
    Source-Contains 'admin-server/src/main/resources/application.yml' 'ddl-auto: validate'
    Source-Contains 'admin-server/src/main/resources/application.yml' 'baseline-on-migrate: false'
    Source-Contains 'admin-server/src/main/resources/application-prod.yml' 'USBSHIELD_DB_URL'
    Source-Contains 'frontend/tests/postgresTestDb.mjs' 'usbshield_browser_test'
    Source-Contains 'frontend/tests/postgresTestDb.mjs' '--spring.jpa.hibernate.ddl-auto=validate'
    Source-Contains 'frontend/tests/postgresTestDb.mjs' 'cleanupPostgresBrowserSchema'
    if (-not $AdminOnly) {
        $contracts = @{
            'packaging/systemd/usbshield-agent.service' = @(
                'User=usbshield', 'Group=usbshield', 'Requires=usbguard.service',
                'After=network-online.target usbguard.service', 'Restart=on-failure',
                'ProtectSystem=full', 'ProtectHome=read-only', 'NoNewPrivileges=true',
                '--spring.profiles.active=live', 'EnvironmentFile=-/etc/usbshield-agent/agent.env'
            )
            'packaging/agent-deb/DEBIAN/control' = @(
                'Package: usbshield-agent', 'Version: 1.0.0', 'Architecture: amd64',
                'openjdk-21-jre-headless', 'usbguard', 'libnotify-bin', 'acl'
            )
            'packaging/agent-deb/DEBIAN/postinst' = @(
                'groupadd --system usbshield', 'useradd --system --gid usbshield',
                'chmod 750 /etc/usbshield-agent', 'chmod 700 /var/lib/usbshield-agent',
                'ImplicitPolicyTarget=block', 'IPCAccessControlFiles=',
                'Devices=list,listen', 'Policy=list,modify', 'Parameters=list',
                'runuser -u usbshield -- usbguard list-rules',
                'runuser -u usbshield -- usbguard list-devices',
                'systemctl enable usbshield-agent.service'
            )
            'packaging/agent-deb/DEBIAN/prerm' = @(
                'ACTION="${1:-remove}"', 'systemctl stop usbshield-agent.service',
                'systemctl disable usbshield-agent.service'
            )
            'packaging/agent-deb/etc/usbshield-agent/application.yml' = @(
                'data-dir: /var/lib/usbshield-agent', 'config-dir: /etc/usbshield-agent',
                'require-https: true', 'simulation-enabled: false',
                'notification-enabled: true', 'server-url: ${USBSHIELD_SERVER_URL:}'
            )
            'packaging/usbguard/rules-v0.1.conf' = @('allow with-interface none-of { 08:*:* }')
            'packaging/desktop/usbshield-notifier.desktop' = @(
                'Exec=/opt/usbshield-agent/usbshield-notifier-launcher.sh',
                'X-GNOME-Autostart-enabled=true'
            )
            'packaging/desktop/usbshield-notifier-launcher.sh' = @(
                '#!/usr/bin/env bash', 'XDG_RUNTIME_DIR',
                'org.springframework.boot.loader.launch.PropertiesLauncher',
                'com.group.usbshield.agent.notification.DesktopNotificationClientMain',
                '-Dloader.home=/opt/usbshield-agent'
            )
            'client-agent/src/main/java/com/group/usbshield/agent/notification/NotifierSocketPaths.java' = @('notice.sock')
            'client-agent/src/main/java/com/group/usbshield/agent/notification/DesktopNotificationClient.java' = @('u:usbshield:rw-')
            'client-agent/src/main/java/com/group/usbshield/agent/notification/UnixSocketNotificationPublisher.java' = @('SO_PEERCRED')
            'client-agent/src/main/resources/application-live.yml' = @(
                'simulation-enabled: false', '/var/lib/usbshield-agent'
            )
        }
        foreach ($relative in $contracts.Keys) {
            foreach ($fragment in $contracts[$relative]) { Source-Contains $relative $fragment }
        }
        foreach ($forbidden in @(
            @('packaging/agent-deb/DEBIAN/postinst', 'chmod 1777 /run/usbshield-notifier'),
            @('packaging/agent-deb/DEBIAN/postinst', 'usermod -aG usbguard usbshield'),
            @('packaging/agent-deb/DEBIAN/prerm', 'rm -rf /var/lib/usbshield-agent'),
            @('packaging/agent-deb/etc/usbshield-agent/application.yml', 'default-enrollment-secret')
        )) {
            $text = [System.IO.File]::ReadAllText((Join-Path $root $forbidden[0]))
            Assert-True (-not $text.Contains($forbidden[1])) "Forbidden source fragment absent in $($forbidden[0])"
        }
    }
    foreach ($scriptPath in @('admin-server/build-release.ps1','packaging/tests/verify-windows.ps1')) {
        $tokens = $null; $parseErrors = $null
        [void][System.Management.Automation.Language.Parser]::ParseFile(
            (Join-Path $root $scriptPath), [ref]$tokens, [ref]$parseErrors)
        Assert-True ($parseErrors.Count -eq 0) "$scriptPath has valid PowerShell syntax"
    }

    if (-not $SkipTests) {
        Protect-ProductionDb
        Import-TestDbSettings
        Invoke-Checked 'Admin Maven tests (isolated PostgreSQL)' $mvn @('-f','admin-server/pom.xml','clean','test')
        if (-not $AdminOnly) {
            Invoke-Checked 'Client Agent Maven tests (Windows applicable)' $mvn @('-f','client-agent/pom.xml','clean','test')
        }
        Invoke-Checked 'Frontend npm ci' $npm @('--prefix','frontend','ci')
        Invoke-Checked 'Frontend test' $npm @('--prefix','frontend','test')
        Invoke-Checked 'Frontend lint' $npm @('--prefix','frontend','run','lint')
        Invoke-Checked 'Frontend production build' $npm @('--prefix','frontend','run','build')
        # Maven clean test removes target/. Build after tests and before live suites.
        Invoke-Checked 'Package current Admin JAR + React' $mvn @(
            '-f','admin-server/pom.xml','-Pwith-frontend','-DskipTests','package')
    } else {
        Write-Warning 'Unit/build tests skipped. Only inspecting existing artifacts.'
    }

    Set-Phase 'Verify packaged Admin Server executable + React + Flyway'
    Assert-JarContents $serverJar $jar

    if (-not $SkipTests -and -not $SkipBrowserLive) {
        # Existing harness: unique schema in usbshield_browser_test, Flyway migrate,
        # Hibernate validate, API/browser assertions and guarded cleanup.
        Invoke-Checked 'Browser-live: actual Admin Server and React UI' $npm @('--prefix','frontend','run','test:browser:live')
        Invoke-Checked 'HTTP-live: Admin Server integration' $npm @('--prefix','frontend','run','test:live')
    } else {
        Write-Warning 'Live backend/browser execution skipped; runnable Admin Server not fully verified.'
    }

    if (-not $AdminOnly) {
        if (-not $SkipDebBuild) {
            $pwsh = Join-Path $PSHOME 'pwsh.exe'
            Invoke-Checked 'Build Windows-native Agent .deb' $pwsh @(
                '-NoProfile','-NonInteractive','-File',$builder)
        } else {
            Write-Warning 'DEB build skipped; inspecting existing package only.'
        }
        Assert-DebPackage
    } else {
        Write-Host '[SKIP] Agent/DEB packaging: -AdminOnly' -ForegroundColor Yellow
    }

    Write-Host "`nRESULT: PASS for executed Windows verification gates." -ForegroundColor Green
    Write-Host "Admin JAR: $serverJar"
    if (-not $AdminOnly) { Write-Host "Agent manifest: $debManifest" }
    Write-Host 'Windows tests do not replace HTTPS/LAN or native Ubuntu USBGuard E2E.' -ForegroundColor Yellow
    if ($SkipTests -or $SkipBrowserLive -or $SkipDebBuild) {
        Write-Warning 'One or more gates were skipped. Do not present this as a full Windows PASS.'
    }
} catch {
    [Console]::Error.WriteLine("FAILED [$phase]: $($_.Exception.Message)")
    exit 1
} finally {
    foreach ($key in $savedTestVars.Keys) {
        [System.Environment]::SetEnvironmentVariable($key, $savedTestVars[$key], 'Process')
    }
    foreach ($key in $savedProdVars.Keys) {
        [System.Environment]::SetEnvironmentVariable($key, $savedProdVars[$key], 'Process')
    }
}

#requires -Version 7.4
<#
.SYNOPSIS
    One Windows verification entrypoint for USBShield (M1-M5 + Ubuntu Agent packaging).
.DESCRIPTION
    Default: check source/security contracts, run Admin/Agent Maven tests,
    frontend tests/lint/build, browser-live suites, build the Admin JAR and
    native Windows .deb, then verify the actual Debian archive content/modes.

    This consolidates the live Week 2-4 Windows packaging checks. It intentionally
    does NOT claim Ubuntu USBGuard, GUI popup, physical USB, or real LAN E2E PASS.

    Prerequisites: Windows, PowerShell 7.4+, JDK 21, Maven, Node/npm, PostgreSQL
    test DBs and an untracked repo-root .env (see .env.example); build-deb.ps1.
.EXAMPLE
    .\packaging\tests\verify-windows.ps1
.EXAMPLE
    .\packaging\tests\verify-windows.ps1 -SkipBrowserLive
.EXAMPLE
    .\packaging\tests\verify-windows.ps1 -SkipTests -SkipDebBuild
#>
[CmdletBinding()]
param(
    [switch]$SkipTests,
    [switch]$SkipBrowserLive,
    [switch]$SkipDebBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $IsWindows) { throw 'This verifier must run in PowerShell 7.4+ on Windows.' }
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$packagePath = Join-Path $root 'packaging/dist/usbshield-agent_1.0.0_amd64.deb'
$builderPath = Join-Path $root 'packaging/scripts/build-deb.ps1'
$steps = [System.Collections.Generic.List[string]]::new()

function Show-Phase([string]$Name) {
    Write-Host "`n=== $Name ===" -ForegroundColor Cyan
    $steps.Add($Name)
}
function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAIL: $Message" }
    Write-Host "[OK] $Message" -ForegroundColor Green
}
function Get-SourceText([string]$Relative) {
    $file = Join-Path $root $Relative
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing source: $Relative" }
    return [System.IO.File]::ReadAllText($file)
}
function Assert-Text([string]$Relative, [string]$Required, [bool]$Present = $true) {
    $found = (Get-SourceText $Relative).Contains($Required)
    if ($found -ne $Present) {
        $what = if ($Present) { 'does not contain' } else { 'unexpectedly contains' }
        throw "FAIL: $Relative $what expected fragment: $Required"
    }
}
function Get-RequiredCommand([string[]]$Names) {
    foreach ($name in $Names) {
        $command = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $command) { return $command.Source }
    }
    throw "Required command is missing: $($Names -join ' / ')"
}
function Invoke-Checked([string]$Name, [string]$Executable, [string[]]$Arguments, [string]$WorkDir = $root) {
    Show-Phase $Name
    Push-Location -LiteralPath $WorkDir
    try {
        $global:LASTEXITCODE = 0
        & $Executable @Arguments
        if ($LASTEXITCODE -ne 0) { throw "$Name failed: exit code $LASTEXITCODE" }
    } finally { Pop-Location }
}
function Get-NormalizedBytes([string]$Relative, [bool]$Text) {
    $file = Join-Path $root $Relative
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing package input: $Relative" }
    if ($Text) {
        $value = [System.IO.File]::ReadAllText($file).Replace("`r`n", "`n").Replace("`r", "`n")
        return ,([System.Text.UTF8Encoding]::new($false).GetBytes($value))
    }
    return ,([System.IO.File]::ReadAllBytes($file))
}
function Compare-Bytes([byte[]]$Actual, [byte[]]$Expected) {
    if ($Actual.Length -ne $Expected.Length) { return $false }
    $actualHash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($Actual))
    $expectedHash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($Expected))
    return $actualHash -ceq $expectedHash
}
function Get-ArMembers([string]$File) {
    $members = [ordered]@{}
    $reader = [System.IO.BinaryReader]::new([System.IO.File]::OpenRead($File))
    try {
        $ascii = [System.Text.Encoding]::ASCII
        $magic = $reader.ReadBytes(8)
        Assert-True (Compare-Bytes $magic ($ascii.GetBytes("!<arch>`n"))) 'Debian ar magic is valid'
        while ($reader.BaseStream.Position -lt $reader.BaseStream.Length) {
            $header = $reader.ReadBytes(60)
            if ($header.Length -ne 60) { throw 'Truncated ar member header' }
            $line = $ascii.GetString($header)
            if (-not $line.EndsWith('`' + "`n")) { throw 'Invalid ar member terminator' }
            $memberName = $line.Substring(0, 16).Trim().TrimEnd('/')
            $lengthText = $line.Substring(48, 10).Trim()
            [long]$length = 0
            if (-not [long]::TryParse($lengthText, [ref]$length) -or $length -lt 0 -or $length -gt 512MB) {
                throw "Invalid or oversized ar member: $memberName"
            }
            if ($members.Contains($memberName)) { throw "Duplicate ar member: $memberName" }
            $body = $reader.ReadBytes([int]$length)
            if ($body.Length -ne $length) { throw "Truncated ar member: $memberName" }
            $members[$memberName] = $body
            if (($length % 2) -ne 0) {
                if ($reader.ReadByte() -ne 10) { throw 'Invalid ar alignment padding' }
            }
        }
    } finally { $reader.Dispose() }
    $expectedNames = @('debian-binary','control.tar.gz','data.tar.gz')
    Assert-True (($members.Keys -join ',') -eq ($expectedNames -join ',')) 'Debian ar members are complete and ordered'
    Assert-True (Compare-Bytes $members['debian-binary'] ([System.Text.Encoding]::ASCII.GetBytes("2.0`n"))) 'Debian format version is 2.0'
    return $members
}
function Get-TarFiles([byte[]]$Compressed, [string]$Name) {
    $result = @{}
    $memory = [System.IO.MemoryStream]::new($Compressed, $false)
    $gzip = [System.IO.Compression.GZipStream]::new($memory, [System.IO.Compression.CompressionMode]::Decompress)
    $tar = [System.Formats.Tar.TarReader]::new($gzip)
    try {
        while ($null -ne ($entry = $tar.GetNextEntry())) {
            $path = $entry.Name -replace '^\./', ''
            if ($entry.EntryType -eq [System.Formats.Tar.TarEntryType]::Directory) { continue }
            if ($entry.EntryType -notin @(
                    [System.Formats.Tar.TarEntryType]::RegularFile,
                    [System.Formats.Tar.TarEntryType]::V7RegularFile)) {
                throw "Unexpected tar entry type ($Name): $path / $($entry.EntryType)"
            }
            if ($path -match '(^/|^\.\.|/\.\./|\\|^[A-Za-z]:)') { throw "Unsafe tar path: $path" }
            if ($result.ContainsKey($path)) { throw "Duplicate tar entry: $path" }
            if ($entry.Uid -ne 0 -or $entry.Gid -ne 0) { throw "Non-root package ownership: $path" }
            $out = [System.IO.MemoryStream]::new()
            if ($null -eq $entry.DataStream) { throw "Missing tar payload: $path" }
            $entry.DataStream.CopyTo($out)
            $result[$path] = [pscustomobject]@{
                Bytes = $out.ToArray()
                Mode = ([int]$entry.Mode -band 0x0FFF)
            }
            $out.Dispose()
        }
    } finally {
        $tar.Dispose(); $gzip.Dispose(); $memory.Dispose()
    }
    return $result
}
function Assert-PackageEntries([hashtable]$Entries, [array]$Manifest, [string]$ArchiveName, [string[]]$AdditionalPaths = @()) {
    $expectedPaths = @((@($Manifest | ForEach-Object { $_.Path }) + @($AdditionalPaths)) | Sort-Object)
    $actualPaths = @($Entries.Keys | Sort-Object)
    Assert-True (($actualPaths -join '|') -eq ($expectedPaths -join '|')) "$ArchiveName file list exactly matches release manifest"
    foreach ($item in $Manifest) {
        $entry = $Entries[$item.Path]
        $expectedMode = [Convert]::ToInt32($item.Mode, 8)
        Assert-True ($entry.Mode -eq $expectedMode) "$ArchiveName/$($item.Path) Unix mode $($item.Mode)"
        $expectedBytes = Get-NormalizedBytes $item.Source ([bool]$item.Text)
        Assert-True (Compare-Bytes $entry.Bytes $expectedBytes) "$ArchiveName/$($item.Path) matches source bytes"
    }
}

try {
    Show-Phase 'Host, source and accidental-secret safeguards'
    Assert-True (Test-Path -LiteralPath (Join-Path $root 'client-agent/pom.xml')) 'Repository layout'
    Assert-True (Test-Path -LiteralPath $builderPath -PathType Leaf) 'Windows-native DEB builder exists'
    $builder = Get-SourceText 'packaging/scripts/build-deb.ps1'
    foreach ($fragment in @('System.Formats.Tar.TarWriter', 'control.tar.gz', 'data.tar.gz', 'debian-binary', 'root')) {
        Assert-True ($builder.Contains($fragment)) "Native DEB builder declares $fragment"
    }
    $git = Get-RequiredCommand @('git.exe', 'git')
    Invoke-Checked -Name 'Git unstaged whitespace validation' -Executable $git -Arguments @('diff','--check')
    Invoke-Checked -Name 'Git staged whitespace validation' -Executable $git -Arguments @('diff','--cached','--check')
    foreach ($secret in @('.env', '.env.e2e.local')) {
        $global:LASTEXITCODE = 0
        $tracked = @(& $git -C $root ls-files -- $secret)
        if ($LASTEXITCODE -ne 0) { throw "git ls-files failed: $secret" }
        Assert-True ($tracked.Count -eq 0) "$secret is not tracked"
    }
    $gitignore = Get-SourceText '.gitignore'
    Assert-True ($gitignore.Contains('.env') -and $gitignore.Contains('packaging/staging/') -and $gitignore.Contains('packaging/dist/')) 'Private env and build outputs are gitignored'

    Show-Phase 'Consolidated Week 2-4 packaging and safety contracts'
    $contracts = @{
        'packaging/systemd/usbshield-agent.service' = @(
            'User=usbshield', 'Group=usbshield', 'Requires=usbguard.service',
            'After=network-online.target usbguard.service', 'Restart=on-failure',
            'ProtectSystem=full', 'ProtectHome=read-only', 'NoNewPrivileges=true',
            '--spring.profiles.active=live',
            '--spring.config.additional-location=file:/etc/usbshield-agent/application.yml',
            'EnvironmentFile=-/etc/usbshield-agent/agent.env'
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
            '#!/usr/bin/env bash', 'XDG_RUNTIME_DIR', 'EXPECTED_RUNTIME="/run/user/${USER_UID}"',
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
        'admin-server/src/main/resources/application-prod.yml' = @(
            'USBSHIELD_DB_URL', 'USBSHIELD_BIND_ADDRESS:127.0.0.1',
            'USBSHIELD_COOKIE_SECURE:true'
        )
        'admin-server/src/main/resources/application.yml' = @(
            'baseline-on-migrate: false', 'ddl-auto: validate', 'clean-disabled: true'
        )
    }
    foreach ($file in ($contracts.Keys | Sort-Object)) {
        foreach ($fragment in $contracts[$file]) { Assert-Text $file $fragment }
        Write-Host "[OK] $file contract" -ForegroundColor Green
    }
    Assert-Text 'packaging/agent-deb/DEBIAN/postinst' 'chmod 1777 /run/usbshield-notifier' $false
    Assert-Text 'packaging/agent-deb/DEBIAN/postinst' 'usermod -aG usbguard usbshield' $false
    Assert-Text 'packaging/agent-deb/DEBIAN/prerm' 'rm -rf /var/lib/usbshield-agent' $false
    Assert-Text 'packaging/agent-deb/etc/usbshield-agent/application.yml' 'default-enrollment-secret' $false
    Assert-Text 'packaging/desktop/usbshield-notifier-launcher.sh' 'exec /usr/bin/java -cp "$JAR_PATH"' $false

    $psFile = $builderPath
    $parseTokens = $null; $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($psFile, [ref]$parseTokens, [ref]$parseErrors)
    Assert-True ($parseErrors.Count -eq 0) 'Native build-deb.ps1 PowerShell syntax'

    if (-not $SkipTests) {
        $maven = Get-RequiredCommand @('mvn.cmd', 'mvn')
        $npm = Get-RequiredCommand @('npm.cmd', 'npm')
        $envPath = Join-Path $root '.env'
        if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
            throw 'Missing test-only .env; copy .env.example and set USBSHIELD_TEST_DB_PASSWORD first.'
        }
        # Parse only the three intended test-DB variables without evaluating code.
        $previous = @{}
        foreach ($key in @('USBSHIELD_TEST_DB_HOST','USBSHIELD_TEST_DB_PORT','USBSHIELD_TEST_DB_USERNAME','USBSHIELD_TEST_DB_PASSWORD')) {
            $previous[$key] = [System.Environment]::GetEnvironmentVariable($key, 'Process')
        }
        try {
            foreach ($line in [System.IO.File]::ReadAllLines($envPath)) {
                $s = $line.Trim()
                if (-not $s -or $s.StartsWith('#')) { continue }
                if ($s -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { throw 'Invalid test .env format (expected KEY=VALUE)' }
                $key = $Matches[1]; $value = $Matches[2].Trim()
                if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
                if ($previous.ContainsKey($key)) { [System.Environment]::SetEnvironmentVariable($key, $value, 'Process') }
            }
            Assert-True ($env:USBSHIELD_TEST_DB_USERNAME -eq 'usbshield_test') 'Dedicated test DB role'
            Assert-True (-not [string]::IsNullOrWhiteSpace($env:USBSHIELD_TEST_DB_PASSWORD) -and
                $env:USBSHIELD_TEST_DB_PASSWORD -ne 'REPLACE_WITH_YOUR_TEST_PASSWORD') 'Test DB password supplied (not printed)'
            Invoke-Checked 'Admin Maven tests (PostgreSQL test DB)' $maven @('-f','admin-server/pom.xml','clean','test')
            Invoke-Checked 'Agent Maven tests (Windows applicable)' $maven @('-f','client-agent/pom.xml','clean','test')
            Invoke-Checked 'Frontend npm ci' $npm @('--prefix','frontend','ci')
            Invoke-Checked 'Frontend tests' $npm @('--prefix','frontend','test')
            Invoke-Checked 'Frontend lint' $npm @('--prefix','frontend','run','lint')
            Invoke-Checked 'Frontend build' $npm @('--prefix','frontend','run','build')
            if (-not $SkipBrowserLive) {
                Invoke-Checked 'Browser live suite (isolated PostgreSQL)' $npm @('--prefix','frontend','run','test:browser:live')
                Invoke-Checked 'HTTP live suite (isolated PostgreSQL)' $npm @('--prefix','frontend','run','test:live')
            }
        } finally {
            foreach ($key in $previous.Keys) {
                [System.Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process')
            }
        }
        Invoke-Checked 'Admin production JAR with React' $maven @('-f','admin-server/pom.xml','-Pwith-frontend','-DskipTests','package')
        $serverJar = Join-Path $root 'admin-server/target/usbshield-admin-server.jar'
        Assert-True (Test-Path -LiteralPath $serverJar -PathType Leaf) 'Admin production JAR exists'
        $jarTool = Get-RequiredCommand @('jar.exe','jar')
        $global:LASTEXITCODE = 0
        $serverEntries = @(& $jarTool tf $serverJar)
        if ($LASTEXITCODE -ne 0) { throw 'Admin jar tf failed' }
        Assert-True ($serverEntries -contains 'BOOT-INF/classes/static/index.html') 'Admin JAR bundles the React Web build'
    }

    Show-Phase 'Create or read Windows-built Agent DEB'
    if (-not $SkipDebBuild) {
        # Child pwsh prevents any accidental exit/scope pollution by the builder.
        $pwsh = (Get-Process -Id $PID).Path
        Invoke-Checked 'Native Windows .deb build' $pwsh @('-NoProfile','-File',$builderPath)
    } else {
        Write-Host '[SKIP] .deb build requested; existing artifact must pass all checks.' -ForegroundColor Yellow
    }
    Assert-True (Test-Path -LiteralPath $packagePath -PathType Leaf) 'Built DEB exists'
    $debBytes = (Get-Item -LiteralPath $packagePath).Length
    Assert-True ($debBytes -gt 10000) "Built DEB is nonempty ($debBytes bytes)"

    Show-Phase 'Inspect actual .deb: ar, tar.gz, modes, owners, payloads'
    $members = Get-ArMembers $packagePath
    $controlManifest = @(
        @{ Path='control'; Source='packaging/agent-deb/DEBIAN/control'; Mode='0644'; Text=$true },
        @{ Path='postinst'; Source='packaging/agent-deb/DEBIAN/postinst'; Mode='0755'; Text=$true },
        @{ Path='prerm'; Source='packaging/agent-deb/DEBIAN/prerm'; Mode='0755'; Text=$true }
    )
    $dataManifest = @(
        @{ Path='opt/usbshield-agent/usbshield-agent.jar'; Source='client-agent/target/usbshield-agent.jar'; Mode='0755'; Text=$false },
        @{ Path='opt/usbshield-agent/usbshield-notifier-launcher.sh'; Source='packaging/desktop/usbshield-notifier-launcher.sh'; Mode='0755'; Text=$true },
        @{ Path='etc/usbshield-agent/application.yml'; Source='packaging/agent-deb/etc/usbshield-agent/application.yml'; Mode='0640'; Text=$true },
        @{ Path='etc/usbshield-agent/rules-v0.1.conf'; Source='packaging/usbguard/rules-v0.1.conf'; Mode='0644'; Text=$true },
        @{ Path='usr/lib/systemd/system/usbshield-agent.service'; Source='packaging/systemd/usbshield-agent.service'; Mode='0644'; Text=$true },
        @{ Path='etc/xdg/autostart/usbshield-notifier.desktop'; Source='packaging/desktop/usbshield-notifier.desktop'; Mode='0644'; Text=$true }
    )
    $controlFiles = Get-TarFiles $members['control.tar.gz'] 'control'
    $dataFiles = Get-TarFiles $members['data.tar.gz'] 'data'
    Assert-True ($controlFiles.ContainsKey('md5sums')) 'Debian control md5sums is present'
    # md5sums is generated by the builder and has no source file in the repo.
    Assert-PackageEntries $controlFiles $controlManifest 'control' @('md5sums')
    Assert-True ($controlFiles['md5sums'].Mode -eq [Convert]::ToInt32('0644', 8)) 'control/md5sums mode 0644'
    $md5text = [System.Text.Encoding]::UTF8.GetString($controlFiles['md5sums'].Bytes)
    $md5map = @{}
    foreach ($line in ($md5text -split "`n")) {
        if (-not $line.Trim()) { continue }
        if ($line -notmatch '^([a-f0-9]{32})  (.+)$') { throw 'Invalid Debian md5sums syntax' }
        if ($md5map.ContainsKey($Matches[2])) { throw 'Duplicate md5sums entry' }
        $md5map[$Matches[2]] = $Matches[1]
    }
    Assert-True ($md5map.Count -eq $dataManifest.Count) 'Debian md5sums covers every data file'
    foreach ($item in $dataManifest) {
        $digest = [Convert]::ToHexString([System.Security.Cryptography.MD5]::HashData($dataFiles[$item.Path].Bytes)).ToLowerInvariant()
        Assert-True ($md5map[$item.Path] -ceq $digest) "Debian md5sums matches $($item.Path)"
    }
    Assert-PackageEntries $dataFiles $dataManifest 'data'

    $jar = Join-Path $root 'client-agent/target/usbshield-agent.jar'
    Assert-True (Test-Path -LiteralPath $jar -PathType Leaf) 'Agent JAR exists for inspection'
    $jarTool = Get-RequiredCommand @('jar.exe','jar')
    $global:LASTEXITCODE = 0
    $jarEntries = @(& $jarTool tf $jar)
    if ($LASTEXITCODE -ne 0) { throw 'jar tf failed' }
    foreach ($class in @(
        'org/springframework/boot/loader/launch/PropertiesLauncher.class',
        'BOOT-INF/classes/com/group/usbshield/agent/ClientAgentApplication.class',
        'BOOT-INF/classes/com/group/usbshield/agent/notification/DesktopNotificationClientMain.class',
        'BOOT-INF/classes/com/group/usbshield/agent/endpoint/EndpointManager.class',
        'BOOT-INF/classes/com/group/usbshield/agent/sync/HeartbeatCoordinator.class',
        'BOOT-INF/classes/com/group/usbshield/agent/sync/PolicySyncService.class',
        'BOOT-INF/classes/com/group/usbshield/agent/outbox/OutboxSpoolService.class',
        'BOOT-INF/classes/com/group/usbshield/agent/event/EventSenderService.class')) {
        Assert-True ($jarEntries -contains $class) "Agent JAR contains $class"
    }
    $hash = (Get-FileHash -LiteralPath $packagePath -Algorithm SHA256).Hash
    Write-Host "`nRESULT: PASS - Windows source, tests (unless skipped), packages and archive checks" -ForegroundColor Green
    Write-Host "DEB SHA256: $hash"
    Write-Host 'UBUNTU E2E: NOT VERIFIED by this Windows-only script' -ForegroundColor Yellow
    if ($SkipTests -or $SkipBrowserLive -or $SkipDebBuild) {
        Write-Host 'SCOPE NOTE: One or more optional checks were skipped; NOT a full Windows regression run.' -ForegroundColor Yellow
    }
} catch {
    [Console]::Error.WriteLine("Windows verification FAILED at last phase: $($steps[$steps.Count - 1]) — $($_.Exception.Message)")
    exit 1
}

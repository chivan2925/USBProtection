#requires -Version 7.4
<#
.SYNOPSIS
    Build a Windows Central bundle with the API JAR and native JavaFX app image.
.DESCRIPTION
    Output is written to admin-server/target/release by default (gitignored),
    not to the source folder. Desktop includes its own Java runtime.
    A clean Git working tree is required unless -AllowDirtyTree is specified.
    Tests run by default using the repo-root, untracked .env test database.
    The .env file is loaded only for build-time tests; environment variables are
    restored when the script finishes. JAR runtime requires separately configured
    environment variables (or scripts/with-env.ps1).
    Does not install/start PostgreSQL, Nginx, or the Admin Server.
.EXAMPLE
    .\admin-server\build-release.ps1
.EXAMPLE
    .\admin-server\build-release.ps1 -SkipTests -AllowDirtyTree
.EXAMPLE
    .\admin-server\build-release.ps1 -OutputDirectory 'D:\USBShield-Releases'
#>
[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$AllowDirtyTree,
    [switch]$SkipTests,
    [string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

if (-not $IsWindows -or $PSVersionTable.PSEdition -ne 'Core') {
    throw 'Use PowerShell 7 on Windows to build this release.'
}

$repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$target = Join-Path $PSScriptRoot 'target'
$artifact = Join-Path $target 'usbshield-admin-server.jar'
$desktopModule = Join-Path $repo 'admin-desktop'
$previousJavaHome = $env:JAVA_HOME
$savedTestVars = @{}
$savedProdVars = @{}
# Get-Command may return multiple installed copies (e.g. GraalVM 21 + JDK 25).
# Resolve one executable, never an array of paths.
function Resolve-Application([string]$Name) {
    $command = Get-Command -Name $Name -CommandType Application -ErrorAction Stop | Select-Object -First 1
    if ($null -eq $command -or [string]::IsNullOrWhiteSpace($command.Source)) {
        throw "Required executable not found: $Name"
    }
    return [string]$command.Source
}

$git = Resolve-Application 'git.exe'
$mvn = Resolve-Application 'mvn.cmd'

function Invoke-Checked([string]$Title, [string]$Executable, [string[]]$Arguments, [string]$WorkingDirectory) {
    Write-Host "`n== $Title ==" -ForegroundColor Cyan
    Push-Location -LiteralPath $WorkingDirectory
    try {
        $global:LASTEXITCODE = 0
        & $Executable @Arguments
        if ($LASTEXITCODE -ne 0) { throw "$Title failed with exit code $LASTEXITCODE" }
    } finally { Pop-Location }
}

try {
    if ($JavaHome) {
        $javaExecutable = Join-Path $JavaHome 'bin/java.exe'
        if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
            throw "JAVA_HOME does not contain bin/java.exe: $JavaHome"
        }
        $env:JAVA_HOME = $JavaHome
    }
    $java = if ($JavaHome) { $javaExecutable } else { Resolve-Application 'java.exe' }
    # jar.exe must come from the same JDK as java.exe, not an arbitrary PATH hit.
    $jarTool = Join-Path (Split-Path -Parent $java) 'jar.exe'
    $jpackage = Join-Path (Split-Path -Parent $java) 'jpackage.exe'
    if (-not (Test-Path -LiteralPath $jarTool -PathType Leaf) -or
        -not (Test-Path -LiteralPath $jpackage -PathType Leaf)) {
        throw 'Selected Java installation must be a full JDK 21 with jar and jpackage.'
    }

    $global:LASTEXITCODE = 0
    $commit = (& $git -C $repo rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-f]{40}$') { throw 'Cannot read Git HEAD.' }
    $tree = (& $git -C $repo rev-parse 'HEAD^{tree}').Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git tree.' }
    $branch = (& $git -C $repo branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot determine branch.' }
    $dirty = @(& $git -C $repo status --porcelain --untracked-files=normal)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect working tree.' }
    if ($dirty.Count -gt 0 -and -not $AllowDirtyTree) {
        throw 'Working tree is not clean. Commit/stash changes or use -AllowDirtyTree for a NON-RELEASE development build.'
    }

    $outDir = if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
        Join-Path $target 'release'
    } else {
        $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDirectory)
    }
    # Do not create output before the build: Maven clean deletes target/.
    $global:LASTEXITCODE = 0
    $javaVersion = (& $java -version 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'java -version failed.' }
    $global:LASTEXITCODE = 0
    $mavenLines = @(& $mvn -version 2>&1)
    if ($LASTEXITCODE -ne 0 -or $mavenLines.Count -lt 1) { throw 'mvn -version failed.' }
    $mavenVersion = [string]$mavenLines[0]
    if ($SkipTests) {
        Invoke-Checked 'Package Admin Server (tests explicitly skipped)' $mvn @(
            '-B', '-f', (Join-Path $PSScriptRoot 'pom.xml'), '-DskipTests', 'clean', 'package'
        ) $repo
    } else {
        $envFile = Join-Path $repo '.env'
        if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
            throw 'Missing test .env. Configure test PostgreSQL and .env, or explicitly use -SkipTests.'
        }
        # Do not allow inherited application/prod datasource settings to redirect tests.
        foreach ($item in (Get-ChildItem Env:)) {
            if ($item.Name -match '^(USBSHIELD_DB_|SPRING_DATASOURCE_|SPRING_FLYWAY_|SPRING_JPA_|SPRING_SQL_|SPRING_PROFILES_ACTIVE$|SPRING_CONFIG_|SPRING_APPLICATION_JSON$|FLYWAY_)') {
                $savedProdVars[$item.Name] = $item.Value
                [System.Environment]::SetEnvironmentVariable($item.Name, $null, 'Process')
            }
        }
        foreach ($name in @('USBSHIELD_TEST_DB_HOST','USBSHIELD_TEST_DB_PORT','USBSHIELD_TEST_DB_USERNAME','USBSHIELD_TEST_DB_PASSWORD')) {
            $savedTestVars[$name] = [System.Environment]::GetEnvironmentVariable($name, 'Process')
        }
        $lineNo = 0
        foreach ($line in [System.IO.File]::ReadAllLines($envFile)) {
            $lineNo++
            $trimmed = $line.Trim()
            if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
            if ($trimmed -notmatch '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
                throw "Invalid test .env at line $lineNo"
            }
            $key = $Matches[1]; $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (
                ($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            if ($savedTestVars.ContainsKey($key)) {
                [System.Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
        }
        if ($env:USBSHIELD_TEST_DB_USERNAME -cne 'usbshield_test' -or
            [string]::IsNullOrWhiteSpace($env:USBSHIELD_TEST_DB_PASSWORD) -or
            $env:USBSHIELD_TEST_DB_PASSWORD -ceq 'REPLACE_WITH_YOUR_TEST_PASSWORD') {
            throw 'Test PostgreSQL role must be usbshield_test with a real test password.'
        }
        Invoke-Checked 'Test and package Admin Server' $mvn @(
            '-B', '-f', (Join-Path $PSScriptRoot 'pom.xml'), 'clean', 'package'
        ) $repo
    }

    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) { throw 'Admin Server JAR was not generated.' }
    $global:LASTEXITCODE = 0
    $entries = @(& $jarTool tf $artifact)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the JAR with jar tf.' }
    foreach ($entry in @(
        'org/springframework/boot/loader/launch/JarLauncher.class',
        'BOOT-INF/classes/com/group/usbshield/server/UsbShieldServerApplication.class',
        'BOOT-INF/classes/db/migration/V1__initial_postgresql_schema.sql',
        'BOOT-INF/classes/db/migration/V2__admin_session_and_usbguard_health.sql',
        'BOOT-INF/classes/db/migration/V3__workspace_boundary.sql'
    )) {
        if ($entries -cnotcontains $entry) { throw "Required JAR entry missing: $entry" }
    }
    if (@($entries | Where-Object { $_ -like 'BOOT-INF/classes/static/*' -or $_ -like '*swagger-ui-*.jar' }).Count) {
        throw 'Admin Server JAR contains Web UI assets.'
    }
    $desktopBuild = @('-B', '-f', (Join-Path $desktopModule 'pom.xml'))
    if ($SkipTests) { $desktopBuild += '-DskipTests' }
    $desktopBuild += @('clean', 'package')
    Invoke-Checked 'Package JavaFX Desktop' $mvn $desktopBuild $repo

    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
    $bundleName = "usbshield-central_$($commit.Substring(0,12))_$stamp"
    $bundle = Join-Path $outDir $bundleName
    $stage = Join-Path $desktopModule 'target/jpackage-input'
    New-Item -ItemType Directory -Path $bundle,$stage | Out-Null
    Copy-Item -LiteralPath (Join-Path $desktopModule 'target/usbshield-desktop.jar') -Destination $stage
    Copy-Item -LiteralPath (Join-Path $desktopModule 'target/lib') -Destination $stage -Recurse
    $checkProperties = Join-Path $desktopModule 'target/check-launcher.properties'
    @('win-console=true', 'arguments=--check-central') | Set-Content -LiteralPath $checkProperties -Encoding ascii
    Invoke-Checked 'Build native Windows app image' $jpackage @(
        '--type','app-image','--name','USBShield','--input',$stage,'--dest',$bundle,
        '--main-jar','usbshield-desktop.jar','--add-launcher',"USBShieldCheck=$checkProperties",
        '--java-options','--module-path=$APPDIR/lib','--java-options','--add-modules=javafx.controls,javafx.fxml',
        '--add-modules','java.desktop,java.net.http,java.logging,java.xml,java.scripting,java.management,java.naming,jdk.crypto.ec,jdk.unsupported'
    ) $repo
    foreach ($relative in @('USBShield/USBShield.exe','USBShield/USBShieldCheck.exe','USBShield/runtime/release')) {
        if (-not (Test-Path -LiteralPath (Join-Path $bundle $relative) -PathType Leaf)) { throw "Missing app image file: $relative" }
    }
    Copy-Item -LiteralPath $artifact -Destination $bundle
    Copy-Item -LiteralPath (Join-Path $repo 'packaging/windows/Start-USBShield.ps1') -Destination $bundle
    Copy-Item -LiteralPath (Join-Path $repo 'packaging/nginx/usbshield-https-acl.conf.example') -Destination $bundle
    $fileName = "$bundleName.zip"
    $archive = Join-Path $outDir $fileName
    Compress-Archive -LiteralPath $bundle -DestinationPath $archive
    $hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    $relativeManifest = "$fileName.manifest.json"
    $manifest = [ordered]@{
        schemaVersion = 3
        builtAtUtc = [DateTime]::UtcNow.ToString('o')
        source = [ordered]@{
            commit = $commit
            tree = $tree
            branch = $branch
            workingTreeCleanAtStart = ($dirty.Count -eq 0)
        }
        toolchain = [ordered]@{
            java = ($javaVersion -split "`r?`n")[0]
            maven = [string]$mavenVersion
            javaTarget = '21'
        }
        build = [ordered]@{
            backendTestsExecuted = (-not $SkipTests)
            desktopTestsExecuted = (-not $SkipTests)
            nativeLauncher = 'jpackage app-image'
            command = 'admin-server/build-release.ps1'
        }
        artifact = [ordered]@{
            filename = $fileName
            sizeBytes = (Get-Item -LiteralPath $archive).Length
            sha256 = $hash
        }
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outDir $relativeManifest) -Encoding utf8NoBOM
    "$hash  $fileName" | Set-Content -LiteralPath (Join-Path $outDir "$fileName.sha256") -Encoding ascii

    Write-Host "`nPASS: Windows Central bundle packaged." -ForegroundColor Green
    Write-Host "Bundle: $archive"
    Write-Host "SHA256: $hash"
    Write-Host "Manifest: $(Join-Path $outDir $relativeManifest)"
    if ($SkipTests -or $dirty.Count -gt 0) {
        Write-Warning 'This build is not fully release-qualified (tests skipped and/or dirty Git tree).'
    }
} finally {
    foreach ($key in $savedTestVars.Keys) {
        [System.Environment]::SetEnvironmentVariable($key, $savedTestVars[$key], 'Process')
    }
    foreach ($key in $savedProdVars.Keys) {
        [System.Environment]::SetEnvironmentVariable($key, $savedProdVars[$key], 'Process')
    }
    $env:JAVA_HOME = $previousJavaHome
}

param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$AllowDirtyTree
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$evidence = Join-Path $repo 'docs/member1/week4-evidence'
$artifact = Join-Path $PSScriptRoot 'target/usbshield-admin-server.jar'
$manifestPath = Join-Path $evidence 'server-release-manifest.json'
$previousJava = $env:JAVA_HOME

try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }

    $sourceCommit = (& git -C $repo rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot determine source commit.' }
    $sourceTree = (& git -C $repo rev-parse 'HEAD^{tree}').Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot determine source tree.' }
    $dirty = @(& git -C $repo status --porcelain)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect source tree status.' }
    if ($dirty.Count -gt 0 -and !$AllowDirtyTree) {
        throw 'Source tree is dirty. Commit changes before creating a release artifact, or explicitly pass -AllowDirtyTree for a non-release build.'
    }

    $javaExecutable = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { (Get-Command java.exe).Source }
    if (!(Test-Path $javaExecutable)) { throw "Java executable not found: $javaExecutable" }
    $javaVersion = (& $javaExecutable -version 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Java version query failed.' }
    $mavenOutput = @(& mvn.cmd -version 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Maven version query failed.' }
    $mavenVersion = $mavenOutput[0].ToString().Trim()
    $nodeVersion = (& node.exe --version).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Node version query failed.' }
    $npmVersion = (& npm.cmd --version).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'npm version query failed.' }

    New-Item -ItemType Directory -Force $evidence | Out-Null
    Push-Location (Join-Path $repo 'frontend')
    try {
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'React production build failed.' }
    } finally { Pop-Location }
    if (!(Test-Path (Join-Path $repo 'frontend/dist/index.html'))) { throw 'React index.html missing.' }

    & mvn.cmd -B -f (Join-Path $PSScriptRoot 'pom.xml') -Pwith-frontend clean package
    if ($LASTEXITCODE -ne 0) { throw 'Server Maven test/package lifecycle failed.' }
    if (!(Test-Path $artifact)) { throw 'Server JAR missing after package.' }

    $artifactInfo = Get-Item -LiteralPath $artifact
    $artifactHash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest = [ordered]@{
        schemaVersion = 1
        owner = 'M1'
        builtAtUtc = [DateTime]::UtcNow.ToString('o')
        sourceCommit = $sourceCommit
        sourceTree = $sourceTree
        branch = (& git -C $repo branch --show-current).Trim()
        workingTreeCleanAtStart = ($dirty.Count -eq 0)
        environment = [ordered]@{
            os = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
            powershell = $PSVersionTable.PSVersion.ToString()
            processArchitecture = [System.Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture.ToString()
        }
        toolchain = [ordered]@{
            java = ($javaVersion -split "`r?`n")[0]
            maven = $mavenVersion
            node = $nodeVersion
            npm = $npmVersion
            javaTarget = '21'
        }
        build = [ordered]@{
            command = 'admin-server/build-release.ps1'
            result = 'PASS'
            lifecycle = 'mvn -B -Pwith-frontend clean package'
        }
        artifact = [ordered]@{
            path = 'admin-server/target/usbshield-admin-server.jar'
            sizeBytes = $artifactInfo.Length
            sha256 = $artifactHash
            includesReactDist = $true
        }
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM
    Write-Output "PASS: built $($manifest.artifact.path)"
    Write-Output "SHA-256: $artifactHash"
    Write-Output "Manifest: $manifestPath"
} finally {
    $env:JAVA_HOME = $previousJava
}

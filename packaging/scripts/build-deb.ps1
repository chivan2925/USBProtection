#requires -Version 7.4
<#
.SYNOPSIS
    Build USBShield's Ubuntu amd64 .deb on Windows in PowerShell 7.4+ (no WSL/dpkg).
.DESCRIPTION
    Builds the existing client-agent JAR using Windows Maven and writes the
    standard Debian ar archive with GNU tar.gz payloads via System.Formats.Tar.
    The package file layout, owners (root:root) and Unix modes match
    packaging/scripts/build-deb.sh on main 1ae8f981.
.NOTES
    Run from a checkout of chivan2925/USBProtection on Windows with JDK 21,
    Maven and PowerShell 7.4+. This is a proposed replacement packaging PATH;
    it does not change the existing Linux build-deb.sh.
#>
[CmdletBinding()]
param(
    [switch]$SkipJavaBuild
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $IsWindows) { throw 'Use Windows PowerShell 7.4+ (pwsh.exe) for this Windows-native build.' }
if (-not ('System.Formats.Tar.TarWriter' -as [type])) {
    throw 'System.Formats.Tar is unavailable. Use a supported PowerShell 7.4+ release.'
}

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$packaging = Join-Path $root 'packaging'
$stage = Join-Path $packaging 'staging/windows-deb'
$outputDir = Join-Path $packaging 'dist'
# F10: unique Debian version per build. This avoids silent apt no-op when
# replacing a .deb that still advertises Version: 1.0.0.
$buildStamp = [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff', [Globalization.CultureInfo]::InvariantCulture)
$packageVersion = "1.0.0+build$buildStamp"
$output = Join-Path $outputDir "usbshield-agent_${packageVersion}_amd64.deb"
$releaseManifest = Join-Path $outputDir 'usbshield-agent.release.json'
$jar = Join-Path $root 'client-agent/target/usbshield-agent.jar'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$ascii = [System.Text.Encoding]::ASCII
$epoch = [System.DateTimeOffset]::FromUnixTimeSeconds(0)

if (-not $SkipJavaBuild) {
    $maven = Get-Command mvn.cmd, mvn -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $maven) { throw 'Maven not found on PATH. Install Java 21 JDK + Maven for Windows.' }
    Write-Host '== Building client-agent JAR (Windows Maven) =='
    & $maven.Source -f (Join-Path $root 'client-agent/pom.xml') clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven package failed (exit $LASTEXITCODE)." }
}
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Missing Agent JAR: $jar" }
if ((Get-Item -LiteralPath $jar).Length -eq 0) { throw 'Agent JAR is empty.' }

if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage, $outputDir -Force | Out-Null

function Copy-PackageFile {
    param(
        [Parameter(Mandatory)][string]$Source,
        [Parameter(Mandatory)][string]$Relative,
        [Parameter(Mandatory)][string]$Subdir,
        [switch]$NormalizeLF
    )
    $src = Join-Path $root $Source
    if (-not (Test-Path -LiteralPath $src -PathType Leaf)) { throw "Package input missing: $Source" }
    $dest = Join-Path (Join-Path $stage $Subdir) $Relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $dest) -Force | Out-Null
    if ($NormalizeLF) {
        $s = [System.IO.File]::ReadAllText($src)
        $s = $s.Replace("`r`n", "`n").Replace("`r", "`n")
        [System.IO.File]::WriteAllText($dest, $s, $utf8)
    } else {
        [System.IO.File]::Copy($src, $dest, $true)
    }
    return $dest
}

# Stage only the production files present in main's build-deb.sh.
$data = @(
    @{ Src='client-agent/target/usbshield-agent.jar'; Path='opt/usbshield-agent/usbshield-agent.jar'; Mode='0755'; Text=$false },
    @{ Src='packaging/agent-deb/etc/usbshield-agent/application.yml'; Path='etc/usbshield-agent/application.yml'; Mode='0640'; Text=$true },
    @{ Src='packaging/usbguard/rules-v0.1.conf'; Path='etc/usbshield-agent/rules-v0.1.conf'; Mode='0644'; Text=$true },
    @{ Src='packaging/systemd/usbshield-agent.service'; Path='usr/lib/systemd/system/usbshield-agent.service'; Mode='0644'; Text=$true },
    @{ Src='packaging/desktop/usbshield-notifier.desktop'; Path='etc/xdg/autostart/usbshield-notifier.desktop'; Mode='0644'; Text=$true },
    @{ Src='packaging/desktop/usbshield-notifier-launcher.sh'; Path='opt/usbshield-agent/usbshield-notifier-launcher.sh'; Mode='0755'; Text=$true }
)
$dataEntries = @()
foreach ($item in $data) {
    $dest = Copy-PackageFile -Source $item.Src -Relative $item.Path -Subdir 'data' -NormalizeLF:([bool]$item.Text)
    $dataEntries += [pscustomobject]@{ Path=$item.Path; File=$dest; Mode=$item.Mode }
}

$control = @(
    @{ Src='packaging/agent-deb/DEBIAN/control'; Path='control'; Mode='0644' },
    @{ Src='packaging/agent-deb/DEBIAN/postinst'; Path='postinst'; Mode='0755' },
    @{ Src='packaging/agent-deb/DEBIAN/prerm'; Path='prerm'; Mode='0755' }
)
$controlEntries = @()
foreach ($item in $control) {
    $dest = Copy-PackageFile -Source $item.Src -Relative $item.Path -Subdir 'control' -NormalizeLF
    if ($item.Path -eq 'control') {
        $text = [System.IO.File]::ReadAllText($dest)
        if (([regex]::Matches($text, '(?m)^Version: 1\.0\.0$')).Count -ne 1) {
            throw 'Expected exactly one control Version: 1.0.0 line'
        }
        $text = $text.Replace('Version: 1.0.0', "Version: $packageVersion")
        [System.IO.File]::WriteAllText($dest, $text, $utf8)
    }
    $controlEntries += [pscustomobject]@{ Path=$item.Path; File=$dest; Mode=$item.Mode }
}
# md5sums is standard Debian metadata for package-file integrity (not a security hash).
$md5Lines = foreach ($item in $dataEntries) {
    $md5 = (Get-FileHash -LiteralPath $item.File -Algorithm MD5).Hash.ToLowerInvariant()
    '{0}  {1}' -f $md5, $item.Path
}
$md5Path = Join-Path $stage 'control/md5sums'
[System.IO.File]::WriteAllText($md5Path, (($md5Lines -join "`n") + "`n"), $utf8)
$controlEntries += [pscustomobject]@{ Path='md5sums'; File=$md5Path; Mode='0644' }

function Write-GnuTarGz {
    param(
        [Parameter(Mandatory)][string]$Target,
        [Parameter(Mandatory)][array]$Entries
    )
    $file = [System.IO.File]::Create($Target)
    $gzip = $null
    $writer = $null
    try {
        $gzip = [System.IO.Compression.GZipStream]::new($file, [System.IO.Compression.CompressionLevel]::Optimal, $true)
        $writer = [System.Formats.Tar.TarWriter]::new($gzip, [System.Formats.Tar.TarEntryFormat]::Gnu, $true)
        $dirs = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($entry in $Entries) {
            $pathSegments = $entry.Path.Split('/')
            $prefix = ''
            for ($i = 0; $i -lt $pathSegments.Length - 1; $i++) {
                $prefix = if ($prefix) { "$prefix/$($pathSegments[$i])" } else { $pathSegments[$i] }
                [void]$dirs.Add($prefix)
            }
        }
        foreach ($dir in ($dirs | Sort-Object { $_.Split('/').Length }, { $_ })) {
            $te = [System.Formats.Tar.GnuTarEntry]::new([System.Formats.Tar.TarEntryType]::Directory, "./$dir/")
            $te.Mode = [System.IO.UnixFileMode]([Convert]::ToInt32('0755', 8))
            $te.Uid = 0; $te.Gid = 0; $te.UserName = 'root'; $te.GroupName = 'root'
            $te.ModificationTime = $epoch
            $writer.WriteEntry($te)
        }
        foreach ($entry in ($Entries | Sort-Object Path)) {
            $te = [System.Formats.Tar.GnuTarEntry]::new([System.Formats.Tar.TarEntryType]::RegularFile, "./$($entry.Path)")
            $te.Mode = [System.IO.UnixFileMode]([Convert]::ToInt32($entry.Mode, 8))
            $te.Uid = 0; $te.Gid = 0; $te.UserName = 'root'; $te.GroupName = 'root'
            $te.ModificationTime = $epoch
            $payload = [System.IO.File]::OpenRead($entry.File)
            try {
                $te.DataStream = $payload
                $writer.WriteEntry($te)
            } finally {
                $payload.Dispose()
            }
        }
    } finally {
        if ($null -ne $writer) { $writer.Dispose() }
        if ($null -ne $gzip) { $gzip.Dispose() }
        $file.Dispose()
    }
}

function Add-ArMember {
    param(
        [Parameter(Mandatory)][System.IO.FileStream]$Archive,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Path
    )
    $length = (Get-Item -LiteralPath $Path).Length
    $member = "$Name/"
    if ($member.Length -gt 16) { throw "ar member name too long: $Name" }
    if ($length -ge 10000000000) { throw 'ar member exceeds format size limit.' }
    # Unix ar: 60-byte ASCII header, then file, then newline if odd length.
    $header = $member.PadRight(16) + '0'.PadRight(12) + '0'.PadRight(6) +
        '0'.PadRight(6) + '100644'.PadRight(8) +
        $length.ToString([System.Globalization.CultureInfo]::InvariantCulture).PadRight(10) +
        ([char]96) + "`n"
    $bytes = $ascii.GetBytes($header)
    if ($bytes.Length -ne 60) { throw "Invalid ar header size for $Name" }
    $Archive.Write($bytes, 0, $bytes.Length)
    $inputStream = [System.IO.File]::OpenRead($Path)
    try { $inputStream.CopyTo($Archive) } finally { $inputStream.Dispose() }
    if (($length % 2) -ne 0) { $Archive.WriteByte(10) }
}

Write-Host '== Creating GNU tar.gz archives with root ownership and Linux file modes =='
$controlTar = Join-Path $stage 'control.tar.gz'
$dataTar = Join-Path $stage 'data.tar.gz'
Write-GnuTarGz -Target $controlTar -Entries $controlEntries
Write-GnuTarGz -Target $dataTar -Entries $dataEntries
$binary = Join-Path $stage 'debian-binary'
[System.IO.File]::WriteAllBytes($binary, $ascii.GetBytes("2.0`n"))

# Avoid leaving a partially-written file with the final name on failures.
$tempOutput = Join-Path $stage 'package.deb.tmp'
$archive = [System.IO.File]::Create($tempOutput)
try {
    $magic = $ascii.GetBytes("!<arch>`n")
    $archive.Write($magic, 0, $magic.Length)
    Add-ArMember -Archive $archive -Name 'debian-binary' -Path $binary
    Add-ArMember -Archive $archive -Name 'control.tar.gz' -Path $controlTar
    Add-ArMember -Archive $archive -Name 'data.tar.gz' -Path $dataTar
} finally {
    $archive.Dispose()
}
Copy-Item -LiteralPath $tempOutput -Destination $output -Force
$sha256 = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
$git = Get-Command git.exe, git -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -eq $git) { throw 'Git required to record HEAD SHA in release manifest.' }
$gitHead = (& $git.Source -C $root rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $gitHead -notmatch '^[a-f0-9]{40}$') { throw 'Cannot obtain Git HEAD SHA' }
# Only one manifest points to the most recently built Debian package.
$release = [ordered]@{
    version = $packageVersion
    filename = [IO.Path]::GetFileName($output)
    sha256 = $sha256
    gitHead = $gitHead
    builtAtUtc = [DateTime]::UtcNow.ToString('o')
}
[IO.File]::WriteAllText($releaseManifest, ($release | ConvertTo-Json -Depth 3) + "`n", $utf8)
Write-Host "DEB: $output"
Write-Host "SHA256: $sha256"
Write-Host "Release manifest: $releaseManifest"
Write-Host 'Build completed. Before installing, verify the package on Ubuntu with dpkg-deb -I/-c and sha256sum.'

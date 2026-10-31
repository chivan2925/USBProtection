# Guard the generated workspace target before invoking M3's unmodified staging script.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..')).Path
$packaging = Join-Path $root 'packaging'
$staging = [System.IO.Path]::GetFullPath((Join-Path $packaging 'staging'))
$expected = $root.TrimEnd('\') + '\packaging\staging'
if ($staging -ne $expected) { throw 'Staging must be the generated packaging/staging directory in this checkout.' }
foreach ($parent in @($root, $packaging)) {
    $item = Get-Item -LiteralPath $parent -Force
    if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) { throw 'Staging parent must not be a reparse point.' }
}
$allowed = @(
    'DEBIAN\control', 'DEBIAN\postinst', 'DEBIAN\prerm',
    'opt\usbshield-agent\usbshield-agent.jar',
    'opt\usbshield-agent\usbshield-notifier-launcher.sh',
    'etc\usbshield-agent\application.yml', 'etc\usbshield-agent\rules-v0.1.conf',
    'etc\xdg\autostart\usbshield-notifier.desktop',
    'usr\lib\systemd\system\usbshield-agent.service'
)
if (Test-Path -LiteralPath $staging) {
    $stagingItem = Get-Item -LiteralPath $staging -Force
    if (-not $stagingItem.PSIsContainer -or ($stagingItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
        throw 'Existing staging must be a normal directory.'
    }
    if ((Resolve-Path -LiteralPath $staging).Path -ne $expected) { throw 'Resolved staging path differs from the intended workspace target.' }
    $pendingDirectories = New-Object 'System.Collections.Generic.Queue[string]'
    $pendingDirectories.Enqueue($staging)
    while ($pendingDirectories.Count -gt 0) {
        foreach ($entry in Get-ChildItem -LiteralPath $pendingDirectories.Dequeue() -Force) {
            if ($entry.Attributes -band [System.IO.FileAttributes]::ReparsePoint) { throw 'Staging contains a reparse point; refusing cleanup.' }
            $absolute = [System.IO.Path]::GetFullPath($entry.FullName)
            if (-not $absolute.StartsWith($expected + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
                throw 'Staging child resolves outside the generated target.'
            }
            if ($entry.PSIsContainer) { $pendingDirectories.Enqueue($absolute) }
            elseif ($allowed -notcontains $absolute.Substring($expected.Length + 1)) {
                throw 'Existing staging contains an unexpected file; refusing to delete potentially unrelated data.'
            }
        }
    }
}
Write-Output "Verified generated staging cleanup target: $expected"
& (Join-Path $packaging 'scripts\prepare-staging.ps1')
foreach ($relative in $allowed) {
    if (-not (Test-Path -LiteralPath (Join-Path $staging $relative) -PathType Leaf)) {
        throw "Staging preparation omitted a required file: $relative"
    }
}
Write-Output 'PASS: guarded staging preparation; all 9 required files exist. Byte equality is verified separately.'

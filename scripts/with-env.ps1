# Run a command with environment values from the untracked repository-root .env.
# Example: .\scripts\with-env.ps1 -RequireTestDb -Command { mvn -f admin-server/pom.xml clean test }
# Supports KEY=VALUE and optional single/double quotes; values are literal (not evaluated).
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [scriptblock]$Command,
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env'),
    [switch]$RequireTestDb
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    throw "Missing .env file at $EnvFile. Copy .env.example to .env and set the test password."
}

$lineNumber = 0
foreach ($line in (Get-Content -LiteralPath $EnvFile -Encoding UTF8)) {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
        continue
    }
    if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
        # Parse values as data only; do not Invoke-Expression or interpolate secrets.
    } else {
        throw "Invalid .env syntax at line $lineNumber (expected KEY=VALUE)."
    }
    $name = $Matches[1]
    $value = $Matches[2].Trim()
    if ($value.Length -ge 2 -and (
            ($value[0] -eq '"' -and $value[$value.Length - 1] -eq '"') -or
            ($value[0] -eq "'" -and $value[$value.Length - 1] -eq "'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    [System.Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

if ($RequireTestDb) {
    if ($env:USBSHIELD_TEST_DB_USERNAME -ne 'usbshield_test') {
        throw '.env must use USBSHIELD_TEST_DB_USERNAME=usbshield_test, not the application role.'
    }
    if ([string]::IsNullOrWhiteSpace($env:USBSHIELD_TEST_DB_PASSWORD) -or
            $env:USBSHIELD_TEST_DB_PASSWORD -eq 'REPLACE_WITH_YOUR_TEST_PASSWORD') {
        throw 'Set a real USBSHIELD_TEST_DB_PASSWORD in .env before running tests.'
    }
}

# This is a child PowerShell process: values stay in this process and its children.
# Never print the password. Preserve the external command exit code.
$global:LASTEXITCODE = 0
& $Command
if (-not $?) {
    if ($global:LASTEXITCODE -ne 0) { exit $global:LASTEXITCODE }
    exit 1
}
exit $global:LASTEXITCODE

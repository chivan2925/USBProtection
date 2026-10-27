# Test suite for verify-week3-windows.ps1 staging verification logic
# Validates both negative probe cases (absent dir, missing files) and positive validation case.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scriptDir = $PSScriptRoot
$root = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
$verifierScript = Join-Path $scriptDir 'verify-week3-windows.ps1'

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  Running Staging Verifier Negative & Positive Probe Tests      " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

$tempBase = Join-Path ([System.IO.Path]::GetTempPath()) ("usbshield-test-" + [System.Guid]::NewGuid().ToString().Substring(0, 8))
New-Item -ItemType Directory -Path $tempBase -Force | Out-Null

try {
    # Test 1: Staging directory completely missing -> MUST FAIL (Exit 1)
    Write-Host "`n[Test 1] Probe non-existent staging directory..." -ForegroundColor Yellow
    $nonExistent = Join-Path $tempBase 'non_existent_dir'
    $failedAsExpected = $false
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $verifierScript -CustomStagingPath $nonExistent
        if ($LASTEXITCODE -ne 0) { $failedAsExpected = $true }
    } catch {
        $failedAsExpected = $true
    }
    if ($LASTEXITCODE -ne 0 -or $failedAsExpected) {
        Write-Host "  [PASS] Non-existent staging directory correctly caused verifier to FAIL (non-zero exit)" -ForegroundColor Green
    } else {
        throw "FAILED: Verifier reported PASS when staging directory was absent!"
    }

    # Test 2: Incomplete staging directory (missing required files) -> MUST FAIL (Exit 1)
    Write-Host "`n[Test 2] Probe incomplete staging directory (missing DEBIAN/control)..." -ForegroundColor Yellow
    $partialStaging = Join-Path $tempBase 'partial_staging'
    New-Item -ItemType Directory -Path (Join-Path $partialStaging 'DEBIAN') -Force | Out-Null
    Set-Content -Path (Join-Path $partialStaging 'DEBIAN\postinst') -Value '#!/bin/bash'
    
    $failedPartial = $false
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $verifierScript -CustomStagingPath $partialStaging
        if ($LASTEXITCODE -ne 0) { $failedPartial = $true }
    } catch {
        $failedPartial = $true
    }
    if ($LASTEXITCODE -ne 0 -or $failedPartial) {
        Write-Host "  [PASS] Incomplete staging directory correctly caused verifier to FAIL (non-zero exit)" -ForegroundColor Green
    } else {
        throw "FAILED: Verifier reported PASS when required files were missing from staging!"
    }

    # Test 3: Complete staging directory -> MUST PASS (Exit 0)
    Write-Host "`n[Test 3] Probe complete and valid staging directory..." -ForegroundColor Yellow
    $validStaging = Join-Path $tempBase 'valid_staging'
    $requiredPaths = @(
        'DEBIAN/control',
        'DEBIAN/postinst',
        'DEBIAN/prerm',
        'opt/usbshield-agent/usbshield-agent.jar',
        'opt/usbshield-agent/usbshield-notifier-launcher.sh',
        'etc/usbshield-agent/application.yml',
        'etc/xdg/autostart/usbshield-notifier.desktop',
        'usr/lib/systemd/system/usbshield-agent.service'
    )
    foreach ($rp in $requiredPaths) {
        $fp = Join-Path $validStaging $rp
        $parent = Split-Path -Parent $fp
        if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
        Set-Content -Path $fp -Value "test-dummy-content"
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $verifierScript -CustomStagingPath $validStaging
    if ($LASTEXITCODE -ne 0) {
        throw "FAILED: Verifier failed on completely valid staging directory!"
    }
    Write-Host "  [PASS] Valid staging directory successfully passed verification (Exit 0)" -ForegroundColor Green

    Write-Host "`n================================================================" -ForegroundColor Cyan
    Write-Host "  ALL STAGING VERIFIER PROBE TESTS PASSED (3/3)                 " -ForegroundColor Green
    Write-Host "================================================================`n" -ForegroundColor Cyan
} finally {
    if (Test-Path $tempBase) {
        Remove-Item -Path $tempBase -Recurse -Force -ErrorAction SilentlyContinue
    }
}

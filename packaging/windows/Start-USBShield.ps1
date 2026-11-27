#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$CaFile,
    [string]$CentralUrl = 'https://127.0.0.1',
    [ValidatePattern('^[a-zA-Z0-9_.-]+$')][string]$DatabaseService = 'USBShieldPostgreSQL',
    [ValidatePattern('^[a-zA-Z0-9_.-]+$')][string]$ServerService = 'USBShieldServer',
    [ValidatePattern('^[a-zA-Z0-9_.-]+$')][string]$ProxyService = 'USBShieldProxy',
    [switch]$StartServices,
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
if (-not $IsWindows) { throw 'Run on the Windows Central host.' }
if ($StartServices -and -not $CheckOnly) {
    throw 'Start services with -StartServices -CheckOnly, then open Desktop as a regular user.'
}
if (-not $CheckOnly) {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    try {
        $principal = [Security.Principal.WindowsPrincipal]::new($identity)
        if ($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
            throw 'Open Desktop from a non-elevated Windows session.'
        }
    } finally {
        $identity.Dispose()
    }
}
$ca = Get-Item -LiteralPath $CaFile
if ($ca.PSIsContainer) { throw 'Supply the provisioned CA PEM file.' }
$image = Join-Path $PSScriptRoot 'USBShield'
$desktop = Join-Path $image 'USBShield.exe'
$check = Join-Path $image 'USBShieldCheck.exe'
foreach ($entry in @($desktop,$check)) {
    if (-not (Test-Path -LiteralPath $entry -PathType Leaf)) { throw "Missing native app image launcher: $entry" }
}
$uri = [uri]$CentralUrl
if ($uri.Scheme -cne 'https' -or $uri.Host -cne '127.0.0.1' -or $uri.AbsolutePath -ne '/' -or
    $uri.Query -or $uri.Fragment -or $uri.UserInfo -or $uri.Port -lt 1) {
    throw 'CentralUrl must be a loopback HTTPS origin such as https://127.0.0.1.'
}

$serviceNames = @($DatabaseService,$ServerService,$ProxyService)
if (($serviceNames | Sort-Object -Unique).Count -ne 3) { throw 'Supply a distinct service for DB, backend and proxy.' }
foreach ($name in $serviceNames) {
    $service = Get-Service -Name $name -ErrorAction Stop
    if ($service.Status -ne 'Running' -and $StartServices) {
        Start-Service -InputObject $service -ErrorAction Stop
        $service.WaitForStatus('Running', [TimeSpan]::FromSeconds(15))
    }
    $service.Refresh()
    if ($service.Status -ne 'Running') { throw "Required Central service is not running: $name" }
}

$previousCa = $env:USBSHIELD_DESKTOP_CA
$previousUrl = $env:USBSHIELD_CENTRAL_URL
try {
    $env:USBSHIELD_DESKTOP_CA = $ca.FullName
    $env:USBSHIELD_CENTRAL_URL = $CentralUrl
    $ready = $false
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        & $check
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        if ($LASTEXITCODE -eq 2) { throw 'Check the Central TLS configuration and CA before launching.' }
        if ($attempt -lt 2) { Start-Sleep -Seconds 2 }
    }
    if (!$ready) { throw 'Central API readiness failed. Check PostgreSQL, backend and Nginx service logs.' }
    if (!$CheckOnly) { Start-Process -FilePath $desktop -WorkingDirectory $image }
} finally {
    $env:USBSHIELD_DESKTOP_CA = $previousCa
    $env:USBSHIELD_CENTRAL_URL = $previousUrl
}

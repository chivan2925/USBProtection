param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$previousJava = $env:JAVA_HOME
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    Push-Location (Join-Path $repo 'frontend')
    try {
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'React build failed.' }
    } finally { Pop-Location }
    if (!(Test-Path (Join-Path $repo 'frontend/dist/index.html'))) { throw 'React index.html missing.' }
    & mvn.cmd -B -f (Join-Path $PSScriptRoot 'pom.xml') -Pwith-frontend clean package
    if ($LASTEXITCODE -ne 0) { throw 'Server build failed.' }
} finally { $env:JAVA_HOME = $previousJava }

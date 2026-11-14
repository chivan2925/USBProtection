# Historical entrypoint retained for callers; exercises the current PostgreSQL
# production JAR/API verifier rather than the removed H2 implementation.
param([string]$JavaHome = $env:JAVA_HOME, [int]$Port = 18080)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'verify-week3.ps1') -JavaHome $JavaHome -Port $Port -EvidencePath 'docs/member1/evidence'

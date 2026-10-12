param([string]$JavaHome = $env:JAVA_HOME, [int]$Port = 18080)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$evidence = Join-Path $repo 'docs/member1/evidence'
$runtime = Join-Path $PSScriptRoot 'target/week1-runtime'
New-Item -ItemType Directory -Force $evidence, $runtime | Out-Null
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { (Get-Command java).Source }
$server = $null
$agent = $null
try {
    $server = Start-Process -FilePath $java -WindowStyle Hidden -PassThru -WorkingDirectory $PSScriptRoot -ArgumentList @(
        '-jar', 'target/usbshield-admin-server.jar', "--server.port=$Port", '--server.address=127.0.0.1',
        '--spring.datasource.url=jdbc:h2:file:./target/week1-runtime/server-db;DB_CLOSE_ON_EXIT=FALSE',
        '--logging.level.org.springframework.boot.autoconfigure.security=ERROR'
    ) -RedirectStandardOutput "$evidence/server-runtime.log" -RedirectStandardError "$evidence/server-runtime-error.log"
    $ready = $false
    for ($i = 0; $i -lt 45; $i++) {
        if ($server.HasExited) { throw 'Server exited before readiness; inspect runtime logs.' }
        try {
            Invoke-RestMethod "http://localhost:$Port/api-docs" -TimeoutSec 2 | Out-Null
            $ready = $true
            break
        } catch { Start-Sleep -Seconds 1 }
    }
    if (!$ready) { throw 'Server did not become ready.' }
    $sample = Get-Content -LiteralPath "$repo/docs/agent-contract/sample-usb-event.json" -Raw
    $source = $sample | ConvertFrom-Json
    $heartbeat = @{ endpointId=$source.endpointId; hostname=$source.hostname; agentVersion='week1-poc'; currentPolicyVersion=0 } | ConvertTo-Json
    $heartbeatResponse = Invoke-RestMethod "http://localhost:$Port/api/agent/heartbeat" -Method Post -ContentType 'application/json' -Body $heartbeat
    $eventResponse = Invoke-RestMethod "http://localhost:$Port/api/agent/events" -Method Post -ContentType 'application/json' -Body $sample
    if (!$heartbeatResponse.accepted -or !$eventResponse.success) { throw 'PoC request not accepted.' }
    @{ heartbeat=$heartbeatResponse; member2Event=$eventResponse; sampleSession=$source.sessionId; sampleDecision=$source.decision } |
        ConvertTo-Json -Depth 5 | Set-Content "$evidence/poc-responses.json" -Encoding utf8

    $agent = Start-Process -FilePath $java -WindowStyle Hidden -PassThru -WorkingDirectory "$repo/client-agent" -ArgumentList @(
        '-jar', 'target/usbshield-agent.jar', "--usbshield.agent.server-url=http://localhost:$Port",
        '--usbshield.agent.data-dir=./target/week1-runtime/data', '--usbshield.agent.config-dir=./target/week1-runtime/config'
    ) -RedirectStandardOutput "$evidence/agent-runtime.log" -RedirectStandardError "$evidence/agent-runtime-error.log"
    Start-Sleep -Seconds 20
    if ($agent.HasExited) { throw 'Agent exited unexpectedly.' }
    $serverLog = Get-Content "$evidence/server-runtime.log" -Raw
    $agentLog = Get-Content "$evidence/agent-runtime.log" -Raw
    if ($serverLog -notmatch '\[HEARTBEAT\].*EP-' -or
        $serverLog -notmatch '\[USB EVENT INGESTION\].*EP-' -or
        $agentLog -notmatch 'Successfully sent USB event') {
        throw 'Missing heartbeat/event evidence from actual Agent process.'
    }
    Write-Output 'PASS: server boot + file H2 + M2 sample HTTP + actual Agent heartbeat/simulated event. Hardware USB enforcement not tested.'
} finally {
    if ($agent -and !$agent.HasExited) { Stop-Process -Id $agent.Id }
    if ($server -and !$server.HasExited) { Stop-Process -Id $server.Id }
}

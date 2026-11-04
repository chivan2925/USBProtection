param(
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$Port = 18083,
    [string]$EvidencePath = 'docs/member1/week3-evidence'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$evidence = if ([System.IO.Path]::IsPathRooted($EvidencePath)) { $EvidencePath } else { Join-Path $repo $EvidencePath }
$runId = [guid]::NewGuid().ToString('N')
$runtime = Join-Path $PSScriptRoot "target/week3-$runId"
New-Item -ItemType Directory -Force $evidence, $runtime | Out-Null
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { (Get-Command java).Source }
$base = "http://127.0.0.1:$Port"
$server = $null
$savedDbPassword = $env:USBSHIELD_DB_PASSWORD
$savedDbUrl = $env:USBSHIELD_DB_URL
$savedDbUsername = $env:USBSHIELD_DB_USERNAME
if ($null -eq $env:USBSHIELD_TEST_DB_PASSWORD) {
    throw 'Set USBSHIELD_TEST_DB_PASSWORD for the dedicated usbshield_browser_test PostgreSQL database.'
}
$pgHost = if ($env:USBSHIELD_TEST_DB_HOST) { $env:USBSHIELD_TEST_DB_HOST } else { '127.0.0.1' }
$pgPort = if ($env:USBSHIELD_TEST_DB_PORT) { $env:USBSHIELD_TEST_DB_PORT } else { '5432' }
if ($pgHost -notmatch '^[a-zA-Z0-9_.-]+$' -or $pgPort -notmatch '^\d{1,5}$' -or [int]$pgPort -lt 1 -or [int]$pgPort -gt 65535) {
    throw 'Invalid PostgreSQL test host/port.'
}
$savedPassword = $env:USBSHIELD_ADMIN_PASSWORD
$savedUser = $env:USBSHIELD_ADMIN_USERNAME
$savedBootstrap = $env:USBSHIELD_ENROLLMENT_TOKEN
$password = [guid]::NewGuid().ToString('N')
$bootstrap = [guid]::NewGuid().ToString('N')
function Assert-Http($Path, $Method, $Body, $Headers, $Expected, $Session = $null) {
    $requestParams = @{ Uri="$base$Path"; Method=$Method; SkipHttpErrorCheck=$true; ContentType='application/json' }
    if ($null -ne $Body) { $requestParams.Body = $Body | ConvertTo-Json -Depth 12 -Compress }
    if ($Headers) { $requestParams.Headers = $Headers }
    if ($Session) { $requestParams.WebSession = $Session }
    $response = Invoke-WebRequest @requestParams
    if ([int]$response.StatusCode -ne $Expected) { throw "$Method $Path expected $Expected; received $($response.StatusCode)" }
    if ($response.Content) { return $response.Content | ConvertFrom-Json }
}
try {
    # This verifier intentionally recreates tables ONLY in the disposable test database.
    $env:USBSHIELD_DB_URL = "jdbc:postgresql://${pgHost}:${pgPort}/usbshield_browser_test"
    $env:USBSHIELD_DB_USERNAME = if ($env:USBSHIELD_TEST_DB_USERNAME) { $env:USBSHIELD_TEST_DB_USERNAME } else { 'usbshield_test' }
    $env:USBSHIELD_DB_PASSWORD = $env:USBSHIELD_TEST_DB_PASSWORD
    $env:USBSHIELD_ADMIN_PASSWORD = $password
    $env:USBSHIELD_ADMIN_USERNAME = 'week3-verifier'
    $env:USBSHIELD_ENROLLMENT_TOKEN = $bootstrap
    $server = Start-Process -FilePath $java -WindowStyle Hidden -PassThru -WorkingDirectory $PSScriptRoot -ArgumentList @(
        '-jar', 'target/usbshield-admin-server.jar', "--server.port=$Port", '--server.address=127.0.0.1', '--spring.profiles.active=prod', '--server.servlet.session.cookie.secure=false',
        "--spring.jpa.hibernate.ddl-auto=create"
    ) -RedirectStandardOutput "$evidence/server-runtime.log" -RedirectStandardError "$evidence/server-runtime-error.log"
    $ready = $false
    for ($i=0; $i -lt 60; $i++) {
        if ($server.HasExited) { throw 'Server exited before readiness.' }
        try { Invoke-RestMethod "$base/api/auth/csrf" -TimeoutSec 5 | Out-Null; $ready=$true; break }
        catch { Start-Sleep -Seconds 1 }
    }
    if (!$ready) { throw 'Server readiness timeout.' }
    $indexResponse = Invoke-WebRequest "$base/"
    if ($indexResponse.StatusCode -ne 200 -or $indexResponse.Content -notmatch '<div id="root">') { throw 'Packaged React index missing.' }
    $assetPaths = [regex]::Matches($indexResponse.Content, '(?:src|href)="(/assets/[^"]+)"')
    if ($assetPaths.Count -lt 2) { throw 'React asset references missing.' }
    foreach ($assetPath in $assetPaths) {
        $asset = Invoke-WebRequest "$base$($assetPath.Groups[1].Value)"
        if ($asset.StatusCode -ne 200 -or $asset.Content -match '<!doctype html>') { throw 'Missing asset served as HTML.' }
    }
    $navigation = Invoke-WebRequest "$base/events" -MaximumRedirection 0 -SkipHttpErrorCheck -ErrorAction SilentlyContinue
    if ($navigation.Headers.Location -notmatch '/#/events$') { throw 'SPA navigation mapping failed.' }
    $missingAsset = Invoke-WebRequest "$base/assets/missing.js" -SkipHttpErrorCheck
    if ($missingAsset.StatusCode -ne 404) { throw 'Missing asset must not fall back to SPA.' }
    foreach ($docsPath in @('/api-docs','/swagger-ui.html')) {
        $docsResponse = Invoke-WebRequest "$base$docsPath" -SkipHttpErrorCheck
        if ($docsResponse.StatusCode -ne 404) { throw "Production API docs should be disabled: $docsPath returned $($docsResponse.StatusCode)." }
    }
    'PASS: production JAR serves React index/assets; /events redirects; missing assets and Swagger/OpenAPI return 404.' | Set-Content "$evidence/07-static-http.txt"
    $web = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $csrf = Assert-Http '/api/auth/csrf' GET $null @{} 200 $web
    Assert-Http '/api/auth/me' GET $null @{} 401 | Out-Null
    Assert-Http '/api/auth/login' POST @{username='week3-verifier';password='wrong'} @{'X-CSRF-TOKEN'=$csrf.token} 401 $web | Out-Null
    Assert-Http '/api/auth/login' POST @{username='week3-verifier';password=$password} @{'X-CSRF-TOKEN'=$csrf.token} 200 $web | Out-Null
    $csrf = Assert-Http '/api/auth/csrf' GET $null @{} 200 $web
    $adminHeaders = @{'X-CSRF-TOKEN'=$csrf.token}
    Assert-Http '/api/auth/me' GET $null @{} 200 $web | Out-Null
    'PASS: real HTTP login, invalid password, session, me; credentials omitted.' | Set-Content "$evidence/02-admin-auth-http.txt"

    $a = Assert-Http '/api/agent/enroll' POST @{hostname='W2-A';machineId="A-$runId";enrollmentToken=$bootstrap} @{} 200
    $b = Assert-Http '/api/agent/enroll' POST @{hostname='W2-B';machineId="B-$runId";enrollmentToken=$bootstrap} @{} 200
    $ha = @{'X-Endpoint-Id'=$a.endpointId;'X-Agent-Token'=$a.agentToken}
    $hb = @{'X-Endpoint-Id'=$b.endpointId;'X-Agent-Token'=$b.agentToken}
    Assert-Http '/api/agent/policy' GET $null @{} 401 | Out-Null
    Assert-Http '/api/agent/policy' GET $null @{'X-Endpoint-Id'=$b.endpointId;'X-Agent-Token'=$a.agentToken} 401 | Out-Null
    Assert-Http '/api/agent/heartbeat' POST @{endpointId=$b.endpointId} $ha 403 | Out-Null
    $heartbeat = Assert-Http '/api/agent/heartbeat' POST @{endpointId=$a.endpointId;currentPolicyVersion=999} $ha 200
    if ($heartbeat.lastAppliedPolicyVersion -ne 0) { throw 'Heartbeat incorrectly changed applied policy.' }
    'PASS: enrollment; missing token 401; A token/B identity 401; spoofed heartbeat 403; heartbeat does not ACK.' | Set-Content "$evidence/03-agent-auth-http.txt"

    $payload = Get-Content "$repo/docs/agent-contract/sample-usb-event.json" -Raw | ConvertFrom-Json
    $payload.endpointId = $a.endpointId
    $ingest = Assert-Http '/api/agent/events' POST $payload $ha 200
    $page = Assert-Http "/api/admin/events?endpointId=$($a.endpointId)&linuxUsername=usbdev&decision=ALLOWED&size=1" GET $null @{} 200 $web
    if ($page.totalElements -ne 1 -or $page.content[0].sessionId -ne '21' -or $page.content[0].decision -ne 'ALLOWED') { throw 'Persisted event mismatch.' }
    $deviceId = $page.content[0].usbDeviceId
    'PASS: enrolled-reference M2 payload persisted, filtered, paged; session 21, mouse ALLOWED. Sample file unchanged; no physical USB test.' | Set-Content "$evidence/06-event-ingestion-query.txt"
    $url = "/api/admin/endpoints/$($a.endpointId)/whitelist"
    Assert-Http $url POST @{usbDeviceId=$deviceId} @{} 403 $web | Out-Null
    $entry = Assert-Http $url POST @{usbDeviceId=$deviceId} $adminHeaders 200 $web
    Assert-Http $url POST @{usbDeviceId=$deviceId} $adminHeaders 200 $web | Out-Null
    $pa = Assert-Http '/api/agent/policy' GET $null $ha 200
    $pb = Assert-Http '/api/agent/policy' GET $null $hb 200
    if ($pa.policyVersion -ne 2 -or $pa.whitelistedDevices.Count -ne 1 -or $pb.policyVersion -ne 1 -or $pb.whitelistedDevices.Count -ne 0) { throw 'Policy isolation/version mismatch.' }
    Assert-Http '/api/agent/policy/ack' POST @{appliedPolicyVersion=3} $ha 409 | Out-Null
    Assert-Http '/api/agent/policy/ack' POST @{appliedPolicyVersion=-1} $ha 400 | Out-Null
    Assert-Http '/api/agent/policy/ack' POST @{appliedPolicyVersion=2} $ha 200 | Out-Null
    Assert-Http '/api/agent/policy/ack' POST @{appliedPolicyVersion=1} $ha 409 | Out-Null
    Assert-Http "$url/$($entry.id)" DELETE $null $adminHeaders 204 $web | Out-Null
    Assert-Http "$url/$($entry.id)" DELETE $null $adminHeaders 204 $web | Out-Null
    $pa = Assert-Http '/api/agent/policy' GET $null $ha 200
    $pb = Assert-Http '/api/agent/policy' GET $null $hb 200
    if ($pa.policyVersion -ne 3 -or $pa.whitelistedDevices.Count -ne 0 -or $pb.policyVersion -ne 1) { throw 'Revoke isolation mismatch.' }
    'PASS: real HTTP allow/duplicate/revoke/idempotent revoke; A versions 1->2->3; B stays 1/empty. CSRF missing 403.' | Set-Content "$evidence/04-policy-A-B-isolation.txt"
    'PASS: negative ACK 400, future/stale 409, valid ACK 200; heartbeat 999 leaves applied=0. This proves server validation, not USBGuard apply.' | Set-Content "$evidence/05-policy-version-ack.txt"
    Assert-Http '/api/auth/logout' POST @{} $adminHeaders 200 $web | Out-Null
    Assert-Http '/api/auth/me' GET $null @{} 401 $web | Out-Null
    'PASS: logout invalidates session; me returns 401.' | Add-Content "$evidence/02-admin-auth-http.txt"
    'PASS: Week 3 standalone server + disposable PostgreSQL + session/CSRF + authenticated Agent APIs + A/B policy + event queries.'
} finally {
    if ($server -and !$server.HasExited) { Stop-Process -Id $server.Id }
    $env:USBSHIELD_DB_PASSWORD = $savedDbPassword
    $env:USBSHIELD_DB_URL = $savedDbUrl
    $env:USBSHIELD_DB_USERNAME = $savedDbUsername
    $env:USBSHIELD_ADMIN_PASSWORD = $savedPassword
    $env:USBSHIELD_ADMIN_USERNAME = $savedUser
    $env:USBSHIELD_ENROLLMENT_TOKEN = $savedBootstrap
}

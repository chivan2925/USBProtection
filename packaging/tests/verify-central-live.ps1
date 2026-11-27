#requires -Version 7.6
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BundleZip,
    [string]$NginxPath = 'C:/nginx/nginx.exe',
    [string]$OpenSslPath = 'C:/Program Files/Git/usr/bin/openssl.exe',
    [string]$PsqlPath = 'C:/Program Files/PostgreSQL/18/bin/psql.exe'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
if (-not $IsWindows) { throw 'Run the native Central smoke test on Windows.' }
if ($env:USBSHIELD_TEST_DB_HOST -and $env:USBSHIELD_TEST_DB_HOST -ne '127.0.0.1') {
    throw 'Live smoke requires the dedicated PostgreSQL test database on IPv4 loopback.'
}
if ($env:USBSHIELD_TEST_DB_USERNAME -cne 'usbshield_test' -or
    [string]::IsNullOrWhiteSpace($env:USBSHIELD_TEST_DB_PASSWORD)) {
    throw 'Set the dedicated usbshield_test role and its password.'
}
$dbPort = if ($env:USBSHIELD_TEST_DB_PORT) { $env:USBSHIELD_TEST_DB_PORT } else { '5432' }
if ($dbPort -notmatch '^\d{1,5}$' -or [int]$dbPort -lt 1 -or [int]$dbPort -gt 65535) {
    throw 'Invalid test PostgreSQL port.'
}

function Resolve-Binary([string]$Path, [string]$Name) {
    if (Test-Path -LiteralPath $Path -PathType Leaf) { return (Resolve-Path -LiteralPath $Path).Path }
    return (Get-Command $Name -CommandType Application | Select-Object -First 1).Source
}
function New-LocalPort {
    $socket = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $socket.Start()
    try { return $socket.LocalEndpoint.Port } finally { $socket.Stop() }
}

$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$zip = (Resolve-Path -LiteralPath $BundleZip).Path
$nginx = Resolve-Binary $NginxPath 'nginx.exe'
$openssl = Resolve-Binary $OpenSslPath 'openssl.exe'
$psql = Resolve-Binary $PsqlPath 'psql.exe'
$curl = (Get-Command curl.exe -CommandType Application | Select-Object -First 1).Source
$java = if ($env:JAVA_HOME) { (Resolve-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe')).Path } else {
    (Get-Command java.exe -CommandType Application | Select-Object -First 1).Source
}
$testClasses = Join-Path $root 'admin-desktop/target/test-classes'
if (!(Test-Path -LiteralPath (Join-Path $testClasses 'com/group/usbshield/desktop/CentralLiveProbe.class'))) {
    throw 'Compile the Desktop test classes from the same candidate before running the packaged smoke test.'
}
$targetItem = New-Item -ItemType Directory -Path (Join-Path $root 'target') -Force
if ($targetItem.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Unsafe test output directory.' }
$target = $targetItem.FullName
$schema = 'usbshield_p2smoke_' + [guid]::NewGuid().ToString('N')
$fixture = Join-Path $target $schema
New-Item -ItemType Directory -Path $fixture, (Join-Path $fixture 'logs'), (Join-Path $fixture 'temp') | Out-Null
$prefix = $fixture.Replace('\','/') + '/'
$server = $null
$proxy = $null
$databaseVerified = $false
$savedEnv = @{}
foreach ($name in @('PGPASSWORD','MSYS2_ARG_CONV_EXCL','USBSHIELD_DESKTOP_CA','USBSHIELD_CENTRAL_URL',
                    'P2_TEST_ADMIN_PASSWORD','P2_TEST_AGENT_ORIGIN')) {
    $savedEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$dbArgs = @('-w','-h','127.0.0.1','-p',$dbPort,'-U','usbshield_test','-d','usbshield_test','-v','ON_ERROR_STOP=1')
try {
    $env:PGPASSWORD = $env:USBSHIELD_TEST_DB_PASSWORD
    $dbIdentity = & $psql @dbArgs -tAc "select current_database() || '/' || current_user"
    if ($LASTEXITCODE -ne 0 -or $dbIdentity -cne 'usbshield_test/usbshield_test') {
        throw 'Test database identity check failed.'
    }
    $databaseVerified = $true

    $expanded = Join-Path $fixture 'bundle'
    [IO.Compression.ZipFile]::ExtractToDirectory($zip, $expanded)
    $layouts = @(Get-ChildItem -LiteralPath $expanded -Directory)
    if ($layouts.Count -ne 1) { throw 'Expected one Windows Central bundle layout.' }
    $bundle = $layouts[0].FullName
    $serverJar = (Resolve-Path -LiteralPath (Join-Path $bundle 'usbshield-admin-server.jar')).Path
    $nativeProbe = (Resolve-Path -LiteralPath (Join-Path $bundle 'USBShield/USBShieldCheck.exe')).Path
    $launcher = (Resolve-Path -LiteralPath (Join-Path $bundle 'Start-USBShield.ps1')).Path
    $denied = & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -NonInteractive -File $launcher -CaFile unused -StartServices 2>&1
    if ($LASTEXITCODE -eq 0 -or ($denied | Out-String) -notmatch 'Start services with -StartServices -CheckOnly') {
        throw 'Launcher did not separate service startup from GUI launch.'
    }

    $ports = @()
    while ($ports.Count -lt 3) {
        $port = New-LocalPort
        if ($ports -notcontains $port) { $ports += $port }
    }
    $backendPort, $adminPort, $agentPort = $ports
    $cert = Join-Path $fixture 'ca.pem'
    $key = Join-Path $fixture 'fixture-key.pem'
    $env:MSYS2_ARG_CONV_EXCL = '*'
    & $openssl req -x509 -newkey 'rsa:2048' -nodes -keyout $key -out $cert -days 2 -subj '/CN=USBShield P2 Fixture' -addext 'basicConstraints=critical,CA:TRUE' -addext 'subjectAltName=IP:127.0.0.1,DNS:usbshield-p2-fixture.invalid' 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot generate the isolated TLS fixture certificate.' }
    $nginxBody = [IO.File]::ReadAllText((Join-Path $bundle 'usbshield-https-acl.conf.example'))
    $nginxBody = $nginxBody.Replace('listen 127.0.0.1:443', "listen 127.0.0.1:$adminPort")
    $nginxBody = $nginxBody.Replace('listen @@CENTRAL_LAN_IPV4@@:443', "listen 127.0.0.1:$agentPort")
    $nginxBody = $nginxBody.Replace('@@CLIENT_CIDR@@', '127.0.0.1/32').Replace('@@USBSHIELD_DNS_NAME@@', 'usbshield-p2-fixture.invalid')
    $nginxBody = $nginxBody.Replace('127.0.0.1:18080', "127.0.0.1:$backendPort")
    $nginxBody = $nginxBody.Replace('@@FULLCHAIN_PEM_PATH@@', '"' + $cert.Replace('\','/') + '"')
    $nginxBody = $nginxBody.Replace('@@PRIVATE_KEY_PEM_PATH@@', '"' + $key.Replace('\','/') + '"')
    $config = @"
worker_processes 1;
pid logs/nginx.pid;
events { worker_connections 64; }
http {
$nginxBody
}
"@
    [IO.File]::WriteAllText((Join-Path $fixture 'nginx.conf'), $config, [Text.UTF8Encoding]::new($false))
    & $nginx -p $prefix -c nginx.conf -t
    if ($LASTEXITCODE -ne 0) { throw 'Nginx rejected the packaged route configuration.' }

    $syntheticAdmin = [guid]::NewGuid().ToString('N')
    $settings = @{}
    foreach ($item in (Get-ChildItem Env:)) {
        if ($item.Name -match '^(USBSHIELD_DB_|USBSHIELD_ADMIN_|SPRING_|FLYWAY_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)') { $settings[$item.Name] = $null }
    }
    $settings.USBSHIELD_DB_URL = "jdbc:postgresql://127.0.0.1:$dbPort/usbshield_test?currentSchema=$schema"
    $settings.USBSHIELD_DB_USERNAME = 'usbshield_test'
    $settings.USBSHIELD_DB_PASSWORD = $env:USBSHIELD_TEST_DB_PASSWORD
    $settings.USBSHIELD_ADMIN_USERNAME = 'p2-synthetic-admin'
    $settings.USBSHIELD_ADMIN_PASSWORD = $syntheticAdmin
    $settings.SPRING_FLYWAY_SCHEMAS = $schema
    $settings.SPRING_FLYWAY_DEFAULT_SCHEMA = $schema
    $settings.SPRING_FLYWAY_CREATE_SCHEMAS = 'true'
    $settings.SPRING_DATASOURCE_HIKARI_SCHEMA = $schema
    $serverArgs = @('-jar', ('"' + $serverJar + '"'), '--spring.profiles.active=prod', "--server.port=$backendPort")
    $server = Start-Process -FilePath $java -ArgumentList $serverArgs -Environment $settings -WorkingDirectory $fixture -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $fixture 'central.log') -RedirectStandardError (Join-Path $fixture 'central.err')
    $proxy = Start-Process -FilePath $nginx -ArgumentList @('-p', ('"' + $prefix + '"'), '-c', 'nginx.conf') -WorkingDirectory $fixture -WindowStyle Hidden -PassThru
    $adminOrigin = "https://127.0.0.1:$adminPort"
    $agentOrigin = "https://usbshield-p2-fixture.invalid:$agentPort"
    $agentResolve = 'usbshield-p2-fixture.invalid:{0}:127.0.0.1' -f $agentPort
    $env:USBSHIELD_DESKTOP_CA = $cert
    $env:USBSHIELD_CENTRAL_URL = $adminOrigin
    $curlArgs = @('--silent','--noproxy','*','--max-time','2','--cacert',$cert,'--output','NUL','--write-out','%{http_code}')
    $ready = $false
    for ($attempt = 0; $attempt -lt 100; $attempt++) {
        $server.Refresh()
        if ($server.HasExited) { throw 'Central test JVM exited before readiness.' }
        $status = & $curl @curlArgs "$adminOrigin/api/auth/csrf"
        if ($status -eq '200') { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw 'Packaged Central did not become ready through Nginx.' }
    & $nativeProbe
    if ($LASTEXITCODE -ne 0) { throw "Native packaged readiness failed with exit code $LASTEXITCODE." }

    function Expect-Status([int]$Expected, [string]$Uri, [string[]]$Extra = @()) {
        $status = & $curl @curlArgs @Extra $Uri
        if ($LASTEXITCODE -ne 0 -or $status -ne "$Expected") { throw "Unexpected HTTPS status $status for $Uri (expected $Expected)." }
    }
    Expect-Status 404 "$adminOrigin/"
    Expect-Status 404 "$adminOrigin/api/agent/policy"
    Expect-Status 401 "$adminOrigin/api/admin/endpoints/page"
    Expect-Status 403 "$agentOrigin/api/auth/csrf" @('--resolve',$agentResolve)
    Expect-Status 403 "$agentOrigin/api/admin/endpoints/page" @('--resolve',$agentResolve)
    Expect-Status 426 "$agentOrigin/api/agent/policy" @('--resolve',$agentResolve)
    Expect-Status 401 "$agentOrigin/api/agent/policy" @('--resolve',$agentResolve,'-H','X-USBShield-Protocol: 2')

    $app = Join-Path $bundle 'USBShield/app'
    $classpath = @($testClasses,(Join-Path $app 'usbshield-desktop.jar'),(Join-Path $app 'lib/*')) -join ';'
    $env:P2_TEST_ADMIN_PASSWORD = $syntheticAdmin
    $env:P2_TEST_AGENT_ORIGIN = "https://127.0.0.1:$agentPort"
    & $java -cp $classpath 'com.group.usbshield.desktop.CentralLiveProbe'
    if ($LASTEXITCODE -ne 0) { throw 'Shipped Desktop client failed API parity against packaged Central.' }
    Write-Host '[OK] Native readiness, split TLS listeners and authenticated API parity on synthetic loopback.'
} finally {
    if ($proxy -and !$proxy.HasExited) {
        $pidFile = Join-Path $fixture 'logs/nginx.pid'
        if (Test-Path -LiteralPath $pidFile) {
            $pidText = [IO.File]::ReadAllText($pidFile).Trim()
            if ($pidText -eq "$($proxy.Id)") { & $nginx -p $prefix -c nginx.conf -s quit }
        }
        if (!$proxy.WaitForExit(5000)) { $proxy.Kill($true); [void]$proxy.WaitForExit(10000) }
    }
    if ($server -and !$server.HasExited) { $server.Kill($true); [void]$server.WaitForExit(10000) }
    try {
        if ($databaseVerified) {
            if ($schema -notmatch '^usbshield_p2smoke_[0-9a-f]{32}$') { throw 'Unsafe test schema name.' }
            $identity = & $psql @dbArgs -tAc "select current_database() || '/' || current_user"
            if ($LASTEXITCODE -ne 0 -or $identity -cne 'usbshield_test/usbshield_test') { throw 'Cleanup database identity check failed.' }
            & $psql @dbArgs -q -c "DROP SCHEMA IF EXISTS $schema CASCADE"
            if ($LASTEXITCODE -ne 0) { throw 'Failed to clean the smoke test schema.' }
        }
    } finally {
        foreach ($name in $savedEnv.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnv[$name], 'Process') }
        $item = Get-Item -LiteralPath $fixture
        if ($item.FullName -ne (Join-Path $target $schema) -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Unsafe smoke fixture directory.'
        }
        Remove-Item -LiteralPath $item.FullName -Recurse -Force
    }
}

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$expectedSha = '5f32d415fc5a5ef6fb8e7b4d1867041eb4d05487'
$actualSha = (& git -C $repoRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $actualSha -ne $expectedSha) {
    throw "Expected repository HEAD $expectedSha, got '$actualSha'. Stop to avoid editing different main revision."
}

$targets = @(
    'docs/qa/WEEK3_QA_SUMMARY.md',
    'docs/qa/WEEK3_TEST_INVENTORY.md',
    'docs/admin-web/WEEK3_UI_UX_HARDENING.md',
    'docs/client-agent/WEEK3_PACKAGING_AND_RETRY.md',
    'docs/Review.txt',
    'docs/Seminar_Week3_Review.txt'
)

foreach ($relative in $targets) {
    $dirty = & git -C $repoRoot status --porcelain -- $relative
    if ($LASTEXITCODE -ne 0 -or $dirty) {
        throw "Refusing to overwrite modified/untracked target: $relative. Commit/stash first."
    }
}

function Replace-Required([string]$Value, [string]$Original, [string]$Replacement, [string]$Where) {
    if ($Value.Contains($Original)) { return $Value.Replace($Original, $Replacement) }
    if ($Value.Contains($Replacement)) { return $Value }
    throw "Expected text not found in '$Where': $Original"
}

$pending = @{}
$qaPath = 'docs/qa/WEEK3_QA_SUMMARY.md'
$qa = [IO.File]::ReadAllText((Join-Path $repoRoot $qaPath))
$qa = Replace-Required $qa 'H02 M4, M3 package provenance, and the H05 group gate remain OPEN.' 'M3 package provenance and the H05 group gate remain OPEN. H02 M4 is DONE (owner acknowledgment dated 2026-10-28).' $qaPath
$qa = Replace-Required $qa '| H02 M4 shared UI | Technical tests/review PASS; owner acknowledgment not newly recorded | M4 confirms integrated shared-UI handoff. |' '| H02 M4 shared UI | **DONE** — M4 owner acknowledgment recorded 2026-10-28 | See `docs/admin-web/WEEK3_UI_UX_HARDENING.md` H02 section; no additional M4 implementation required. |' $qaPath
$pending[$qaPath] = $qa

$inventoryPath = 'docs/qa/WEEK3_TEST_INVENTORY.md'
$inventory = [IO.File]::ReadAllText((Join-Path $repoRoot $inventoryPath))
$inventory = Replace-Required $inventory 'H02 M4 and group gate: OPEN.' 'H02 M4: DONE (acknowledged 2026-10-28); H05 group gate: OPEN.' $inventoryPath
$inventory = Replace-Required $inventory 'H02/H04/H05 remain open' 'H02 DONE; H04 `.deb` provenance and H05 remain open' $inventoryPath
$pending[$inventoryPath] = $inventory

$frontendPath = 'docs/admin-web/WEEK3_UI_UX_HARDENING.md'
$frontend = [IO.File]::ReadAllText((Join-Path $repoRoot $frontendPath))
$frontend = Replace-Required $frontend '— **OPEN, thuộc M3**.' '— **RESOLVED 2026-10-28, thuộc M3**; see `docs/qa/WEEK3_M5_M3_PERSISTENCE_CLOSURE_20261028.md`.' $frontendPath
$pending[$frontendPath] = $frontend

$clientPath = 'docs/client-agent/WEEK3_PACKAGING_AND_RETRY.md'
$client = [IO.File]::ReadAllText((Join-Path $repoRoot $clientPath))
$clientNote = @'

## Week 3 closeout overlay — 2026-10-28

The 2026-10-28 patch changes `packaging/agent-deb/DEBIAN/postinst` to validate an explicit fail-closed `ImplicitPolicyTarget=block` (including whitespace and duplicate directives), reject unsafe configurations without overwriting foreign policy, and report USBGuard service startup errors. This is **NOT EXECUTED on Ubuntu** and requires M3/M2 installer review plus the Windows `bash -n` and packaging static checks listed in `docs/qa/WEEK3_FINAL_OWNER_CLOSEOUT_PENDING.md`. Historical PASS logs above predate this installer edit and must not be recast as a fresh test.
'@
if (-not $client.Contains('## Week 3 closeout overlay — 2026-10-28')) {
    $client = $client + $clientNote
}
$pending[$clientPath] = $client

$archiveBanner = "SUPERSEDED HISTORICAL REVIEW - DO NOT USE AS CURRENT WEEK 3 GATE.`r`nSee docs/qa/WEEK3_FINAL_OWNER_CLOSEOUT_PENDING.md and docs/qa/WEEK3_QA_SUMMARY.md for current verified status (H02 DONE; M5-W3-07 RESOLVED; H05 PENDING).`r`n`r`n"
foreach ($relative in @('docs/Review.txt', 'docs/Seminar_Week3_Review.txt')) {
    $current = [IO.File]::ReadAllText((Join-Path $repoRoot $relative))
    if (-not $current.StartsWith('SUPERSEDED HISTORICAL REVIEW')) {
        $pending[$relative] = $archiveBanner + $current
    } else {
        $pending[$relative] = $current
    }
}

$encoding = New-Object System.Text.UTF8Encoding($false)
foreach ($relative in $targets) {
    $fullPath = Join-Path $repoRoot $relative
    $current = [IO.File]::ReadAllText($fullPath)
    if ($current -ne $pending[$relative]) {
        [IO.File]::WriteAllText($fullPath, $pending[$relative], $encoding)
        Write-Host "UPDATED $relative"
    } else {
        Write-Host "UNCHANGED $relative"
    }
}
Write-Host 'Week 3 QA/handoff sync completed. H05 remains PENDING owner sign-off.'

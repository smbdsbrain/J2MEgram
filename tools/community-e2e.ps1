<#
.SYNOPSIS
    Full reversible live E2E for community and forum administration.

.DESCRIPTION
    Runs only with explicit fixture arguments. The administrator API session
    exercises rights, permissions, membership, invite links and topic CRUD.
    An exact packaged JAR under the target account then submits/retries join
    requests, leaves and joins. Every artifact has an unconditional cleanup
    that restores membership and revokes all active fixture links.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ChatTitle,
    [Parameter(Mandatory = $true)][string]$TargetUsername,
    [string]$TargetProfile = 'bigchats',
    [string[]]$ArtifactName = @(
        'J2MEgram-1.6.0-community-e2e',
        'J2MEgram-1.6.0-community-e2e-min'
    ),
    [string[]]$JavaArgs = @('-Xmx32m'),
    [int]$TargetMutationDelaySeconds = 50,
    [switch]$SkipBuild,
    [switch]$SkipApiLifecycle
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_env.ps1')

if (-not $Jdk8Home) { throw 'JDK 8 is required' }
if ($ArtifactName.Count -lt 1) { throw 'at least one artifact is required' }

$root = Join-RepoPath 'local' 'community-e2e'
New-Item -ItemType Directory -Force -Path $root | Out-Null
$run = Join-Path $root (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Path $run | Out-Null

if (-not $SkipBuild) {
    for ($i = 0; $i -lt $ArtifactName.Count; $i++) {
        $release = $ArtifactName[$i].EndsWith('-min')
        if ($release) {
            & (Join-Path $PSScriptRoot 'build.ps1') -Target tg `
                -Env production -Clean -ArtifactName $ArtifactName[$i] -Release
        }
        else {
            & (Join-Path $PSScriptRoot 'build.ps1') -Target tg `
                -Env production -Clean -ArtifactName $ArtifactName[$i]
        }
        if ($LASTEXITCODE -ne 0) { throw "artifact build failed at index $i" }
    }
}

& (Join-Path $PSScriptRoot 'build.ps1') -Profile desktop -Env production
if ($LASTEXITCODE -ne 0) { throw 'desktop live harness build failed' }

$classes = Join-RepoPath 'build' 'desktop' 'classes'
$tests = Join-RepoPath 'build' 'desktop' 'test-classes'
$cp = @($classes, $tests) -join $PathSep
$driver = Join-Path $PSScriptRoot 'drive-emulator.ps1'

function Invoke-Admin([string]$mode, [string]$state = '') {
    $args = @('-cp', $cp, 'tgtest.LiveCommunityAdministrationTest',
        $mode, $ChatTitle, $TargetUsername)
    if ($state) { $args += $state }
    & $Jdk8Java @args | ForEach-Object { Write-Host $_ }
    $code = $LASTEXITCODE
    return $code
}

function Set-Private([string]$path, [string]$value) {
    [IO.File]::WriteAllText($path, $value, [Text.UTF8Encoding]::new($false))
}

function Invoke-Target([string]$artifact, [string]$state,
        [string]$action) {
    Set-Private (Join-Path $state 'target-action') $action
    & $driver -Scenario rc-community-target -Env production `
        -EmulatorProfile $TargetProfile -SkipBuild -ArtifactName $artifact `
        -StateDir $state -Role b -NoDiagTail -JavaArgs $JavaArgs |
        ForEach-Object { Write-Host $_ }
    $code = $LASTEXITCODE
    return $code
}

# Prove the selected saved profile is exactly the authorized target without
# printing the username into process output.
$identity = Join-Path $run 'identity'
New-Item -ItemType Directory -Path $identity | Out-Null
& $driver -Scenario rc-identity -Env production `
    -EmulatorProfile $TargetProfile -SkipBuild `
    -ArtifactName $ArtifactName[0] -StateDir $identity -Role b `
    -NoDiagTail -JavaArgs $JavaArgs
if ($LASTEXITCODE -ne 0) { throw 'target profile identity could not be read' }
$actual = [IO.File]::ReadAllText((Join-Path $identity 'b.username')).Trim()
$wanted = $TargetUsername.Trim().TrimStart('@')
if ($actual -ine $wanted) { throw 'target profile does not match fixture user' }
Remove-Item -LiteralPath (Join-Path $identity 'b.username') -Force
Write-Ok 'target profile identity matches the authorized fixture'

if (-not $SkipApiLifecycle) {
    Write-Step 'community API lifecycle'
    if ((Invoke-Admin 'full') -ne 0) { throw 'community API lifecycle failed' }
}

foreach ($artifact in $ArtifactName) {
    $safe = $artifact -replace '[^A-Za-z0-9._-]', '-'
    $state = Join-Path $run $safe
    New-Item -ItemType Directory -Path $state | Out-Null
    $flowPassed = $false
    $cleanupCode = 1
    try {
        Write-Step "community packaged target flow :: $artifact"
        if ((Invoke-Admin 'prepare' $state) -ne 0) {
            throw 'request fixture preparation failed'
        }
        if ((Invoke-Target $artifact $state 'request-1') -ne 0) {
            throw 'first packaged join request failed'
        }
        if ((Invoke-Admin 'reject' $state) -ne 0) {
            throw 'join request rejection failed'
        }
        Start-Sleep -Seconds $TargetMutationDelaySeconds
        if ((Invoke-Target $artifact $state 'request-2') -ne 0) {
            throw 'second packaged join request failed'
        }
        if ((Invoke-Admin 'approve' $state) -ne 0) {
            throw 'join request approval failed'
        }
        Start-Sleep -Seconds 8
        if ((Invoke-Admin 'ban-cycle' $state) -ne 0) {
            throw 'ban/unban cycle failed'
        }
        Start-Sleep -Seconds $TargetMutationDelaySeconds
        if ((Invoke-Target $artifact $state 'direct-join') -ne 0) {
            throw 'packaged rejoin after unban failed'
        }
        if ((Invoke-Admin 'ensure-member' $state) -ne 0) {
            throw 'member verification after unban failed'
        }
        Start-Sleep -Seconds 8
        if ((Invoke-Target $artifact $state 'leave') -ne 0) {
            throw 'packaged leave failed'
        }
        Start-Sleep -Seconds $TargetMutationDelaySeconds
        if ((Invoke-Target $artifact $state 'direct-join') -ne 0) {
            throw 'packaged private rejoin failed'
        }
        if ((Invoke-Admin 'ensure-member' $state) -ne 0) {
            throw 'member verification after leave failed'
        }
        $public = [IO.File]::ReadAllText(
                (Join-Path $state 'public-address')).Trim() -eq 'true'
        if ($public) {
            if ((Invoke-Target $artifact $state 'leave') -ne 0) {
                throw 'pre-public-join leave failed'
            }
            if ((Invoke-Target $artifact $state 'public-join') -ne 0) {
                throw 'packaged public join failed'
            }
        }
        $flowPassed = $true
    }
    finally {
        Start-Sleep -Seconds 8
        $cleanupCode = Invoke-Admin 'cleanup' $state
        if ($cleanupCode -ne 0) {
            Start-Sleep -Seconds 8
            $cleanupCode = Invoke-Admin 'cleanup' $state
        }
        if ($cleanupCode -ne 0) {
            Write-Step "packaged recovery join :: $artifact"
            if ((Invoke-Target $artifact $state 'direct-join') -eq 0) {
                Start-Sleep -Seconds 8
                $cleanupCode = Invoke-Admin 'cleanup' $state
            }
        }
        if ($cleanupCode -ne 0) {
            Write-Step "packaged request recovery :: $artifact"
            if ((Invoke-Admin 'request-recover' $state) -eq 0 -and
                    (Invoke-Target $artifact $state 'request-1') -eq 0 -and
                    (Invoke-Admin 'approve' $state) -eq 0) {
                Start-Sleep -Seconds 8
                $cleanupCode = Invoke-Admin 'cleanup' $state
            }
        }
        if ($cleanupCode -ne 0) {
            Write-Bad "community cleanup failed for $artifact"
        }
    }
    if (-not $flowPassed -or $cleanupCode -ne 0) {
        throw "community flow failed for $artifact"
    }
    $evidence = @(
        "artifact=$artifact",
        'participant-paging=pass',
        'request-reject=pass',
        'request-approve=pass',
        'leave=pass',
        'private-join=pass',
        'cleanup=pass'
    )
    [IO.File]::WriteAllText((Join-Path $state 'evidence.txt'),
        ($evidence -join "`n") + "`n", [Text.Encoding]::UTF8)
    Write-Ok "community packaged target flow passed: $artifact"
}

Write-Ok 'FULL COMMUNITY E2E PASSED'
Write-Host "         private evidence: $run" -ForegroundColor Yellow

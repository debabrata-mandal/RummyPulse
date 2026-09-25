[CmdletBinding()]
param(
    [switch]$Background,
    [Parameter(DontShow = $true)]
    [switch]$Child
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeDirectory = Join-Path $repoRoot '.firebase-emulator-runtime'
$dataDirectory = Join-Path $repoRoot '.firebase-emulator-data'
$stateFile = Join-Path $runtimeDirectory 'emulators.json'
$outputLog = Join-Path $runtimeDirectory 'emulators.out.log'
$errorLog = Join-Path $runtimeDirectory 'emulators.err.log'
$firebaseConfig = Join-Path $repoRoot 'firebase.json'
$projectConfig = Join-Path $repoRoot '.firebaserc'

function Get-ConfiguredProjectId {
    if (-not (Test-Path -LiteralPath $projectConfig)) {
        throw 'Missing .firebaserc. Configure a default Firebase project before starting emulators.'
    }
    $config = Get-Content -LiteralPath $projectConfig -Raw | ConvertFrom-Json
    $projectId = $config.projects.default
    if ([string]::IsNullOrWhiteSpace($projectId)) {
        throw 'The default Firebase project is missing from .firebaserc.'
    }
    return $projectId
}

function Assert-LocalPrerequisites {
    if (-not (Test-Path -LiteralPath $firebaseConfig)) {
        throw "Missing firebase.json at $repoRoot."
    }
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        throw 'Node.js is required. Install Node.js and retry.'
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw 'Java JDK 11 or newer is required for the Firestore emulator.'
    }
    if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
        throw 'npm is required to install the Functions dependencies.'
    }
}

function Install-FunctionDependencies {
    $modulesDirectory = Join-Path $repoRoot 'functions\node_modules'
    if (Test-Path -LiteralPath $modulesDirectory) {
        return
    }
    Write-Host 'Installing Cloud Functions dependencies...'
    & npm.cmd --prefix (Join-Path $repoRoot 'functions') ci
    if ($LASTEXITCODE -ne 0) {
        throw "npm ci failed with exit code $LASTEXITCODE."
    }
}

function Invoke-FirebaseCli([string[]]$Arguments) {
    $installedFirebase = Get-Command firebase.cmd -ErrorAction SilentlyContinue
    if ($installedFirebase) {
        & $installedFirebase.Source @Arguments | Out-Host
        return $LASTEXITCODE
    }
    $npx = Get-Command npx.cmd -ErrorAction SilentlyContinue
    if (-not $npx) {
        throw 'Firebase CLI is not installed and npx is unavailable.'
    }
    Write-Host 'Firebase CLI is not installed globally; using npx firebase-tools.'
    & $npx.Source --yes firebase-tools @Arguments | Out-Host
    return $LASTEXITCODE
}

function Start-FirebaseEmulators {
    Assert-LocalPrerequisites
    Install-FunctionDependencies
    $projectId = Get-ConfiguredProjectId
    New-Item -ItemType Directory -Force -Path $dataDirectory | Out-Null

    $arguments = @(
        'emulators:start',
        '--project', $projectId,
        '--only', 'auth,firestore,functions',
        '--export-on-exit', $dataDirectory
    )
    if (Test-Path -LiteralPath (Join-Path $dataDirectory 'firebase-export-metadata.json')) {
        $arguments += @('--import', $dataDirectory)
    }

    Write-Host "Starting local Firebase services for $projectId..."
    Write-Host 'Emulator UI: http://127.0.0.1:4000'
    Write-Host 'Press Ctrl+C for a graceful stop and local-data export.'
    Push-Location $repoRoot
    try {
        $exitCode = Invoke-FirebaseCli $arguments
        if ($exitCode -ne 0) {
            throw "Firebase emulators exited with code $exitCode."
        }
    } finally {
        Pop-Location
    }
}

if ($Background -and -not $Child) {
    Assert-LocalPrerequisites
    New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null
    if (Test-Path -LiteralPath $stateFile) {
        $existing = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
        if (Get-Process -Id $existing.processId -ErrorAction SilentlyContinue) {
            throw "Local emulators already appear to be running (PID $($existing.processId))."
        }
        Remove-Item -LiteralPath $stateFile -Force
    }

    $pwsh = (Get-Process -Id $PID).Path
    $childArguments = @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', "`"$PSCommandPath`"",
        '-Child'
    )
    $process = Start-Process -FilePath $pwsh -ArgumentList $childArguments `
        -WorkingDirectory $repoRoot -WindowStyle Hidden `
        -RedirectStandardOutput $outputLog -RedirectStandardError $errorLog -PassThru
    @{
        processId = $process.Id
        startedAt = (Get-Date).ToUniversalTime().ToString('o')
        scriptPath = $PSCommandPath
        projectId = Get-ConfiguredProjectId
        hubUrl = 'http://127.0.0.1:4400'
        outputLog = $outputLog
        errorLog = $errorLog
    } | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8
    Write-Host "Local emulators are starting in the background (PID $($process.Id))."
    Write-Host "Logs: $outputLog and $errorLog"
    Write-Host 'Emulator UI: http://127.0.0.1:4000'
    exit 0
}

Start-FirebaseEmulators

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeDirectory = Join-Path $repoRoot '.firebase-emulator-runtime'
$stateFile = Join-Path $runtimeDirectory 'emulators.json'

if (-not (Test-Path -LiteralPath $stateFile)) {
    Write-Host 'No background emulator session is recorded. If it is running in the foreground, press Ctrl+C in that terminal.'
    exit 0
}

$state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
$process = Get-CimInstance Win32_Process -Filter "ProcessId = $($state.processId)" -ErrorAction SilentlyContinue
if (-not $process) {
    Remove-Item -LiteralPath $stateFile -Force
    Write-Host 'The recorded emulator process is no longer running. Removed stale state.'
    exit 0
}

$expectedScript = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'start-local.ps1'))
$commandLine = [string]$process.CommandLine
if ($commandLine -notlike "*$expectedScript*" -or $commandLine -notmatch '(?i)-Child') {
    throw "Refusing to stop PID $($state.processId): it is not the recorded RummyPulse emulator launcher."
}

Write-Host 'Requesting graceful Firebase Emulator Suite shutdown...'
$graceful = $false
try {
    Invoke-WebRequest -Uri "$($state.hubUrl)/__/quitquitquit" -Method Get -TimeoutSec 5 | Out-Null
    $graceful = $true
} catch {
    Write-Warning 'The emulator hub did not accept the graceful shutdown request.'
}

$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Milliseconds 250
    $stillRunning = Get-Process -Id $state.processId -ErrorAction SilentlyContinue
} while ($stillRunning -and (Get-Date) -lt $deadline)

if ($stillRunning) {
    Write-Warning 'Graceful shutdown timed out. Stopping only the validated emulator process tree; the latest local data might not be exported.'
    $allProcesses = Get-CimInstance Win32_Process
    $descendants = [System.Collections.Generic.List[int]]::new()
    $frontier = [System.Collections.Generic.Queue[int]]::new()
    $frontier.Enqueue([int]$state.processId)
    while ($frontier.Count -gt 0) {
        $parentId = $frontier.Dequeue()
        foreach ($child in $allProcesses | Where-Object ParentProcessId -eq $parentId) {
            $descendants.Add([int]$child.ProcessId)
            $frontier.Enqueue([int]$child.ProcessId)
        }
    }
    [array]::Reverse($descendants)
    foreach ($processId in $descendants) {
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
    Stop-Process -Id $state.processId -Force -ErrorAction SilentlyContinue
}

Remove-Item -LiteralPath $stateFile -Force
if ($graceful) {
    Write-Host 'Local emulators stopped and the export-on-exit request was sent.'
} else {
    Write-Host 'Local emulator process stopped.'
}

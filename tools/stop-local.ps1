[CmdletBinding()]
param(
    [int]$GracePeriodSeconds = 30,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeDirectory = Join-Path $repoRoot '.firebase-emulator-runtime'
$stateFile = Join-Path $runtimeDirectory 'emulators.json'
$firebaseConfig = Join-Path $repoRoot 'firebase.json'

# Only these images are ever force-stopped; anything else holding an emulator
# port belongs to another tool and is reported instead of killed.
$emulatorImageNames = @('node', 'java')

function Get-EmulatorPort {
    # Ports the suite reserves without declaring them in firebase.json.
    $ports = [System.Collections.Generic.List[int]]@(4500, 9150)
    if (Test-Path -LiteralPath $firebaseConfig) {
        $config = Get-Content -LiteralPath $firebaseConfig -Raw | ConvertFrom-Json
        if ($config.emulators) {
            foreach ($emulator in $config.emulators.PSObject.Properties) {
                $port = $emulator.Value.port -as [int]
                if ($port -and -not $ports.Contains($port)) {
                    $ports.Add($port)
                }
            }
        }
    }
    return $ports
}

function Get-PortOwner([int]$Port) {
    $owners = [System.Collections.Generic.List[int]]::new()
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        foreach ($connection in Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {
            $processId = [int]$connection.OwningProcess
            if ($processId -gt 0 -and -not $owners.Contains($processId)) {
                $owners.Add($processId)
            }
        }
        return $owners
    }
    foreach ($line in (netstat -ano)) {
        if ($line -match "^\s+TCP\s+\S+:$Port\s+\S+\s+LISTENING\s+(\d+)\s*$") {
            $processId = [int]$Matches[1]
            if ($processId -gt 0 -and -not $owners.Contains($processId)) {
                $owners.Add($processId)
            }
        }
    }
    return $owners
}

function Get-BoundPort {
    $bound = [System.Collections.Generic.List[pscustomobject]]::new()
    foreach ($port in Get-EmulatorPort) {
        foreach ($processId in Get-PortOwner $port) {
            $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
            $bound.Add([pscustomobject]@{
                Port = $port
                ProcessId = $processId
                ProcessName = if ($process) { $process.ProcessName } else { 'unknown' }
            })
        }
    }
    return $bound
}

function Wait-ForPortRelease([int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $bound = @(Get-BoundPort)
        if ($bound.Count -eq 0) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Stop-ProcessTree([int]$ProcessId) {
    $allProcesses = Get-CimInstance Win32_Process
    $descendants = [System.Collections.Generic.List[int]]::new()
    $frontier = [System.Collections.Generic.Queue[int]]::new()
    $frontier.Enqueue($ProcessId)
    while ($frontier.Count -gt 0) {
        $parentId = $frontier.Dequeue()
        foreach ($child in $allProcesses | Where-Object ParentProcessId -eq $parentId) {
            $childId = [int]$child.ProcessId
            if (-not $descendants.Contains($childId)) {
                $descendants.Add($childId)
                $frontier.Enqueue($childId)
            }
        }
    }
    [array]::Reverse($descendants)
    foreach ($id in $descendants) {
        Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

$hubUrl = 'http://127.0.0.1:4400'
if (Test-Path -LiteralPath $stateFile) {
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    if ($state.hubUrl) {
        $hubUrl = $state.hubUrl
    }
}

if (@(Get-BoundPort).Count -eq 0) {
    if (Test-Path -LiteralPath $stateFile) {
        Remove-Item -LiteralPath $stateFile -Force
        Write-Host 'No emulator ports are in use. Removed stale session state.'
    } else {
        Write-Host 'No local emulators are running.'
    }
    exit 0
}

$graceful = $false
if (-not $Force) {
    Write-Host 'Requesting graceful Firebase Emulator Suite shutdown...'
    try {
        Invoke-WebRequest -Uri "$hubUrl/__/quitquitquit" -Method Get -TimeoutSec 5 | Out-Null
        $graceful = $true
    } catch {
        Write-Warning 'The emulator hub did not accept the graceful shutdown request.'
    }

    if (Wait-ForPortRelease $GracePeriodSeconds) {
        if (Test-Path -LiteralPath $stateFile) {
            Remove-Item -LiteralPath $stateFile -Force
        }
        if ($graceful) {
            Write-Host 'Local emulators stopped and the export-on-exit request was sent.'
        } else {
            Write-Host 'Local emulators stopped.'
        }
        exit 0
    }

    Write-Warning 'Graceful shutdown did not release every emulator port. Force-stopping the emulator processes; the latest local data might not be exported.'
}

$foreign = [System.Collections.Generic.List[string]]::new()
$stopped = [System.Collections.Generic.List[int]]::new()
foreach ($entry in Get-BoundPort) {
    if ($emulatorImageNames -notcontains $entry.ProcessName) {
        $foreign.Add("port $($entry.Port) is held by $($entry.ProcessName) (PID $($entry.ProcessId))")
        continue
    }
    if ($stopped.Contains($entry.ProcessId)) {
        continue
    }
    Write-Host "Force-stopping $($entry.ProcessName) (PID $($entry.ProcessId)) on port $($entry.Port)..."
    Stop-ProcessTree $entry.ProcessId
    $stopped.Add($entry.ProcessId)
}

$released = Wait-ForPortRelease 10

if (Test-Path -LiteralPath $stateFile) {
    Remove-Item -LiteralPath $stateFile -Force
}

if ($foreign.Count -gt 0) {
    Write-Warning "Left non-emulator processes untouched: $($foreign -join '; ')."
}

if (-not $released) {
    $remaining = (@(Get-BoundPort) | ForEach-Object { "$($_.Port)/$($_.ProcessName) (PID $($_.ProcessId))" }) -join ', '
    throw "Emulator ports are still in use after a forced stop: $remaining."
}

Write-Host 'Local emulators stopped.'

# Cleanly stops everything start-dev.ps1 started, and cleans up anything left
# over from an IDE run configuration or a forgotten terminal window too - the
# port-based fallback below is what actually fixes "port 8080 already in use"
# for good. See docs/Development.md.

$ErrorActionPreference = 'Continue'
$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $RepoRoot

$LogDir = Join-Path $RepoRoot 'logs'
$BackendPidFile = Join-Path $LogDir 'backend.pid'
$FrontendPidFile = Join-Path $LogDir 'frontend.pid'

function Write-Ok([string]$Text) { Write-Host "  [OK] $Text" -ForegroundColor Green }

function Get-PortOwnerPid([int]$Port) {
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop | Select-Object -First 1
        if ($conn) { return $conn.OwningProcess }
        return $null
    } catch {
        $line = netstat -ano | Select-String ":$Port\s.*LISTENING" | Select-Object -First 1
        if ($line) {
            $parts = ($line.ToString().Trim() -split '\s+')
            return [int]$parts[-1]
        }
        return $null
    }
}

function Stop-Tree([int]$ProcId) {
    taskkill /PID $ProcId /T /F *> $null
}

function Stop-ByPidFile([string]$PidFile, [string]$Label) {
    if (Test-Path $PidFile) {
        $storedPid = Get-Content $PidFile -ErrorAction SilentlyContinue
        if ($storedPid) {
            $proc = Get-Process -Id $storedPid -ErrorAction SilentlyContinue
            if ($proc) {
                Stop-Tree -ProcId $storedPid
                Write-Ok "$Label stopped (PID $storedPid)"
            }
        }
        Remove-Item $PidFile -ErrorAction SilentlyContinue
    }
}

function Stop-ByPort([int]$Port, [string]$Label) {
    $procId = Get-PortOwnerPid -Port $Port
    if ($procId) {
        Stop-Tree -ProcId $procId
        Write-Ok "$Label cleaned up leftover process on port $Port (PID $procId)"
    }
}

Write-Host "== Stopping GateKeeper dev environment ==" -ForegroundColor Cyan

Stop-ByPidFile -PidFile $FrontendPidFile -Label "Frontend"
Stop-ByPidFile -PidFile $BackendPidFile -Label "Backend"

# Fallback cleanup - catches anything not started by start-dev.ps1 (an IDE run
# configuration, a forgotten terminal, an earlier manual run). This is exactly
# what leaves orphan processes holding port 8080 in practice.
Stop-ByPort -Port 5173 -Label "Frontend"
Stop-ByPort -Port 8080 -Label "Backend"

Write-Host ""
Write-Host "  Stopping docker compose (postgres)..."
docker compose stop *> $null
Write-Ok "docker compose stopped"

Write-Host ""
Write-Host "Done. Postgres data volume preserved - see docs/Development.md to reset it."

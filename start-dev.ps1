# One-command local dev startup: Docker -> Postgres -> backend -> frontend.
# See docs/Development.md for the full writeup. Windows PowerShell 5.1 compatible.
#
# Usage:
#   .\start-dev.ps1          start everything (reuses anything already running)
#   .\start-dev.ps1 -Demo    also seed the curated demo dataset (local,demo profiles)

param(
    [switch]$Demo
)

# Deliberately NOT 'Stop': native commands (docker, npm) write routine
# progress to stderr, and PowerShell 5.1 wraps that as a terminating
# NativeCommandError under 'Stop' even when exit code is 0. Failures are
# checked explicitly via $LASTEXITCODE / try-catch throughout instead.
$ErrorActionPreference = 'Continue'
$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $RepoRoot

$BackendPort = 8080
$ManagementPort = 8081
$FrontendPort = 5173
$LogDir = Join-Path $RepoRoot 'logs'
$BackendPidFile = Join-Path $LogDir 'backend.pid'
$FrontendPidFile = Join-Path $LogDir 'frontend.pid'
$BackendLog = Join-Path $LogDir 'backend.log'
$FrontendLog = Join-Path $LogDir 'frontend.log'

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir -Force | Out-Null }

# Spring Boot does not read .env directly - only docker-compose does, via its
# own variable substitution (see INSTALLATION.md). Since this script runs the
# backend natively (./mvnw spring-boot:run), .env is loaded here and exported
# into THIS process's environment so the child inherits it, making .env the
# single persistent config source for both paths. An already-set environment
# variable always wins - .env only fills in what isn't already set.
function Import-DotEnv([string]$Path) {
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { return }
        $eq = $line.IndexOf('=')
        if ($eq -lt 1) { return }
        $key = $line.Substring(0, $eq).Trim()
        $value = $line.Substring($eq + 1).Trim()
        if ($value.StartsWith('"') -and $value.EndsWith('"') -and $value.Length -ge 2) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if (-not (Test-Path "Env:$key")) {
            Set-Item -Path "Env:$key" -Value $value
        }
    }
}
Import-DotEnv (Join-Path $RepoRoot '.env')

function Write-Section([string]$Text) {
    Write-Host ""
    Write-Host "== $Text ==" -ForegroundColor Cyan
}
function Write-Ok([string]$Text) { Write-Host "  [OK] $Text" -ForegroundColor Green }
function Write-Warn([string]$Text) { Write-Host "  [!]  $Text" -ForegroundColor Yellow }
function Write-Err([string]$Text) { Write-Host "  [FAIL] $Text" -ForegroundColor Red }

# Resolves the PID of whatever process is LISTENing on a TCP port, or $null.
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

function Test-HttpOk([string]$Url, [int]$TimeoutSec = 3) {
    try {
        $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        return ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500)
    } catch {
        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode
            return ($code -ge 200 -and $code -lt 500)
        }
        return $false
    }
}

# The backend always brings its actuator up on $ManagementPort alongside the
# app port, whether started via mvnw or docker-compose - a reliable, no-auth
# fingerprint for "is this GateKeeper" that doesn't depend on business logic.
function Test-ManagementHealthy {
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:$ManagementPort/actuator/health" -UseBasicParsing -TimeoutSec 3
        # Actuator responds with its own application/vnd.spring-boot.actuator.v3+json
        # media type, which PowerShell 5.1 doesn't recognize as text - .Content
        # comes back as a raw byte[] rather than a decoded string in that case.
        $content = $resp.Content
        if ($content -is [byte[]]) {
            $content = [System.Text.Encoding]::UTF8.GetString($content)
        }
        return ($resp.StatusCode -eq 200 -and $content -match '"status"\s*:\s*"UP"')
    } catch {
        return $false
    }
}

# --- Docker --------------------------------------------------------------
Write-Section "Docker"
$dockerUp = $false
docker info *> $null
if ($LASTEXITCODE -eq 0) { $dockerUp = $true }

if (-not $dockerUp) {
    Write-Warn "Docker daemon not reachable - attempting to start Docker Desktop"
    $dockerDesktopPath = Join-Path $Env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (Test-Path $dockerDesktopPath) {
        Start-Process $dockerDesktopPath | Out-Null
        $waited = 0
        while ((-not $dockerUp) -and ($waited -lt 90)) {
            Start-Sleep -Seconds 3
            $waited += 3
            docker info *> $null
            if ($LASTEXITCODE -eq 0) { $dockerUp = $true }
        }
    }
}

if (-not $dockerUp) {
    Write-Err "Docker is not running and could not be started automatically."
    Write-Host "  Start Docker Desktop yourself, then re-run this script."
    exit 1
}
Write-Ok "Docker daemon is up"

# --- Database --------------------------------------------------------------
# Only the 'postgres' service - never 'docker compose up' bare, which would
# also start the compose file's 'backend' service on the SAME host port 8080
# that a native './mvnw spring-boot:run' below wants. Running both is the
# most common way this port conflict actually happens.
Write-Section "Database"
docker compose up -d postgres *> $null
$waited = 0
$dbHealthy = $false
while ($waited -lt 60) {
    $status = docker inspect --format='{{.State.Health.Status}}' gatekeeper-postgres 2>$null
    if ($status -eq 'healthy') { $dbHealthy = $true; break }
    Start-Sleep -Seconds 2
    $waited += 2
}
if ($dbHealthy) {
    Write-Ok "PostgreSQL is healthy (gatekeeper-postgres)"
} else {
    Write-Err "PostgreSQL did not become healthy within ${waited}s - check 'docker logs gatekeeper-postgres'"
    exit 1
}

# --- Backend --------------------------------------------------------------
Write-Section "Backend"
$existingPid = Get-PortOwnerPid -Port $BackendPort
$backendReady = $false

if ($existingPid) {
    if (Test-ManagementHealthy) {
        Write-Ok "Backend already running (PID $existingPid) - reusing it."
        $backendReady = $true
    } else {
        $proc = Get-Process -Id $existingPid -ErrorAction SilentlyContinue
        $procName = 'unknown'
        if ($proc) { $procName = $proc.ProcessName }
        Write-Err "Port $BackendPort is in use by '$procName' (PID $existingPid), and it does not look like GateKeeper."
        Write-Host "  Resolve with one of:"
        Write-Host "    taskkill /PID $existingPid /F"
        Write-Host "    Run backend on another port: `$env:SERVER_PORT = '8090'` then re-run this script"
        exit 1
    }
} else {
    Write-Host "  Starting backend (backend\mvnw.cmd spring-boot:run)..."
    if ($Demo) {
        $env:SPRING_PROFILES_ACTIVE = 'local,demo'
        Write-Host "  Profile: local,demo (curated demo dataset will be seeded)"
    }
    $backendProc = Start-Process -FilePath (Join-Path $RepoRoot 'backend\mvnw.cmd') `
        -ArgumentList 'spring-boot:run' `
        -WorkingDirectory (Join-Path $RepoRoot 'backend') `
        -WindowStyle Hidden `
        -RedirectStandardOutput $BackendLog `
        -RedirectStandardError (Join-Path $LogDir 'backend.err.log') `
        -PassThru
    $backendProc.Id | Out-File -FilePath $BackendPidFile -Encoding ascii -Force

    $waited = 0
    while ($waited -lt 150) {
        if (Test-ManagementHealthy) { $backendReady = $true; break }
        if ($backendProc.HasExited) {
            Write-Err "Backend process exited early - see logs\backend.log and logs\backend.err.log"
            exit 1
        }
        Start-Sleep -Seconds 3
        $waited += 3
    }
    if ($backendReady) {
        Write-Ok "Backend started (PID $($backendProc.Id)), ready after ~${waited}s"
    } else {
        Write-Err "Backend did not become healthy within ${waited}s - see logs\backend.log"
        exit 1
    }
}

# --- Frontend --------------------------------------------------------------
Write-Section "Frontend"
$frontendDir = Join-Path $RepoRoot 'frontend'
if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
    Write-Host "  Installing frontend dependencies (npm install)..."
    Push-Location $frontendDir
    npm install
    $npmInstallExit = $LASTEXITCODE
    Pop-Location
    if ($npmInstallExit -ne 0) {
        Write-Err "npm install failed"
        exit 1
    }
    Write-Ok "Dependencies installed"
}

$existingFrontendPid = Get-PortOwnerPid -Port $FrontendPort
$frontendReady = $false
if ($existingFrontendPid) {
    Write-Ok "Frontend already running (PID $existingFrontendPid) - reusing it."
    $frontendReady = $true
} else {
    Write-Host "  Starting frontend (npm run dev)..."
    # Routed through cmd.exe rather than resolving npm's own path directly:
    # Get-Command npm can resolve to the extensionless POSIX shim some
    # installs ship alongside npm.cmd, which Start-Process cannot execute
    # directly ("%1 is not a valid Win32 application").
    $frontendProc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'npm', 'run', 'dev' `
        -WorkingDirectory $frontendDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput $FrontendLog `
        -RedirectStandardError (Join-Path $LogDir 'frontend.err.log') `
        -PassThru
    $frontendProc.Id | Out-File -FilePath $FrontendPidFile -Encoding ascii -Force

    $waited = 0
    while ($waited -lt 30) {
        if (Test-HttpOk -Url "http://localhost:$FrontendPort/") { $frontendReady = $true; break }
        Start-Sleep -Seconds 2
        $waited += 2
    }
    if ($frontendReady) {
        Write-Ok "Frontend started (PID $($frontendProc.Id))"
    } else {
        Write-Warn "Frontend did not respond within ${waited}s yet - it may still be warming up; check logs\frontend.log"
    }
}

# --- Status ------------------------------------------------------------
Write-Section "Status"
function Status-Line([string]$Label, [bool]$Ok) {
    $mark = '[FAIL]'
    if ($Ok) { $mark = '[OK]' }
    Write-Host ("  {0,-10} {1}" -f $Label, $mark)
}
Status-Line -Label "Database" -Ok $dbHealthy
Status-Line -Label "Backend" -Ok $backendReady
Status-Line -Label "Frontend" -Ok $frontendReady

Write-Host ""
Write-Host "  Frontend:        http://localhost:$FrontendPort"
Write-Host "  Backend API:     http://localhost:$BackendPort/api/v1"
Write-Host "  Backend health:  http://localhost:$ManagementPort/actuator/health"
Write-Host "  Logs:            $LogDir"
Write-Host ""

if ($dbHealthy -and $backendReady -and $frontendReady) {
    Write-Host "GateKeeper dev environment is up." -ForegroundColor Green
    exit 0
} else {
    Write-Host "GateKeeper dev environment started with warnings - see above." -ForegroundColor Yellow
    exit 1
}

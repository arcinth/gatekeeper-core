#!/usr/bin/env bash
# One-command local dev startup: Docker -> Postgres -> backend -> frontend.
# See docs/Development.md for the full writeup.
#
# Usage:
#   ./start-dev.sh          start everything (reuses anything already running)
#   ./start-dev.sh --demo   also seed the curated demo dataset (local,demo profiles)
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

BACKEND_PORT=8080
MANAGEMENT_PORT=8081
FRONTEND_PORT=5173
LOG_DIR="$REPO_ROOT/logs"
BACKEND_PID_FILE="$LOG_DIR/backend.pid"
FRONTEND_PID_FILE="$LOG_DIR/frontend.pid"
DEMO=0

for arg in "$@"; do
  case "$arg" in
    --demo) DEMO=1 ;;
  esac
done

mkdir -p "$LOG_DIR"

# Spring Boot does not read .env directly - only docker-compose does, via its
# own variable substitution (see INSTALLATION.md). Since this script runs the
# backend natively (./mvnw spring-boot:run), .env is loaded here and exported
# into THIS process's environment so the child inherits it, making .env the
# single persistent config source for both paths. An already-set environment
# variable always wins - .env only fills in what isn't already set.
if [ -f "$REPO_ROOT/.env" ]; then
  while IFS='=' read -r key value; do
    key="$(printf '%s' "$key" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    case "$key" in
      ''|'#'*) continue ;;
    esac
    value="$(printf '%s' "$value" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    value="${value%\"}"
    value="${value#\"}"
    if [ -z "${!key+set}" ]; then
      export "$key=$value"
    fi
  done < "$REPO_ROOT/.env"
fi

ok()      { printf "  [OK] %s\n" "$1"; }
warn()    { printf "  [!]  %s\n" "$1"; }
err()     { printf "  [FAIL] %s\n" "$1" >&2; }
section() { printf "\n== %s ==\n" "$1"; }

# Resolves the PID of whatever process is LISTENing on a TCP port, or empty.
# Tries lsof (macOS/most Linux), then ss (Linux), then netstat (older
# Linux, and Windows netstat.exe when this runs under Git Bash).
port_pid() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti tcp:"$port" -sTCP:LISTEN 2>/dev/null | head -n1
  elif command -v ss >/dev/null 2>&1; then
    ss -ltnp 2>/dev/null | awk -v p=":$port" '$4 ~ p' | grep -oE 'pid=[0-9]+' | head -n1 | cut -d= -f2
  elif command -v netstat >/dev/null 2>&1; then
    netstat -ano 2>/dev/null | grep -E "[:.]$port[[:space:]]" | grep -i LISTEN | awk '{print $NF}' | cut -d/ -f1 | head -n1
  fi
}

http_ok() {
  local url="$1" code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$url" 2>/dev/null)
  [ -n "$code" ] && [ "$code" -ge 200 ] && [ "$code" -lt 500 ]
}

# The backend always brings its actuator up on $MANAGEMENT_PORT alongside the
# app port - a reliable, no-auth fingerprint for "is this GateKeeper" that
# doesn't depend on business logic.
management_healthy() {
  curl -s --max-time 3 "http://localhost:$MANAGEMENT_PORT/actuator/health" 2>/dev/null | grep -q '"status":"UP"'
}

# --- Docker -----------------------------------------------------------
section "Docker"
if ! docker info >/dev/null 2>&1; then
  warn "Docker daemon not reachable - attempting to start it"
  if [ "$(uname)" = "Darwin" ]; then
    open -a Docker >/dev/null 2>&1 || true
  fi
  waited=0
  while [ "$waited" -lt 90 ] && ! docker info >/dev/null 2>&1; do
    sleep 3
    waited=$((waited + 3))
  done
fi
if ! docker info >/dev/null 2>&1; then
  err "Docker is not running."
  echo "  Start it (Docker Desktop on macOS, or 'sudo systemctl start docker' on Linux), then re-run this script."
  exit 1
fi
ok "Docker daemon is up"

# --- Database -----------------------------------------------------------
# Only the 'postgres' service - never 'docker compose up' bare, which would
# also start the compose file's 'backend' service on the SAME host port 8080
# that a native './mvnw spring-boot:run' below wants. Running both is the
# most common way this port conflict actually happens.
section "Database"
docker compose up -d postgres >/dev/null
waited=0
db_healthy=0
while [ "$waited" -lt 60 ]; do
  status=$(docker inspect --format='{{.State.Health.Status}}' gatekeeper-postgres 2>/dev/null)
  if [ "$status" = "healthy" ]; then db_healthy=1; break; fi
  sleep 2
  waited=$((waited + 2))
done
if [ "$db_healthy" -eq 1 ]; then
  ok "PostgreSQL is healthy (gatekeeper-postgres)"
else
  err "PostgreSQL did not become healthy within ${waited}s - check 'docker logs gatekeeper-postgres'"
  exit 1
fi

# --- Backend -----------------------------------------------------------
section "Backend"
existing_pid=$(port_pid "$BACKEND_PORT")
backend_ready=0
if [ -n "${existing_pid:-}" ]; then
  if management_healthy; then
    ok "Backend already running (PID $existing_pid) - reusing it."
    backend_ready=1
  else
    proc_name=$(ps -p "$existing_pid" -o comm= 2>/dev/null || echo unknown)
    err "Port $BACKEND_PORT is in use by '$proc_name' (PID $existing_pid), and it does not look like GateKeeper."
    echo "  Resolve with one of:"
    echo "    kill $existing_pid"
    echo "    SERVER_PORT=8090 $0"
    exit 1
  fi
else
  echo "  Starting backend (./backend/mvnw spring-boot:run)..."
  if [ "$DEMO" = "1" ]; then
    export SPRING_PROFILES_ACTIVE="local,demo"
    echo "  Profile: local,demo (curated demo dataset will be seeded)"
  fi
  ( cd backend && nohup ./mvnw spring-boot:run > "$LOG_DIR/backend.log" 2>&1 & echo $! > "$BACKEND_PID_FILE" )
  waited=0
  while [ "$waited" -lt 150 ]; do
    if management_healthy; then backend_ready=1; break; fi
    sleep 3
    waited=$((waited + 3))
  done
  if [ "$backend_ready" -eq 1 ]; then
    ok "Backend started (PID $(cat "$BACKEND_PID_FILE" 2>/dev/null)), ready after ~${waited}s"
  else
    err "Backend did not become healthy within ${waited}s - see logs/backend.log"
    exit 1
  fi
fi

# --- Frontend -----------------------------------------------------------
section "Frontend"
if [ ! -d "frontend/node_modules" ]; then
  echo "  Installing frontend dependencies (npm install)..."
  ( cd frontend && npm install )
  ok "Dependencies installed"
fi

existing_frontend_pid=$(port_pid "$FRONTEND_PORT")
frontend_ready=0
if [ -n "${existing_frontend_pid:-}" ]; then
  ok "Frontend already running (PID $existing_frontend_pid) - reusing it."
  frontend_ready=1
else
  echo "  Starting frontend (npm run dev)..."
  ( cd frontend && nohup npm run dev > "$LOG_DIR/frontend.log" 2>&1 & echo $! > "$FRONTEND_PID_FILE" )
  waited=0
  while [ "$waited" -lt 30 ]; do
    if http_ok "http://localhost:$FRONTEND_PORT/"; then frontend_ready=1; break; fi
    sleep 2
    waited=$((waited + 2))
  done
  if [ "$frontend_ready" -eq 1 ]; then
    ok "Frontend started (PID $(cat "$FRONTEND_PID_FILE" 2>/dev/null))"
  else
    warn "Frontend did not respond within ${waited}s yet - it may still be warming up; check logs/frontend.log"
  fi
fi

# --- Status -----------------------------------------------------------
section "Status"
db_mark="[FAIL]";       [ "$db_healthy" -eq 1 ] && db_mark="[OK]"
backend_mark="[FAIL]";  [ "$backend_ready" -eq 1 ] && backend_mark="[OK]"
frontend_mark="[FAIL]"; [ "$frontend_ready" -eq 1 ] && frontend_mark="[OK]"
printf "  %-10s %s\n" "Database" "$db_mark"
printf "  %-10s %s\n" "Backend" "$backend_mark"
printf "  %-10s %s\n" "Frontend" "$frontend_mark"

echo ""
echo "  Frontend:        http://localhost:$FRONTEND_PORT"
echo "  Backend API:     http://localhost:$BACKEND_PORT/api/v1"
echo "  Backend health:  http://localhost:$MANAGEMENT_PORT/actuator/health"
echo "  Logs:            $LOG_DIR"
echo ""

if [ "$db_healthy" -eq 1 ] && [ "$backend_ready" -eq 1 ] && [ "$frontend_ready" -eq 1 ]; then
  echo "GateKeeper dev environment is up."
  exit 0
else
  echo "GateKeeper dev environment started with warnings - see above."
  exit 1
fi

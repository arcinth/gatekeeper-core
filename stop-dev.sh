#!/usr/bin/env bash
# Cleanly stops everything start-dev.sh started, and cleans up anything left
# over from an IDE run configuration or a forgotten terminal window too - the
# port-based fallback below is what actually fixes "port 8080 already in use"
# for good. See docs/Development.md.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"
LOG_DIR="$REPO_ROOT/logs"
BACKEND_PID_FILE="$LOG_DIR/backend.pid"
FRONTEND_PID_FILE="$LOG_DIR/frontend.pid"

ok() { printf "  [OK] %s\n" "$1"; }

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

kill_tree() {
  local pid="$1"
  # Under Git Bash/MSYS on Windows, POSIX kill/pkill can silently fail to
  # terminate native Win32 processes (java.exe, node.exe) - they aren't
  # MSYS-aware, so the emulated signal has nowhere to land. taskkill is the
  # real, working mechanism there ("//PID" - doubled slash - stops MSYS's
  # own path-conversion heuristic from mangling the flag before exec).
  if [ "${OS:-}" = "Windows_NT" ] && command -v taskkill >/dev/null 2>&1; then
    taskkill //PID "$pid" //T //F >/dev/null 2>&1 || true
    return
  fi
  pkill -P "$pid" 2>/dev/null || true
  kill "$pid" 2>/dev/null || true
  sleep 1
  kill -9 "$pid" 2>/dev/null || true
}

stop_pidfile() {
  local pidfile="$1" label="$2" pid
  if [ -f "$pidfile" ]; then
    pid=$(cat "$pidfile" 2>/dev/null || true)
    if [ -n "${pid:-}" ] && kill -0 "$pid" 2>/dev/null; then
      kill_tree "$pid"
      ok "$label stopped (PID $pid)"
    fi
    rm -f "$pidfile"
  fi
}

stop_port() {
  local port="$1" label="$2" pid
  pid=$(port_pid "$port")
  if [ -n "${pid:-}" ]; then
    kill_tree "$pid"
    ok "$label cleaned up leftover process on port $port (PID $pid)"
  fi
}

echo "== Stopping GateKeeper dev environment =="

stop_pidfile "$FRONTEND_PID_FILE" "Frontend"
stop_pidfile "$BACKEND_PID_FILE" "Backend"

# Fallback cleanup - catches anything not started by start-dev.sh (an IDE run
# configuration, a forgotten terminal, an earlier manual run). This is exactly
# what leaves orphan processes holding port 8080 in practice.
stop_port 5173 "Frontend"
stop_port 8080 "Backend"

echo ""
echo "  Stopping docker compose (postgres)..."
docker compose stop >/dev/null 2>&1 || true
ok "docker compose stopped"

echo ""
echo "Done. Postgres data volume preserved - see docs/Development.md to reset it."

# Local Development Workflow

This document covers day-to-day local development: starting and stopping the full stack with one command, what those scripts actually do, and how to recover when something's stuck. For first-time setup (installing Java/Node/Docker, environment variables, the bootstrap admin account), see [INSTALLATION.md](../INSTALLATION.md) first - this document assumes that's already done once.

## Prerequisites

Same as [INSTALLATION.md](../INSTALLATION.md#prerequisites): JDK 21, Node.js 20+, Docker. Nothing extra is required for the scripts below - no new dependency was added to run them.

## Configuration: `.env`

Copy `.env.example` to `.env` (gitignored, never committed) and fill in real values once. `start-dev` loads it and exports it into the backend process's environment before launching `./mvnw spring-boot:run` - Spring Boot itself never reads `.env` directly, only `docker-compose.yml`'s variable substitution does, so this is what makes `.env` the single persistent config source for both the native run path and Compose. An environment variable already set in your shell always takes priority over `.env`.

This is where GitHub App / Anthropic credentials belong for day-to-day local development, so you only ever enter them once instead of re-exporting them in every new terminal.

## How to start

From the repository root:

| Platform | Command |
|---|---|
| Windows (PowerShell or double-click) | `start-dev.bat` or `.\start-dev.ps1` |
| Linux / macOS | `./start-dev.sh` |
| Any platform, via npm | `npm run dev:all` |

Add `-Demo` (PowerShell) / `--demo` (bash) / `npm run dev:all:demo` to also seed the curated demo dataset (`SPRING_PROFILES_ACTIVE=local,demo` - see [Demo profile](#demo-profile) below).

This one command:

1. Checks Docker is running; tries to start Docker Desktop if it isn't (Windows/macOS), waiting up to 90s.
2. Starts **only** the `postgres` service from `docker-compose.yml` (`docker compose up -d postgres`) and waits for its healthcheck.
3. Checks port 8080. If something's already listening there and it answers on the management port (8081) with `{"status":"UP"}`, it's treated as an already-running GateKeeper backend and reused - nothing new is started. Otherwise a fresh `./backend/mvnw spring-boot:run` is launched in the background, logged to `logs/backend.log`, and its PID recorded to `logs/backend.pid`.
4. Installs frontend dependencies (`npm install`) if `frontend/node_modules` doesn't exist yet.
5. Checks port 5173 the same way, reusing an already-running Vite dev server or starting a fresh one (`logs/frontend.log`, `logs/frontend.pid`).
6. Prints a status table and the URLs to use.

Example output:

```
== Docker ==
  [OK] Docker daemon is up

== Database ==
  [OK] PostgreSQL is healthy (gatekeeper-postgres)

== Backend ==
  Starting backend (backend\mvnw.cmd spring-boot:run)...
  [OK] Backend started (PID 27184), ready after ~48s

== Frontend ==
  [OK] Frontend already running (PID 13848) - reusing it.

== Status ==
  Database   [OK]
  Backend    [OK]
  Frontend   [OK]

  Frontend:        http://localhost:5173
  Backend API:     http://localhost:8080/api/v1
  Backend health:  http://localhost:8081/actuator/health
  Logs:            W:\Projects\AI-ML\gatekeeper-core\logs

GateKeeper dev environment is up.
```

Re-running the command is always safe: anything already up is detected and reused rather than started again. This is the property that actually prevents the "port 8080 already in use" failure - see [Root cause](#root-cause-why-this-used-to-break) below.

## How to stop

| Platform | Command |
|---|---|
| Windows | `stop-dev.bat` or `.\stop-dev.ps1` |
| Linux / macOS | `./stop-dev.sh` |
| Any platform, via npm | `npm run dev:stop` |

This stops the frontend and backend (using the recorded PIDs when available, and killing whatever is listening on 8080/5173 as a fallback even if it wasn't started by `start-dev`), then runs `docker compose stop` to stop Postgres. The Postgres data volume is preserved - nothing is deleted. No process is left running afterward; run `start-dev` again for a clean slate.

## Root cause: why this used to break

Three things made it easy to end up with two backends fighting over port 8080:

1. **`./mvnw spring-boot:run` is a foreground process with no tracking.** Start it in a terminal, forget about it (or start it via an IDE run configuration), start another one later, and the second one fails with `Port 8080 was already in use` - or, if the first one already died in a way that freed the port, the two instances end up interleaved across restarts, and the frontend has no way to know which backend answered a given request.
2. **`docker-compose.yml` defines a `backend` service on the same host port 8080** as the native `./mvnw spring-boot:run` path. Running `docker compose up` (bringing up everything in the file) *and* running the backend natively collides immediately - both bind `0.0.0.0:8080`. `start-dev` avoids this by only ever bringing up the `postgres` service from Compose; the backend is always the natively-run process.
3. **Nothing checked before starting.** There was no script, Makefile target, or convention that asked "is a backend already listening here?" before launching another one.

There is no Spring Boot DevTools in this project, so devtools-triggered child-JVM restarts were ruled out as a contributor - the dependency isn't present in `backend/pom.xml`.

## Common issues

### Port conflicts

`start-dev` checks port 8080 (and 5173) before doing anything. If it finds something there:

- **It's GateKeeper** (answers on 8081 with `{"status":"UP"}`): reused automatically, printed as `Backend already running (PID ...) - reusing it.` No action needed.
- **It's something else**: the script prints the owning process name and PID and exits rather than guessing:
  ```
  [FAIL] Port 8080 is in use by 'java' (PID 9142), and it does not look like GateKeeper.
    Resolve with one of:
      taskkill /PID 9142 /F          (kill it - Windows)
      kill 9142                      (kill it - Linux/macOS)
      $env:SERVER_PORT = '8090'      (run GateKeeper on a different port instead)
  ```
  Run `stop-dev` first if you're not sure what's on the port - it clears both 8080 and 5173 unconditionally as its last step, GateKeeper or not.

### Docker issues

- `start-dev` tries to launch Docker Desktop automatically on Windows/macOS and waits up to 90 seconds. On Linux, or if Docker Desktop isn't installed at the expected path, it prints an error and exits - start Docker yourself (`sudo systemctl start docker` on most Linux distributions) and re-run.
- If Postgres never reports healthy, check `docker logs gatekeeper-postgres` - most often a stale container from a previous, differently-configured run. `docker compose down` (from the repository root) removes the container without touching the named volume, then `start-dev` recreates it.

### Database reset

The Postgres data volume (`postgres-data` in `docker-compose.yml`) persists across `start-dev`/`stop-dev` cycles by design. To wipe it and start from an empty database:

```bash
./stop-dev.sh          # or stop-dev.bat / stop-dev.ps1
docker compose down -v # removes the named volume - this deletes all data
./start-dev.sh          # recreates an empty database and re-runs migrations
```

### Demo profile

Passing `-Demo` / `--demo` sets `SPRING_PROFILES_ACTIVE=local,demo`, which activates `DemoDataSeeder` (`backend/src/main/java/com/gatekeeper/demo/DemoDataSeeder.java`). It seeds five curated repositories with a consistent, self-contained governance story on first run.

This only seeds on an **empty** database (`if (repositoryRepository.count() > 0) return;`) - it silently does nothing against a database that already has any repository in it, curated or not. If you've been developing against a database with leftover test data and want the real demo dataset, reset the database first (above), then start with `-Demo`.

### Testing workflow

Not affected by any of the scripts above - run tests exactly as documented in [INSTALLATION.md](../INSTALLATION.md#running-tests):

```bash
cd backend && ./mvnw test
cd frontend && npx tsc -b && npx vite build
```

Backend integration tests that use Testcontainers need Docker running (the same daemon `start-dev` checks for), but do **not** need `start-dev` to have been run first - they start their own ephemeral Postgres container independent of `gatekeeper-postgres`.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `Port 8080 was already in use` | An old backend instance (terminal, IDE run config, or a previous `start-dev`) is still running | `stop-dev`, then `start-dev` again |
| Frontend loads but every request fails | Backend died after `start-dev` reported it healthy | Check `logs/backend.log` and `logs/backend.err.log` |
| `start-dev` hangs on "Database" | Postgres container unhealthy or port 5433 taken by something else | `docker logs gatekeeper-postgres`; check nothing else uses 5433 |
| `start-dev` hangs on "Backend" for the full timeout | Usually a slow first Maven dependency download, or a genuine startup failure | Tail `logs/backend.log` in another terminal while it runs |
| Login works from a fresh backend but the browser still fails | Stale JWT/refresh token from a previous backend instance (different JWT secret if `JWT_SECRET` wasn't fixed) | Log out and back in, or clear the frontend's local storage |
| `stop-dev` says nothing was running, but something still answers on 8080/5173 | Process not owned by your user, or a container publishing that port directly | `docker ps` to check for a container publishing 8080; otherwise inspect the PID `stop-dev` reported skipping |

## Remaining limitations

- `start-dev`/`stop-dev` manage the **native** backend and frontend processes plus the **`postgres`** Compose service only. If you deliberately run the full `docker compose up` stack (including the `backend` service defined in `docker-compose.yml`), these scripts won't see or manage that container - stop it with `docker compose down` directly.
- The "is this GateKeeper" check relies on the management port (8081) being reachable and unauthenticated, which is how this project is configured today (see [Observability.md](Observability.md)). If that ever changes, the fingerprint in both scripts (`Test-ManagementHealthy` / `management_healthy`) needs updating alongside it.
- On Linux, automatic Docker daemon startup isn't attempted (no equivalent of "Docker Desktop.exe" to launch); the script asks you to start it yourself.
- These scripts assume one developer, one machine. They don't coordinate across multiple machines or containers sharing the same database.

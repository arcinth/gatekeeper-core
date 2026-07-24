# Migration Guide — to v1.0.0

This is the first tagged release, so there's no prior version to migrate *from* in the usual sense. What follows is: (1) what anyone with an existing checkout or a deployed earlier build needs to change, and (2) the concrete steps to take this repository's current working tree to a clean, tagged v1.0.0.

## 1. Database

No manual migration steps. Flyway migrations `V1` through `V16` are additive and apply cleanly on startup; there is no `V17` introduced by this release. If you're starting fresh, nothing to do — the application creates and migrates the schema on first boot.

If your local database has accumulated ad-hoc test data from earlier development (repositories/installations/analysis runs created by hand or by prior manual verification) that now collides with real usage — the specific, previously-encountered symptom is a security/verdict page that looks inconsistent with itself, or a GitHub "Manage" link pointing at an installation ID GitHub no longer recognizes. Both are data-hygiene issues, not schema issues:

```bash
./stop-dev.sh                      # or stop-dev.ps1 / .bat
docker compose down -v             # removes the named volume - deletes all data
./start-dev.sh --demo              # recreates an empty database, seeds the curated demo dataset
```

Or, to keep production/real data and only clear a specific stale record, use the application's own reconciliation path rather than editing the database directly — GitHub installation records self-heal via `POST /api/v1/github/installations/reconcile?installationId=...` (see `docs/Development.md` and `GitHubInstallationService.reconcileInstallation`).

## 2. New required configuration

`GITHUB_APP_SLUG` is new. If GitHub integration ("Connect GitHub") worked for you before without it, it didn't — it always required this value; it was simply undocumented. Set it now:

```
GITHUB_APP_SLUG=<your-app>          # the slug from https://github.com/apps/<your-app>
```

Nothing else changed shape. Every other environment variable from earlier development keeps its name and meaning — see `INSTALLATION.md`'s configuration table for the complete, current list.

## 3. `.env` now has a real effect on the native run path

Previously, `.env` only fed `docker-compose.yml`'s own variable substitution — the backend run via `./mvnw spring-boot:run` never read it, so credentials had to be re-exported in every terminal session or set through an IDE run configuration. `start-dev.ps1`/`.sh` now load `.env` and export it into the backend process automatically.

If you have credentials currently set only via shell exports or an IDE run configuration, no action is required — those still work, and take priority over `.env` (an already-set environment variable is never overridden). But going forward, `.env` is the more durable place to put them: copy `.env.example` to `.env` once, and stop re-exporting values per session.

## 4. Frontend routes and removed pages

Fifteen routes collapsed to six top-level destinations. Every old path still resolves — `AppRoutes.tsx` redirects each retired route (`/dashboard`, `/analysis-runs`, `/analysis-runs/:id`, `/policy-findings`, `/security-findings`, `/ai-review-runs`, `/ai-review-runs/:id`, `/verdicts`, `/verdicts/:id`, `/repositories/governance`, `/repositories/:id/governance`, `/users`, `/audit-log`) to wherever that content now lives, so no bookmark or link posted into a GitHub check breaks.

If you have an in-flight branch that touches any of these deleted files, it will conflict on merge, not silently disappear:

- **Pages removed**: `DashboardPage`, `AnalysisRunsPage`, `AnalysisRunDetailPage`, `PolicyFindingsPage`, `SecurityFindingsPage`, `AIReviewRunsPage`, `AIReviewRunDetailPage`, `VerdictsPage`, `VerdictDetailPage`, `RepositoryGovernancePage`, `UsersPage`.
- **Components removed**: `ui/Badge`, `ui/Card`, `ui/badgeTones`, `ui/EmptyState`, `ui/ErrorState`, `ui/PageHeader`, `ui/Button`, `Breadcrumbs`, `LoadingSpinner` — each has a direct successor (`ui/Chip`, `ui/Surface`, `ui/tones`, `ui/states`, `ui/buttonClasses`, breadcrumbs now render inline in `AppLayout`, `ProtectedRoute`'s own inline loading state).
- **Services removed**: `analysisRunService`, `verdictService`, `aiReviewRunService`, `aiReviewFindingService`, `policyFindingService` — each was folded into `reportService.getByAnalysisRunId()`, which already aggregated everything the deleted services fetched separately.

Rebase in-flight frontend work onto the new component set rather than resolving each conflict by re-adding the old file — the old file's functionality is preserved, just relocated.

## 5. Local dev workflow

Prior instructions (`./mvnw spring-boot:run` in one terminal, `npm run dev` in another, `docker compose up -d postgres` before both) still work exactly as before. They're just no longer the fastest path — see `docs/Development.md` for the one-command alternative, and for what actually caused the "port 8080 already in use" failures some environments were hitting (usually a forgotten prior instance, or `docker compose up` bringing up the Compose file's own `backend` service on the same port as the natively-run one).

## 6. Taking this repository's current working tree to a tagged v1.0.0

This specific checkout has substantial work from the final development session sitting uncommitted: the frontend redesign, the demo data seeder, three GitHub-integration fixes, and the local dev tooling. None of it is in git history yet. To actually cut the v1.0.0 release:

1. Review `git status` and `git diff` — confirm nothing unexpected is staged (in particular, `.env` must stay untracked; it already is, via `.gitignore`).
2. Commit in logical groups rather than one undifferentiated commit, if history quality matters to you — the changes cleanly separate into: frontend redesign, demo data seeder, security-findings lifecycle filtering, GitHub App configuration diagnostics + `.env` wiring, GitHub installation reconciliation, webhook secret sanitization, and local dev tooling.
3. Run the full checklist in `docs/Testing-Checklist.md` before tagging.
4. Tag `v1.0.0` and update the release link at the bottom of `CHANGELOG.md` if the repository URL differs from the placeholder already there.

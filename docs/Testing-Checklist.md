# Testing Checklist

A release-validation checklist for GateKeeper v1.0.0: what to run automatically, and what to click through by hand before calling a build good. Pair this with [docs/Development.md](Development.md) for how to actually get the stack running.

## 1. Automated checks

Run these first — nothing below is worth doing by hand until these pass.

- [ ] `cd backend && ./mvnw test` — unit tests, zero failures expected. Integration tests under `com.gatekeeper.integration` additionally need a Docker environment Testcontainers can detect; if they error with "Failed to load ApplicationContext" / "Could not find a valid Docker environment" on an otherwise-working Docker install, that's a known Testcontainers-on-native-Windows limitation, not a code regression — confirm by running the same suite in CI (`ubuntu-latest`) instead.
- [ ] `cd frontend && npx tsc -b` — TypeScript typecheck, zero errors.
- [ ] `cd frontend && npx vite build` — production build succeeds.
- [ ] CI green on the PR/branch (`.github/workflows/backend.yml`, `frontend.yml`, `security.yml`) — this is where the Testcontainers integration tests, OWASP Dependency Check, npm audit, and Gitleaks actually run to completion.

## 2. Environment sanity

- [ ] `start-dev.ps1` / `.sh` reports `Database [OK]`, `Backend [OK]`, `Frontend [OK]`.
- [ ] Re-running `start-dev` immediately after prints "already running - reusing it" for both backend and frontend, rather than failing on a port conflict.
- [ ] `stop-dev.ps1` / `.sh` leaves nothing listening on 8080 or 5173 afterward.
- [ ] Backend startup log shows `✓ GitHub App configured` (or a specific, named `✗ ... missing: ...` if credentials aren't set — never a silent gap).

## 3. Authentication & access control

- [ ] Log in with the bootstrap administrator (`admin@gatekeeper.local` / the configured password). Redirects to Inbox.
- [ ] Log out, confirm the session actually ends (protected routes redirect to `/login`).
- [ ] An expired/invalid access token triggers a silent refresh, not a forced logout, as long as the refresh token is still valid.
- [ ] Create a user with each role from Settings → Access; confirm each role sees only what it should (e.g., a Developer cannot reach Settings → Access; only Administrator can).
- [ ] Attempt an admin-only backend endpoint (e.g. `POST /api/v1/users`) as a non-admin JWT — expect `403`, not a silent success.

## 4. Repository onboarding

- [ ] Repositories page → "Connect GitHub" redirects to `https://github.com/apps/<slug>/installations/new` (not the "not configured" error, if the App is set up).
- [ ] Completing the GitHub install flow lands back on Repositories with the new installation visible and repositories populated, without a manual "Resync" click being required.
- [ ] "Manage" on an installation opens that installation's real GitHub settings page, not a 404.
- [ ] "Resync" on an installation updates `lastSuccessfulSyncAt` and repository count.
- [ ] Disconnecting a repository removes it from the list and stops it from producing new Analysis Runs.

## 5. Pull request pipeline (needs a real GitHub App + a test PR, or the demo dataset)

- [ ] Opening a pull request in a connected repository produces a new entry on the Inbox / Pull Requests page within a few seconds.
- [ ] A PR containing a hardcoded secret or an insecure crypto call produces Security findings and a `BLOCKED` verdict.
- [ ] A PR containing only a `TODO`/`FIXME` comment produces a Policy finding, with severity/verdict impact matching `docs/Policy-Development.md`'s documented rules.
- [ ] A clean PR (no findings) produces an `APPROVED` verdict.
- [ ] The PR detail page renders, in order: verdict banner → rationale → evidence (Security/Policy/AI tabs) → decision → history — for at least one `BLOCKED` and one `APPROVED` PR.
- [ ] AI Review findings (if `AI_REVIEW_ENABLED=true`) appear only in the Engineering Report / evidence tab, never affect the verdict, and the AI tab's advisory-notice banner is visible.
- [ ] A GitHub Check Run appears on the PR for the automated verdict; recording a reviewer decision publishes a second, separate Check Run.
- [ ] Redelivering a webhook from GitHub's own UI (Advanced tab → a past delivery → Redeliver) returns `200`, not `401`.

## 6. Security triage

- [ ] Security page defaults to `CRITICAL` severity, current/open findings only.
- [ ] The count of critical findings shown here is consistent with the count of `BLOCKED` pull requests on the Inbox — no finding should read as unaddressed while its PR's verdict says otherwise.
- [ ] Toggling "Show resolved & historical findings" reveals findings from merged/closed PRs or superseded runs, each labeled with its PR's actual status.
- [ ] Clicking a finding navigates to the originating pull request.

## 7. Governance & audit

- [ ] Insights page loads and its block-rate / findings-by-category numbers are consistent with what's on the Pull Requests and Security pages for the same window.
- [ ] Policies page lists the active Policy Engine rules for the organization and supports whatever CRUD the current build exposes.
- [ ] Settings → Audit shows an entry for at least: a login, a user created/edited, and a policy change; filters work; CSV export downloads a non-empty file.

## 8. Cross-cutting

- [ ] Dark and light theme both render every page above without unreadable contrast or a layout break.
- [ ] A 401/403/404/500 from the backend surfaces as a readable error state in the UI, not a blank page or an unhandled console exception.
- [ ] Rate limiting: repeated rapid login attempts against one account (or from a script hitting one IP) eventually return `429`.
- [ ] Every mutating admin action you performed above shows up in Settings → Audit afterward.

## 9. Release hygiene

- [ ] `git status` is clean — no uncommitted work left behind (`git log` should be the actual source of truth for what shipped).
- [ ] `CHANGELOG.md` reflects everything in this release.
- [ ] `.env` / secrets are not staged or committed (`git status`, check nothing under `secrets/` besides `README.md`/`.gitkeep`).
- [ ] Version/tag matches `CHANGELOG.md`'s `[1.0.0]` entry.

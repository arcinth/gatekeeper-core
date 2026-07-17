# End-to-End Testing Guide

This is an operator runbook for verifying GateKeeper's GitHub App integration against a real GitHub App, a real repository, and a real pull request — not WireMock, not a simulated webhook. It assumes you've already followed `INSTALLATION.md` once and can get the backend and frontend running locally; this document only covers the additional steps needed to connect a real GitHub App and watch a real pull request move through the full pipeline.

Nothing here requires deploying anywhere. The backend stays on your machine; a tunnel (ngrok or Cloudflare Tunnel) is what makes `localhost:8080` reachable by GitHub's webhook delivery.

## Prerequisites

- A GitHub account with permission to create a GitHub App (a personal account is sufficient — the App can be installed on your own repositories).
- `ngrok` or `cloudflared` installed locally.
- A test repository on GitHub you can push branches and open pull requests against. It does not need to be `gatekeeper-core` itself — any repository works, including an empty scratch one created just for this.
- PostgreSQL, the backend, and the frontend all runnable locally per `INSTALLATION.md`.

---

## 1. Start PostgreSQL

```bash
docker compose up -d postgres
```

Confirm it's healthy before continuing:

```bash
docker ps --filter name=gatekeeper-postgres
```

The `STATUS` column should read `Up ... (healthy)`. By default this listens on host port `5433` (see `docker-compose.yml` / `.env.example` — chosen specifically to avoid colliding with a Postgres instance from another project on the standard `5432`).

**Database change:** none yet. `organizations`, `roles`, and the bootstrap `users` row are seeded the first time the *backend* starts against an empty database, not by Postgres starting.

---

## 2. Start the Backend

This time, the backend needs real GitHub App credentials, not the defaults. You won't have these until Step 3 of the [GitHub App setup below](#5-configure-the-github-app-webhook-url) — come back to this step once you've created the App and have its App ID, webhook secret, and private key in place at `secrets/github-app-private-key.pem` (see `secrets/README.md`).

From `backend/`:

```bash
GITHUB_APP_ID=<your app id> \
GITHUB_WEBHOOK_SECRET=<your webhook secret> \
GITHUB_APP_PRIVATE_KEY_PATH=../secrets/github-app-private-key.pem \
./mvnw spring-boot:run
```

Wait for:

```
Started GateKeeperApplication in N.NNN seconds
```

**Database change:** Flyway applies any outstanding migrations (none, if you're already on the latest schema version). No data rows change yet.

---

## 3. Start the Frontend

From `frontend/`:

```bash
npm install
npm run dev
```

Confirm `http://localhost:5173/login` loads, and that you can sign in with the bootstrap administrator (`admin@gatekeeper.local` / `ChangeMe123!`, unless you've already changed it). You'll use the **Repositories**, **Analysis Runs**, and **Analysis Run Detail** pages later to visually confirm the pipeline ran.

**Database change:** none.

---

## 4. Expose Localhost with ngrok (or Cloudflare Tunnel)

GitHub needs a public HTTPS URL to deliver webhooks to. The backend's webhook endpoint is `POST /api/v1/github/webhook` on port **8080** (not the frontend's 5173).

**ngrok:**

```bash
ngrok http 8080
```

ngrok prints a `Forwarding` line, e.g.:

```
Forwarding    https://<random-subdomain>.ngrok-free.app -> http://localhost:8080
```

Copy the `https://...ngrok-free.app` URL — you'll append `/api/v1/github/webhook` to it in the next step.

**Cloudflare Tunnel** (alternative, no account required for a quick tunnel):

```bash
cloudflared tunnel --url http://localhost:8080
```

`cloudflared` prints a `https://<random>.trycloudflare.com` URL the same way.

**Important:** on ngrok's free tier, the URL changes every time you restart `ngrok`. If you stop and restart the tunnel, you must go back to Step 5 and update the GitHub App's webhook URL to match, or every subsequent webhook delivery will fail with a connection error on GitHub's side (visible in the App's "Recent Deliveries" tab as a failed delivery, not as anything in your own backend's logs — the request never arrives).

**Database change:** none. This step is pure networking.

---

## 5. Configure the GitHub App Webhook URL

If you haven't created the GitHub App yet:

1. Go to **Settings → Developer settings → GitHub Apps → New GitHub App** (github.com/settings/apps/new, under either your personal account or an organization you own).
2. Fill in a name and homepage URL (any placeholder URL is fine for local testing).
3. **Webhook URL:** `<your-tunnel-url>/api/v1/github/webhook` — e.g. `https://abcd1234.ngrok-free.app/api/v1/github/webhook`.
4. **Webhook secret:** generate a real random value and set it here. This must be the exact same value as `GITHUB_WEBHOOK_SECRET` in Step 2 — a mismatch causes `WebhookSignatureVerifier` to reject every delivery with `401 Unauthorized`.
5. **Permissions:** under "Repository permissions," grant **Pull requests: Read-only** at minimum (GateKeeper only reads PR metadata and changed files; it never writes back to GitHub).
6. **Subscribe to events:** check **Pull requests**. You do *not* need to separately subscribe to "Installation" or "Installation repositories" — GitHub sends those two automatically to every App regardless of event subscriptions, since they describe the App's own lifecycle rather than repository activity.
7. Under **Where can this GitHub App be installed?**, choose "Only on this account" unless you specifically want it installable elsewhere.
8. Save. GitHub will show you the **App ID** on this same page, and lets you generate a **private key** (downloads a `.pem` file in PKCS#1 format).

Convert the downloaded key and put it in place:

```bash
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
  -in downloaded-key.pem -out secrets/github-app-private-key.pem
```

Now go back to Step 2 and (re)start the backend with the real `GITHUB_APP_ID`, `GITHUB_WEBHOOK_SECRET`, and `GITHUB_APP_PRIVATE_KEY_PATH=../secrets/github-app-private-key.pem`.

If you change the webhook URL later (e.g., because ngrok restarted), you edit it on this same App settings page — **Settings → Developer settings → GitHub Apps → [your app] → General → Webhook URL**.

**Database change:** none — this is entirely GitHub-side and backend-config configuration, no request has been sent yet.

---

## 6. Install the GitHub App on gatekeeper-core

1. From the App's settings page, click **Install App** in the left sidebar (or visit the App's public page and click "Install").
2. Choose the account/organization to install it on.
3. Choose **Only select repositories**, and pick your test repository (or **All repositories** if you'd rather not pick one).
4. Click **Install**.

This single action triggers two webhook deliveries in quick succession: an `installation` event (action `created`), immediately followed by an `installation_repositories` event (action `added`) listing the repository/repositories you selected.

**Database change:** this is where real rows first appear — see Steps 7 and 8 below.

---

## 7. Verify the Installation Webhook

**Backend log** — look for:

```
Installation <installation-id> upserted for account '<your-login>' (delivery <delivery-id>, action 'created').
```

**GitHub's side** — on the App's settings page, open the **Advanced** tab. The "Recent Deliveries" list shows every webhook GitHub attempted to send, each with a response code. The `installation` delivery should show **200**. Clicking a delivery shows the exact JSON payload GitHub sent and the response your backend returned — useful for comparing against what actually got persisted if something looks wrong.

**Database** — `github_installations` should now have one row:

```sql
SELECT installation_id, github_account_login, github_account_id, github_account_type,
       repository_selection, permissions, active
FROM github_installations
ORDER BY id DESC LIMIT 1;
```

Expect `active = true`, `github_account_login` matching your GitHub username/org, and `permissions` containing the JSON permissions object you granted in Step 5 (e.g. `{"pull_requests":"read"}`).

---

## 8. Verify the `installation_repositories` Webhook

**Backend log** — look for:

```
Repository '<owner>/<repo>' linked to installation <installation-id> (delivery <delivery-id>).
```

**GitHub's side** — same Advanced/Recent Deliveries tab; the `installation_repositories` delivery should also show **200**, sent as a separate delivery immediately after the `installation` one.

**Database** — `repositories` should now have a row linked to the installation above:

```sql
SELECT name, full_name, owner, github_repository_id, github_installation_id, active
FROM repositories
WHERE full_name = '<owner>/<repo>';
```

Expect `active = true`, `owner` matching the repository owner login, and `github_installation_id` pointing at the `github_installations.id` from Step 7 (not `installation_id` — that's the GitHub-side ID; the foreign key is GateKeeper's own internal `id`).

**Frontend confirmation:** the **Repositories** page (`http://localhost:5173/repositories`) should now list this repository, showing "Active" status.

---

## 9. Create a Test Branch

Any change works, but to actually exercise both deterministic engines and see real findings, include content each engine's rules are known to match. This exact combination has been verified elsewhere in this codebase (`SecurityEngineExecutionIntegrationTest`) to trigger `TODO_COMMENT` and `FIXME_COMMENT` (Policy Engine) plus `HARDCODED_SECRET` and `INSECURE_CRYPTO_FUNCTION` (Security Engine):

```java
class Example {
    // TODO: extract helper
    // FIXME: null check missing
    String apiKey = "sk-live-abcdef1234567890";
    MessageDigest md = MessageDigest.getInstance("MD5");
}
```

```bash
git checkout -b test/gatekeeper-e2e
# add the snippet above (or similar) to any file
git add .
git commit -m "test: trigger GateKeeper policy and security findings"
git push -u origin test/gatekeeper-e2e
```

**Database change:** none — GitHub doesn't notify GateKeeper about branch pushes, only pull request events.

---

## 10. Open a Pull Request

Open a PR from `test/gatekeeper-e2e` into the repository's default branch, either via the GitHub UI or:

```bash
gh pr create --title "Test: GateKeeper E2E verification" --body "Exercises the full analysis pipeline." --base main --head test/gatekeeper-e2e
```

This triggers a `pull_request` webhook delivery (action `opened`).

**Database change:** this is the point where `pull_requests` and `analysis_runs` first get rows for this PR — see Step 11.

---

## 11. Verify the Analysis Pipeline

This is the multi-stage part — expect it to take a few seconds end to end, most of it spent on the two GitHub API calls (fetching changed files, once per engine-execution-path).

### Backend log sequence, in order

```
Persisted PR #<number> (OPEN) for repository '<owner>/<repo>' (delivery <delivery-id>).
AnalysisRun <id> (RECEIVED) recorded for PR #<number> at commit <sha> (delivery <delivery-id>).
AnalysisRun <id> queued for execution (delivery <delivery-id>).
```

Shortly after (async, on a separate thread — timestamps will show a small gap):

```
Starting policy evaluation for analysis run <id> (repository '<owner>/<repo>', <N> file(s)).
Completed policy evaluation for analysis run <id>: <N> finding(s) from <N> rule(s).
Starting security evaluation for analysis run <id> (repository '<owner>/<repo>', <N> file(s)).
Completed security evaluation for analysis run <id>: <N> finding(s) from <N> rule(s).
Persisted <N> policy finding(s), <N> security finding(s), and a <APPROVED|BLOCKED> verdict; marked analysis run <id> COMPLETED.
```

If AI Review is enabled (`AI_REVIEW_ENABLED=true` with a valid `ANTHROPIC_API_KEY`), independently and in parallel:

```
Persisted AI review run <id> for analysis run <id>: <N> finding(s) from provider '<provider>'.
```

And finally, once both the verdict and (if enabled) AI review have resolved:

```
Published engineering report <id> for analysis run <id> (aiReviewStatus=<INCLUDED|DISABLED|UNAVAILABLE>).
```

With the example content from Step 9 and AI Review left at its default (disabled), expect `2 finding(s)` from Policy (one `TODO_COMMENT`, one `FIXME_COMMENT`), `2 finding(s)` from Security (one `HARDCODED_SECRET`, one `INSECURE_CRYPTO_FUNCTION`), and a `BLOCKED` verdict — this codebase's Verdict Engine blocks on `HARDCODED_SECRET` as a critical-severity security finding.

### Confirm via the frontend

- **Analysis Runs** (`/analysis-runs`) — a new row for this PR, status `COMPLETED`, verdict `BLOCKED`.
- **Analysis Run Detail** (click into it) — the Governance Decision banner, the Policy and Security findings tables with the exact rule IDs above, and (once published) the Engineering Report panel with its AI status badge.

### Confirm via the database

```sql
SELECT status, trigger_reason, commit_sha FROM analysis_runs ORDER BY id DESC LIMIT 1;
SELECT rule_id, severity FROM policy_findings ORDER BY id DESC LIMIT 5;
SELECT rule_id, severity FROM security_findings ORDER BY id DESC LIMIT 5;
SELECT outcome FROM verdicts ORDER BY id DESC LIMIT 1;
SELECT ai_review_status, published_at FROM engineering_reports ORDER BY id DESC LIMIT 1;
```

---

## 12. Database Changes Per Step (Summary)

| Step | Tables affected |
|---|---|
| 1. Start PostgreSQL | none |
| 2. Start backend (first ever run) | `organizations`, `roles`, `users` (bootstrap admin) seeded |
| 6. Install the App → `installation` event | `github_installations` (new row) |
| 6. Install the App → `installation_repositories` event | `repositories` (new or reactivated row) |
| 10. Open the PR → `pull_request` event | `pull_requests` (new row), `analysis_runs` (new row, `RECEIVED` → `QUEUED`) |
| 11. Pipeline executes | `analysis_runs` (`QUEUED` → `IN_PROGRESS` → `COMPLETED`), `policy_findings`, `security_findings`, `verdicts`, `verdict_reasons` |
| 11. AI Review (if enabled) | `ai_review_runs`, `ai_review_findings` — independent of the row above |
| 11. Report publication | `engineering_reports`, `audit_logs` |

---

## 13. Expected Log Messages (Reference)

Grouped by pipeline stage, in the order they normally appear. All are `INFO` level unless noted.

**Webhook receipt & installation onboarding**
- `Installation <id> upserted for account '<login>' (delivery <id>, action '<action>').`
- `Repository '<full_name>' linked to installation <id> (delivery <id>).`
- `Ignoring unsupported GitHub event type '<type>' (delivery <id>).` — expected for any event type other than `pull_request`, `installation`, or `installation_repositories` (GitHub sends many more than GateKeeper acts on).

**Ingestion (pull_request event)**
- `Persisted PR #<number> (<status>) for repository '<full_name>' (delivery <id>).`
- `AnalysisRun <id> (<status>) recorded for PR #<number> at commit <sha> (delivery <id>).`
- `AnalysisRun <id> queued for execution (delivery <id>).`

**Deterministic pipeline execution**
- `Starting policy evaluation for analysis run <id> (repository '<full_name>', <N> file(s)).`
- `Completed policy evaluation for analysis run <id>: <N> finding(s) from <N> rule(s).`
- `Starting security evaluation for analysis run <id> (repository '<full_name>', <N> file(s)).`
- `Completed security evaluation for analysis run <id>: <N> finding(s) from <N> rule(s).`
- `Persisted <N> policy finding(s), <N> security finding(s), and a <outcome> verdict; marked analysis run <id> COMPLETED.`

**AI Review (only if `AI_REVIEW_ENABLED=true`)**
- `Persisted AI review run <id> for analysis run <id>: <N> finding(s) from provider '<provider>'.`
- `Recorded failed AI review run <id> for analysis run <id>: <reason>` (`WARN` level — logged instead of the line above if the provider call fails; does not affect the analysis run's own outcome).

**Report publication**
- `Published engineering report <id> for analysis run <id> (aiReviewStatus=<status>).`

**Failure paths worth recognizing**
- `Analysis run <id> failed during execution.` (`ERROR`, with a stack trace) — the deterministic pipeline itself threw; the run is marked `FAILED`.
- `AI review failed for analysis run <id>; this does not affect the analysis run's own outcome.` (`WARN`) — expected if AI Review is enabled but misconfigured or the provider errors; the deterministic verdict is unaffected either way.

---

## 14. Troubleshooting

**Webhook delivery shows a non-200 response, or no delivery appears at all in "Recent Deliveries."** Check the tunnel URL is still current (ngrok's free-tier URL changes on every restart — see Step 4) and matches exactly what's configured in the App's Webhook URL field, including the `/api/v1/github/webhook` suffix.

**Delivery shows `401` in "Recent Deliveries."** `GITHUB_WEBHOOK_SECRET` on the backend doesn't match the webhook secret configured on the GitHub App. They must be character-for-character identical. Re-check both sides; there's no way to "view" the secret again on GitHub's side once saved, so if unsure, generate a new one and update both places together.

**Backend fails to start with something like "GitHub App private key is not configured" or a PEM-parsing error.** Confirm `GITHUB_APP_PRIVATE_KEY_PATH` points at a real file and that the file is in PKCS#8 format (`-----BEGIN PRIVATE KEY-----`), not the PKCS#1 format GitHub actually downloads (`-----BEGIN RSA PRIVATE KEY-----`) — re-run the `openssl pkcs8 -topk8 ...` conversion from Step 5 if unsure which one you have.

**`installation_repositories` webhook arrives but nothing shows up in `repositories`, and the log says "Ignoring installation_repositories event for unknown installation."** The `installation` event for this same installation was never processed (check for its own log line first — Step 7). This can happen if the backend was down or misconfigured when the App was first installed; reinstalling or reconfiguring permissions on the App re-sends both events.

**`pull_request` webhook arrives (log shows nothing) or the delivery 200s but no `AnalysisRun` appears.** Check for `Skipping delivery <id>: GitHub repository id <id> has no linked GateKeeper repository.` in the log — this means Step 6/8 never completed for this specific repository (it exists on GitHub and the App is installed, but GateKeeper never got or processed the `installation_repositories` event for it). Re-verify Step 8.

**Port 8080 already in use when starting the backend.** A previous run is still holding the port — this is a common leftover from a prior `spring-boot:run` that didn't shut down cleanly (especially after killing the terminal rather than stopping the process). Find and stop it: on Windows, `Get-NetTCPConnection -LocalPort 8080 | Select OwningProcess`, then `Stop-Process -Id <pid> -Force`; on macOS/Linux, `lsof -ti:8080 | xargs kill`.

**Port 5432/5433 conflicts, or `password authentication failed for user "gatekeeper"`.** Something else on the machine (another project's Postgres container, most commonly) is bound to the port GateKeeper's Postgres expects, and the backend ends up talking to the wrong database entirely. Confirm with `docker ps` which container actually owns port 5433, and that `docker ps --filter name=gatekeeper-postgres` shows `Up ... (healthy)` with that exact port mapping.

**AI Review section never populates, no AI-related log lines appear at all.** Expected if `AI_REVIEW_ENABLED` isn't set to `true`, or `ANTHROPIC_API_KEY` isn't set — this is the documented default, not a failure. The Engineering Report's `aiReviewStatus` will show `DISABLED` rather than an error.

**Verdict is `APPROVED` when you expected `BLOCKED` (or vice versa).** Confirm the actual diff content in your test branch matches what a rule looks for — e.g. `TODO`/`FIXME` are matched case-insensitively as whole words (`\bTODO\b`), so `TODOO` or `mytodo` won't match. If in doubt, use the exact snippet from Step 9, already confirmed elsewhere in this codebase to trigger both engines.

**Frontend shows nothing after all of this.** Confirm you're looking at the right window — the Analysis Runs list has no auto-refresh; reload the page. Also confirm `VITE_API_BASE_URL` (or its default, `http://localhost:8080/api/v1`) actually points at the backend you're running.

# secrets/

Local-only home for credential material that should never be committed — currently, the GitHub App private key.

## GitHub App private key

Place the PKCS#8 PEM file GitHub gives you here as:

```
secrets/github-app-private-key.pem
```

GitHub issues the key in PKCS#1 format; convert it first:

```bash
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
  -in downloaded-key.pem -out secrets/github-app-private-key.pem
```

Then point the backend at it with `GITHUB_APP_PRIVATE_KEY_PATH`:

- Running with `./mvnw spring-boot:run` from `backend/`, use a path relative to that working directory (e.g. `../secrets/github-app-private-key.pem`) or an absolute path.
- Running with `docker compose up`, this directory is mounted read-only into the `backend` container at `/secrets`, so use `/secrets/github-app-private-key.pem`.

See `INSTALLATION.md` for the full list of GitHub integration environment variables.

## Why this directory exists

Everything else in `.env`/environment variables is short strings (IDs, secrets, tokens) that are easy to pass as plain environment values. A PEM private key is multi-line and awkward to carry in a single environment variable — a file path is the more standard way to hand it to the application. Nothing under this directory except this README and `.gitkeep` is tracked by git; see `.gitignore`.

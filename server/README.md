# iskolarPath Server

Spring Boot REST API for the centralized iskolarPath catalog + auth (Phase 2 of
the hub-and-spoke architecture). The JavaFX desktop app polls this service for
the scholarship catalog and uses the cached snapshot for fully offline matching.

## Endpoints

| Method | Path                 | Auth   | Description                                  |
|--------|----------------------|--------|----------------------------------------------|
| GET    | `/api/health`        | public | Liveness probe (Render health check / pings) |
| POST   | `/api/auth/register` | public | Create a STUDENT account (201 + JWT)         |
| POST   | `/api/auth/login`    | public | Verify credentials (200 + JWT / 401)         |
| GET    | `/api/auth/me`       | Bearer | Current caller (401 without a valid token)   |
| POST   | `/api/auth/forgot`   | public | Request a password-reset token (no enumeration) |
| POST   | `/api/auth/reset`    | public | Reset password with a one-time token         |
| GET    | `/api/catalog/version` | public | `{ "version": 1 }` — bumped on admin writes |
| GET    | `/api/catalog`       | public | Full catalog snapshot (JSON)                 |

## Tech

- Java 21, Spring Boot 3.5, Spring MVC + JDBC
- PostgreSQL via Flyway migrations (`db/migration/`)
- bcrypt (jBCrypt, cost 12 — same algorithm as the desktop app)
- JWT (HS256, 30-day expiry) via jjwt
- Tests run against a real embedded PostgreSQL (zonky), so Flyway + SQL are
  verified exactly as they'll run on Neon

## Run locally

You need a local PostgreSQL. Quickest way:

```bash
docker run -d --name iskolar-pg -e POSTGRES_PASSWORD=iskolar -e POSTGRES_DB=iskolar -p 5432:5432 postgres:16-alpine
```

Then:

```bash
cd server
mvn spring-boot:run
```

Defaults point at `jdbc:postgresql://localhost:5432/iskolar`, user `postgres`.
If you created the container with the command above, provide its password:

```powershell
$env:ISC_DB_PASSWORD = "iskolar"
mvn spring-boot:run
```

### Configuration (environment variables)

| Variable           | Purpose                                   | Default                     |
|--------------------|-------------------------------------------|-----------------------------|
| `ISC_DB_URL`       | `postgres://` or `postgresql://` URL      | localhost default           |
| `ISC_DB_JDBC_URL`  | Full `jdbc:postgresql://` URL             | localhost default           |
| `ISC_DB_USERNAME`  | Database user (optional — extracted from `ISC_DB_URL` when it has credentials) | `postgres`  |
| `ISC_DB_PASSWORD`  | Database password (optional — same)       | (empty)                     |
| `ISC_JWT_SECRET`   | HS256 signing secret (≥ 32 chars)         | dev-only default            |
| `ISC_SMTP_HOST`    | SMTP host for reset emails (see below)    | (empty = dev mode)          |
| `PORT`             | HTTP port                                 | `8080`                      |

`ISC_DB_URL` wins over `ISC_DB_JDBC_URL` and accepts the `postgres://` scheme
Neon/Render provide (normalized to `jdbc:` at startup). Credentials embedded in
the URL are extracted into the datasource properties automatically, so a Neon
string like `postgresql://user:pass@host/db` works with **no** extra variables;
explicit `ISC_DB_USERNAME` / `ISC_DB_PASSWORD` values override the URL when
both are set.

## Test

```bash
cd server
mvn test
```

First run downloads the embedded PostgreSQL binaries (~30 MB).

## Deploy (free tier)

**Blueprint (recommended).** The repo root contains `render.yaml`, so Render
can create the service in one click:

1. **Neon** — create a free Postgres project and copy the connection string
   (`postgresql://user:pass@host/db?sslmode=require`). Flyway creates + seeds
   the schema on first boot.
2. **Render** — *New + → Blueprint* → connect this repo. Fill in the
   `ISC_DB_URL` variable with the Neon string (its embedded credentials are
   extracted automatically — set `ISC_DB_USERNAME` / `ISC_DB_PASSWORD` only if
   the URL has none), then deploy. `PORT` and `ISC_JWT_SECRET` are handled by
   the blueprint.
3. **Verify** — `GET https://<service>.onrender.com/api/health` → `{"status":"ok"}`,
   then `/api/catalog/version` → `{"version":1}`.
4. **Point the desktop app** at the URL (it defaults to
   `http://localhost:8080` and reads the `iskolar.apiUrl` system property).

Manual service (same result): *New + → Web Service* → repo → **Docker**
runtime, **Dockerfile path** `server/Dockerfile`, **Root Directory** empty
(repo root — the Dockerfile expects the root as build context), health check
`/api/health`, same environment variables.

**Neon gotchas**

- Keep `?sslmode=require` in the connection string — Neon requires SSL.
- If the first boot fails during the Flyway migration with a
  PgBouncer/transaction-pooling error, you copied Neon's **pooled** host
  (`…-pooler.…`). Switch to the **direct** host and enable Neon's free IPv4
  add-on — Render cannot reach IPv6-only hosts.
- **Cold start** — Render's free tier sleeps after ~15 min idle (30–60 s wake),
  and Neon auto-suspends too. A free cron ping (e.g. cron-job.org hitting
  `/api/health` every 10 min) keeps the API warm within the 750 free
  hours/month; the desktop app retries failed syncs automatically.

## Notes

- Public registration always creates STUDENT accounts; ADMIN accounts are
  provisioned directly in the database.
- Catalog writes (admin) bump `app_state.catalog_version` so clients detect the
  change.
- **Password reset (dev mode)** — with no `ISC_SMTP_HOST` set, `POST
  /api/auth/forgot` logs the single-use reset token to the server console
  (`DEV MODE password-reset token for …`); paste it into the desktop app's
  "Forgot password?" dialog. Set `ISC_SMTP_HOST` to route tokens through a real
  SMTP relay (`MailService` is the integration point).
- Rate limiting and refresh-token rotation remain future work.

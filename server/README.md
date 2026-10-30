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

### Configuration (environment variables)

| Variable           | Purpose                                   | Default                     |
|--------------------|-------------------------------------------|-----------------------------|
| `ISC_DB_URL`       | `postgres://` or `postgresql://` URL      | localhost default           |
| `ISC_DB_JDBC_URL`  | Full `jdbc:postgresql://` URL             | localhost default           |
| `ISC_DB_USERNAME`  | Database user                             | `postgres`                  |
| `ISC_DB_PASSWORD`  | Database password                         | (empty)                     |
| `ISC_JWT_SECRET`   | HS256 signing secret (≥ 32 chars)         | dev-only default            |
| `ISC_SMTP_HOST`    | SMTP host for reset emails (see below)    | (empty = dev mode)          |
| `PORT`             | HTTP port                                 | `8080`                      |

`ISC_DB_URL` wins over `ISC_DB_JDBC_URL` and accepts the `postgres://` scheme
Neon/Render provide (normalized to `jdbc:` at startup).

## Test

```bash
cd server
mvn test
```

First run downloads the embedded PostgreSQL binaries (~30 MB).

## Deploy (free tier)

1. **Neon** — create a free Postgres project; copy the connection string
   (`postgresql://user:pass@host/db`). Flyway creates + seeds the schema on
   first boot.
2. **Render** — create a new Web Service from this repo (or push the
   `render.yaml` blueprint), set `ISC_DB_URL` / `ISC_DB_USERNAME` /
   `ISC_DB_PASSWORD`, and deploy. The `PORT` and `ISC_JWT_SECRET` variables are
   handled by the blueprint.
3. **Stay warm (optional)** — Render's free tier sleeps after ~15 min idle and
   cold-starts in 30–60 s. A free cron ping (e.g. UptimeRobot / cron-job.org
   hitting `/api/health` every 10 min) keeps it responsive within the 750
   free hours/month.

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

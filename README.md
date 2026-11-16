# iskolarPath: Discover, match, and track your scholarship journey.

Desktop scholarship discovery, two-stage weighted matching, and Kanban
application tracking for Filipino students.

> **Latest release:** [`v1.0.3`](https://github.com/Unfien-2007/iskolarsql/releases/tag/v1.0.3) — Windows installer with SHA-256 checksum.  
> **Live API:** [`iskolarpath-api.onrender.com/api/health`](https://iskolarpath-api.onrender.com/api/health)

| Layer        | Technology |
|--------------|------------|
| Language     | Java 21 LTS (Records, Switch Expressions, Pattern Matching) |
| Build        | Apache Maven |
| UI           | JavaFX 21 (FXML + CSS) |
| Theme        | AtlantaFX — `PrimerLight` / `PrimerDark` one-click toggle |
| Icons        | Ikonli — Feather + Material Design packs |
| Database     | SQLite via JDBC + HikariCP pool (local cache) |
| Server       | Spring Boot REST API + PostgreSQL (Neon) |
| Deployment   | Render (Docker) + GitHub Actions release pipeline |
| Security     | jBCrypt hashing, JWT server sessions, Java `Preferences` for theme/session state |
| Tests        | JUnit 5 |

## Architecture

Classic **MVC + DAO** layered flow:

```
controller (C) ──► service (business rules) ──► dao (SQL) ──► SQLite (HikariCP)
     ▲                                                          │
     └────────────── view (V): FXML + CSS ◄── model/dto (M) ◄───┘
```

| Package | Responsibility | Delivered |
|---|---|---|
| `com.iskolar` | `Launcher` + `ScholarshipApp` (Stage/BorderPane shell) | Phase 4 |
| `com.iskolar.config` | `DatabaseConfig` — HikariCP pool, schema bootstrap | Phase 2 |
| `com.iskolar.model` | Domain Records + enums (Role, SchoolType, MatchTier, …) | Phase 2 |
| `com.iskolar.dto` | `MatchResult`, `ProfileWizardData`, card projections | Phase 3/5 |
| `com.iskolar.dao` | One DAO per aggregate, prepared statements | Phase 2 |
| `com.iskolar.service` | `MatchingService` (2-stage engine), Auth, Profile | Phase 3 |
| `com.iskolar.controller` | One controller per FXML view | Phase 5 |
| `com.iskolar.util` | `ViewNavigator`, `ThemeManager`, `SessionManager` | Phase 4 |

## Phase Checklist

- [x] **Phase 1** — `pom.xml`, package skeleton, `schema.sql` + `seed.sql`
- [x] **Phase 2** — `DatabaseConfig` (HikariCP pool + first-boot bootstrap), 9 enums + 9 Records, 7 DAOs + `DaoFactory` — verified by a 31-point smoke run (reads against the bootstrapped file DB, writes against an in-memory DB)
- [x] **Phase 3** — `MatchingService` two-stage engine (6 hard gates + 35/30/20/15 weighted score, Borderline-Grade grace zone, `MatchResult` with tier + warnings) — 100 JUnit 5 tests, including seeded-DB integration scenarios
- [x] **Phase 4** — App shell (`Launcher`/`ScholarshipApp`), collapsible sidebar rail (`ShellView`), `ViewNavigator`, `ThemeManager` (AtlantaFX PrimerLight/PrimerDark + Preferences), `SessionManager`, `PasswordUtils`, `AuthService` — verified live: `mvn javafx:run` launches and renders cleanly
- [x] **Phase 5a** — Login/Register FXML + controllers (BCrypt auth, Enter-submit, inline errors) and the Scholarship Discovery Grid: compact Filters popover (program/year/provider/location), responsive card flow, provider badges, deadline urgency pills, acceptance-rate estimates, tier-colored match badges with tooltip warnings, guest sign-in banner — verified live with zero CSS warnings
- [x] **Phase 5b** — 4-step Profile Wizard (progressive saves, GWA 5-point/percent conversion, tag + document vault), Kanban Application Tracker (6 columns, transition-validated moves, match badges), side-by-side Comparison modal (up to 3), Admin Portal (catalog table + full eligibility-rule editor with acceptance-rate/awards fields and atomic transactions), card Save/Compare actions, launch splash animation — **all phases complete**
- [x] **Phase 6** — Central catalog service: Spring Boot REST API, PostgreSQL + Flyway migrations, JWT auth, desktop sync with offline cache, Render + Neon deployment, and tag-driven Windows installer releases

## Build

```powershell
mvn -q compile     # compile the desktop app
mvn -q test        # run the JUnit 5 suite
mvn javafx:run     # launch the JavaFX app

cd server
mvn -q test        # run the server integration tests
```

## Download

Grab the latest Windows installer from the
[Releases page](https://github.com/Unfien-2007/iskolarsql/releases).
Every release publishes `iskolarPath-Setup-<version>.exe` together with a
`.sha256` checksum.

Pushing a tag such as `v1.0.4` triggers
`.github/workflows/release.yml`, which builds the installer on
`windows-latest` and publishes the GitHub Release automatically.

## Keyboard & interaction

- `Ctrl+F` focuses the Discover search box; `Esc` clears it without losing the rest of the filters.
- `Ctrl+1…4` jump to Discover, My Applications, My Profile and Admin Portal — the same order as the sidebar.
- Tab to a scholarship or tracker card and press `Enter`/`Space` to open its details, so the grid is usable without a mouse.
- `Esc` closes the details drawer and the comparison sheet; focus returns to whatever opened them.
- Compare ticks survive navigation between Discover and the tracker, and the sidebar **Compare** entry opens the sheet once two or three scholarships are ticked.
- The intro splash plays on every launch; pass `-Diskolar.skipIntro=true` to bypass it.
- The window size and the last theme are remembered on the next launch.

## Database

`src/main/resources/db/schema.sql` and `seed.sql` define the baseline SQLite
schema (3NF) and realistic Philippine seed data. Phase 2's `DatabaseConfig`
auto-applies both on first boot, then `SchemaMigrator` applies any pending
scripts from `src/main/resources/db/migrations/` (currently through
`v4__more_scholarships.sql`). The database file lives at
`%APPDATA%\Iskolar\iskolar.db` (WAL mode, foreign keys enforced per
connection).

The bundled catalog contains **22 Philippine scholarships**. Each scholarship
may also carry an estimated acceptance rate and an awards-available count;
both are optional because providers do not always publish them.

**Seed account** (password: `password`)

- `admin@iskolar.app` — ADMIN

Student accounts are created through registration in the app (the account is
verified against the catalog server once, then works offline from the local
cache). The sample student profiles used by the automated tests live in
`src/test/resources/db/test_profiles.sql`.

### Key engine conventions baked into the schema

- Empty `scholarship_allowed_programs` / `scholarship_allowed_years` → open to **all** programs / years
- `max_annual_income IS NULL` → **merit-only** (full financial points in Stage 2)
- `min_gwa_percentage IS NULL` → no academic minimum
- `required_province IS NULL` → no residency gate
- `is_mandatory = 1` tag → hard disqualifier; `0` → preferred-tag bonus
- `acceptance_rate IS NULL` / `awards_available IS NULL` → selection data not published; cards hide the rate and the details drawer shows “Not published”
- Deadlines in `seed.sql` and the add-on migrations are relative to today, so urgency pills always demo correctly

## Central catalog server (optional)

The `server/` directory is a standalone Spring Boot module that turns the app
into a hub-and-spoke system: a central PostgreSQL catalog + accounts, with the
desktop app working fully offline on cached data.

- **Endpoints** — `POST /api/auth/register`, `POST /api/auth/login`,
  `GET /api/auth/me`, `POST /api/auth/forgot`, `POST /api/auth/reset`,
  `GET /api/catalog/version`, `GET /api/catalog`, admin-only
  `POST/PUT/DELETE /api/admin/scholarships`, `GET /api/health`
- **Catalog** — Flyway migrations (`V1`–`V5`) create and evolve the shared
  catalog, including the 22 seeded scholarships; every admin write bumps
  `app_state.catalog_version` so connected clients re-sync
- **Client behavior** — catalog auto-syncs on startup with retry/backoff;
  login is online-first with an offline credential cache; admin edits and
  registration require a connection
- **Live deployment** — the API runs at
  `https://iskolarpath-api.onrender.com` with PostgreSQL hosted on Neon;
  health is exposed at `/api/health`. Free-tier services may take 30–60
  seconds to wake after idling
- **Deploy** — a `render.yaml` Blueprint at the repo root deploys it to
  Render with a Neon PostgreSQL database (both free); see
  [`server/README.md`](server/README.md) for the full walkthrough and local
  setup (Docker Postgres + `cd server && mvn spring-boot:run`)

The app runs exactly as before without a server — the bundled catalog and
local accounts remain the default.

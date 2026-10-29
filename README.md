# iskolarPath: Discover, match, and track your scholarship journey.

Desktop scholarship discovery, two-stage weighted matching, and Kanban
application tracking for Filipino students.

| Layer        | Technology |
|--------------|------------|
| Language     | Java 21 LTS (Records, Switch Expressions, Pattern Matching) |
| Build        | Apache Maven |
| UI           | JavaFX 21 (FXML + CSS) |
| Theme        | AtlantaFX — `PrimerLight` / `PrimerDark` one-click toggle |
| Icons        | Ikonli — Feather + Material Design packs |
| Database     | SQLite via JDBC + HikariCP pool |
| Security     | jBCrypt hashing, Java `Preferences` for theme/session state |
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
- [x] **Phase 3** — `MatchingService` two-stage engine (6 hard gates + 35/30/20/15 weighted score, Borderline-Grade grace zone, `MatchResult` with tier + warnings) — 60 JUnit 5 tests, including seeded-DB integration scenarios
- [x] **Phase 4** — App shell (`Launcher`/`ScholarshipApp`), collapsible sidebar rail (`ShellView`), `ViewNavigator`, `ThemeManager` (AtlantaFX PrimerLight/PrimerDark + Preferences), `SessionManager`, `PasswordUtils`, `AuthService` — verified live: `mvn javafx:run` launches and renders cleanly
- [x] **Phase 5a** — Login/Register FXML + controllers (BCrypt auth, Enter-submit, inline errors) and the Scholarship Discovery Grid: filter bar (program/year/provider/location), responsive card flow, provider badges, deadline urgency pills, tier-colored match badges with tooltip warnings, guest sign-in banner — verified live with zero CSS warnings
- [x] **Phase 5b** — 4-step Profile Wizard (progressive saves, GWA 5-point/percent conversion, tag + document vault), Kanban Application Tracker (6 columns, transition-validated moves, match badges), side-by-side Comparison modal (up to 3), Admin Portal (catalog table + full eligibility-rule editor with atomic transactions), card Save/Compare actions — **all phases complete**

## Build

```powershell
mvn -q compile     # works today (Phase 1)
mvn -q test        # Phase 3+ (MatchingService tests)
mvn javafx:run     # Phase 4+ (Launcher delivered in Phase 4)
```

## Database

`src/main/resources/db/schema.sql` and `seed.sql` define the SQLite schema
(3NF) and realistic Philippine seed data. Phase 2's `DatabaseConfig`
auto-applies both on first boot — no manual setup needed. The database file
lives at `%APPDATA%\Iskolar\iskolar.db` (WAL mode, foreign keys enforced per
connection).

**Demo accounts** (password: `password`)

- `admin@iskolar.app` — ADMIN
- `maria.santos@email.com` — STUDENT (BSIT, GWA 91.50, missing ITR)
- `juan.delacruz@email.com` — STUDENT (BSCE, GWA 86.25, 4Ps/PWD)

### Key engine conventions baked into the schema

- Empty `scholarship_allowed_programs` / `scholarship_allowed_years` → open to **all** programs / years
- `max_annual_income IS NULL` → **merit-only** (full financial points in Stage 2)
- `min_gwa_percentage IS NULL` → no academic minimum
- `required_province IS NULL` → no residency gate
- `is_mandatory = 1` tag → hard disqualifier; `0` → preferred-tag bonus
- Deadlines in `seed.sql` are relative to today, so urgency pills always demo correctly

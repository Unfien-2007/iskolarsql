-- =====================================================================
-- iskolarPath: Discover, match, and track your scholarship journey.
-- schema.sql  |  SQLite 3.x dialect  |  Normalized to 3NF
-- =====================================================================
-- Usage (CLI inspection only — Phase 2's DatabaseConfig auto-applies
-- this script on first boot):
--   sqlite3 iskolar.db < schema.sql
--   sqlite3 iskolar.db < seed.sql
--
-- SQLite dialect conventions:
--   Booleans    -> INTEGER (0 = false, 1 = true)
--   Dates       -> TEXT ISO-8601 'YYYY-MM-DD'
--   Timestamps  -> TEXT ISO-8601 UTC, DEFAULT (datetime('now'))
--   Money       -> NUMERIC(12,2)
--   GWA         -> REAL on a standardized 0-100 scale:
--                  gwa_percentage = (5.0 - gwa_5point) * 25   e.g. 1.25 -> 93.75
--
-- Matching-engine conventions (Stage 1 hard gates):
--   * scholarship_allowed_programs EMPTY -> open to ALL degree programs
--   * scholarship_allowed_years    EMPTY -> open to ALL year levels
--   * scholarship_criteria.min_gwa_percentage  NULL -> no academic minimum
--   * scholarship_criteria.max_annual_income   NULL -> MERIT-ONLY (no income
--     cap); Stage 2 then awards full financial points
--   * scholarship_criteria.allowed_school_type NULL -> PUBLIC + PRIVATE OK
--   * scholarship_criteria.required_province   NULL -> no hard residency gate
--   * scholarship_required_tags.is_mandatory = 1 -> HARD disqualifier when the
--     student lacks the tag; 0 -> preferred tag (Stage 2 weighted bonus only)
-- =====================================================================

PRAGMA foreign_keys = ON;

-- ---------------------------------------------------------------------
-- users — authentication + role + persisted theme preference
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    email         TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT    NOT NULL,                    -- bcrypt via jBCrypt
    role          TEXT    NOT NULL DEFAULT 'STUDENT'
                  CHECK (role IN ('STUDENT','ADMIN')),
    theme_pref    TEXT    NOT NULL DEFAULT 'LIGHT'
                  CHECK (theme_pref IN ('LIGHT','DARK')),
    created_at    TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ---------------------------------------------------------------------
-- degree_programs — lookup of CHED-recognized programs
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS degree_programs (
    program_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    program_code   TEXT NOT NULL UNIQUE,               -- e.g. 'BSIT'
    program_name   TEXT NOT NULL,                      -- e.g. 'BS Information Technology'
    field_category TEXT NOT NULL                       -- ICT | ENGINEERING | BUSINESS | HEALTH |
                                                       -- SCIENCE | EDUCATION | HUMANITIES |
                                                       -- AGRICULTURE | DESIGN | SOCIAL_SCIENCE
);

-- ---------------------------------------------------------------------
-- student_profiles — 1:1 with users (STUDENT role)
-- Wizard Steps 1-3 persist here; demographic tags live in student_tags.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_profiles (
    student_id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id              INTEGER NOT NULL UNIQUE
                         REFERENCES users(user_id) ON DELETE CASCADE,
    first_name           TEXT    NOT NULL,
    last_name            TEXT    NOT NULL,
    birthdate            TEXT,                         -- 'YYYY-MM-DD'
    sex                  TEXT    CHECK (sex IN ('MALE','FEMALE','PREFER_NOT_TO_SAY')),
    province             TEXT,
    city_municipality    TEXT,
    school_name          TEXT,
    school_type          TEXT    CHECK (school_type IN ('PUBLIC','PRIVATE')),
    program_id           INTEGER REFERENCES degree_programs(program_id),
    year_level           INTEGER CHECK (year_level BETWEEN 1 AND 5),
    gwa_percentage       REAL    CHECK (gwa_percentage BETWEEN 0 AND 100),
    has_failing_grade    INTEGER NOT NULL DEFAULT 0,
    annual_family_income NUMERIC(12,2),
    household_size       INTEGER CHECK (household_size > 0)   -- Wizard Step 2
);

-- ---------------------------------------------------------------------
-- scholarships — the scholarship catalog
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scholarships (
    scholarship_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    created_by          INTEGER NOT NULL REFERENCES users(user_id),
    title               TEXT    NOT NULL,
    provider_name       TEXT    NOT NULL,
    provider_type       TEXT    NOT NULL                    -- GOVERNMENT | GOVERNMENT_OWNED_CORP |
                                                            -- PRIVATE_CORPORATE | NGO_FOUNDATION |
                                                            -- UNIVERSITY | LOCAL_GOVERNMENT |
                                                            -- INTERNATIONAL
                  CHECK (provider_type IN ('GOVERNMENT','GOVERNMENT_OWNED_CORP',
                                           'PRIVATE_CORPORATE','NGO_FOUNDATION',
                                           'UNIVERSITY','LOCAL_GOVERNMENT','INTERNATIONAL')),
    description         TEXT,
    tuition_benefit     TEXT,                               -- e.g. 'FULL TUITION', 'PHP 40,000/year'
    monthly_stipend     NUMERIC(12,2) DEFAULT 0,
    return_service_years INTEGER NOT NULL DEFAULT 0,
    application_url     TEXT,
    open_date           TEXT,                               -- 'YYYY-MM-DD'
    deadline_date       TEXT,                               -- 'YYYY-MM-DD'
    is_active           INTEGER NOT NULL DEFAULT 1
);

-- ---------------------------------------------------------------------
-- scholarship_criteria — 1:1 eligibility rules per scholarship
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scholarship_criteria (
    criteria_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    scholarship_id       INTEGER NOT NULL UNIQUE
                         REFERENCES scholarships(scholarship_id) ON DELETE CASCADE,
    min_gwa_percentage   REAL,                             -- NULL = no academic minimum
    allow_failing_grade  INTEGER NOT NULL DEFAULT 0,
    max_annual_income    NUMERIC(12,2),                    -- NULL = merit-only, no income cap
    allowed_school_type  TEXT CHECK (allowed_school_type IN ('PUBLIC','PRIVATE') OR
                                     allowed_school_type IS NULL),
    required_province    TEXT,                             -- NULL = no residency gate
    required_city        TEXT
);

-- ---------------------------------------------------------------------
-- scholarship_allowed_programs — junction (EMPTY = open to all programs)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scholarship_allowed_programs (
    scholarship_id INTEGER NOT NULL REFERENCES scholarships(scholarship_id) ON DELETE CASCADE,
    program_id     INTEGER NOT NULL REFERENCES degree_programs(program_id) ON DELETE CASCADE,
    PRIMARY KEY (scholarship_id, program_id)
);

-- ---------------------------------------------------------------------
-- scholarship_allowed_years — junction (EMPTY = open to all year levels)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scholarship_allowed_years (
    scholarship_id INTEGER NOT NULL REFERENCES scholarships(scholarship_id) ON DELETE CASCADE,
    year_level     INTEGER NOT NULL CHECK (year_level BETWEEN 1 AND 5),
    PRIMARY KEY (scholarship_id, year_level)
);

-- ---------------------------------------------------------------------
-- tags + junctions — special demographics / attributes
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tags (
    tag_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    tag_code    TEXT NOT NULL UNIQUE,                     -- e.g. 'DOST_PRIORITY'
    tag_name    TEXT NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS student_tags (
    student_id INTEGER NOT NULL REFERENCES student_profiles(student_id) ON DELETE CASCADE,
    tag_id     INTEGER NOT NULL REFERENCES tags(tag_id) ON DELETE CASCADE,
    PRIMARY KEY (student_id, tag_id)
);

CREATE TABLE IF NOT EXISTS scholarship_required_tags (
    scholarship_id INTEGER NOT NULL REFERENCES scholarships(scholarship_id) ON DELETE CASCADE,
    tag_id         INTEGER NOT NULL REFERENCES tags(tag_id) ON DELETE CASCADE,
    is_mandatory   INTEGER NOT NULL DEFAULT 1,            -- 1 = hard gate, 0 = preferred
    PRIMARY KEY (scholarship_id, tag_id)
);

-- ---------------------------------------------------------------------
-- document_types + junctions — Document Readiness Vault (Wizard Step 4)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS document_types (
    document_type_id INTEGER PRIMARY KEY AUTOINCREMENT,
    doc_code         TEXT NOT NULL UNIQUE,                -- e.g. 'GRADES_CTC'
    doc_name         TEXT NOT NULL,
    description      TEXT
);

CREATE TABLE IF NOT EXISTS student_documents (
    student_id       INTEGER NOT NULL REFERENCES student_profiles(student_id) ON DELETE CASCADE,
    document_type_id INTEGER NOT NULL REFERENCES document_types(document_type_id) ON DELETE CASCADE,
    is_ready         INTEGER NOT NULL DEFAULT 0,
    updated_at       TEXT    NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (student_id, document_type_id)
);

CREATE TABLE IF NOT EXISTS scholarship_documents (
    scholarship_id   INTEGER NOT NULL REFERENCES scholarships(scholarship_id) ON DELETE CASCADE,
    document_type_id INTEGER NOT NULL REFERENCES document_types(document_type_id) ON DELETE CASCADE,
    PRIMARY KEY (scholarship_id, document_type_id)
);

-- ---------------------------------------------------------------------
-- applications — Kanban tracker (one card per student × scholarship)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS applications (
    application_id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id     INTEGER NOT NULL REFERENCES student_profiles(student_id) ON DELETE CASCADE,
    scholarship_id INTEGER NOT NULL REFERENCES scholarships(scholarship_id) ON DELETE CASCADE,
    status         TEXT    NOT NULL DEFAULT 'SAVED'
                   CHECK (status IN ('SAVED','PREPARING_DOCS','APPLIED',
                                     'INTERVIEW','AWARDED','REJECTED')),
    applied_date   TEXT,                                  -- set when status -> APPLIED
    updated_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    notes          TEXT,
    UNIQUE (student_id, scholarship_id)
);

-- ---------------------------------------------------------------------
-- Indexes — tuned for the discovery grid and matching engine
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_scholarships_discovery
    ON scholarships (is_active, deadline_date);
CREATE INDEX IF NOT EXISTS idx_criteria_scholarship
    ON scholarship_criteria (scholarship_id);
CREATE INDEX IF NOT EXISTS idx_allowed_programs_sch
    ON scholarship_allowed_programs (scholarship_id);
CREATE INDEX IF NOT EXISTS idx_allowed_years_sch
    ON scholarship_allowed_years (scholarship_id);
CREATE INDEX IF NOT EXISTS idx_student_tags_student
    ON student_tags (student_id);
CREATE INDEX IF NOT EXISTS idx_scholarship_tags_sch
    ON scholarship_required_tags (scholarship_id);
CREATE INDEX IF NOT EXISTS idx_student_documents_student
    ON student_documents (student_id);
CREATE INDEX IF NOT EXISTS idx_scholarship_documents_sch
    ON scholarship_documents (scholarship_id);
CREATE INDEX IF NOT EXISTS idx_applications_student
    ON applications (student_id);

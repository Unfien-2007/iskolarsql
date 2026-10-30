-- Phase 1: central-database foundation tables.
-- sync_state tracks sync bookkeeping (e.g. the server catalog version).
-- auth_cache caches credentials so students can log in offline.

CREATE TABLE IF NOT EXISTS sync_state (
    key        TEXT PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS auth_cache (
    user_id        INTEGER PRIMARY KEY,
    email          TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash  TEXT NOT NULL,
    role           TEXT NOT NULL CHECK (role IN ('STUDENT', 'ADMIN')),
    last_online_at TEXT
);

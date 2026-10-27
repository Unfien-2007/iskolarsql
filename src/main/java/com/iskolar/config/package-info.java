/**
 * Application configuration (Phase 2).
 *
 * <ul>
 *   <li>{@code DatabaseConfig} — HikariCP pool factory for the SQLite file,
 *       foreign-keys/WAL pragmas, and first-boot bootstrap of
 *       {@code db/schema.sql} + {@code db/seed.sql}.</li>
 *   <li>{@code AppPaths} — resolves the per-user data directory holding the
 *       {@code iskolar.db} file.</li>
 * </ul>
 */
package com.iskolar.config;

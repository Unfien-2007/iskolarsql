package com.iskolar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Ordered schema migrations for the local SQLite database.
 *
 * <p>Migration state lives in the {@code schema_version} table. Version 1 is the
 * baseline — the original schema plus seed data that shipped with the app.
 * Installations created before this runner existed are detected by the presence
 * of the {@code users} table and recorded as version 1 without re-applying any
 * SQL, so existing student data is never touched. Later versions are plain SQL
 * scripts under {@code db/migrations/}, each applied once, in order, inside a
 * transaction. Migration scripts must be idempotent (use {@code IF NOT EXISTS}).
 *
 * <p>This is the Phase 1 foundation for the centralized catalog:
 * {@code sync_state} will track the server catalog version and
 * {@code auth_cache} will cache credentials for offline login.
 */
public final class SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    private static final String BASELINE_DESCRIPTION = "Baseline schema and seed data";

    /**
     * @param version     numeric schema version, ascending
     * @param description short human-readable description stored in schema_version
     * @param scriptPath  classpath SQL script, or {@code null} for the baseline
     *                    (version 1), which is special-cased
     */
    private record Migration(int version, String description, String scriptPath) {
    }

    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, BASELINE_DESCRIPTION, null),
            new Migration(2, "Central-database foundation: sync_state + auth_cache",
                    "/db/migrations/v2__sync_state_and_auth_cache.sql")
    );

    private SchemaMigrator() {
    }

    /**
     * Brings the database up to the latest schema version. Safe to call
     * repeatedly; already-applied migrations are skipped.
     */
    public static void migrate(Connection conn) throws SQLException {
        ensureVersionTable(conn);
        int current = currentVersion(conn);

        if (current < 1) {
            if (DatabaseConfig.tableExists(conn, "users")) {
                // Pre-existing install created before the migration runner:
                // its schema is exactly the baseline, so just mark it.
                log.info("Existing database detected - recording baseline schema version 1");
                recordVersion(conn, 1, "Baseline (existing installation)");
            } else {
                log.info("First boot - applying baseline schema and seed data");
                try (Statement st = conn.createStatement()) {
                    // executeUpdate drives xerial's multi-statement loop reliably;
                    // execute() bails early on scripts producing result rows.
                    st.executeUpdate(DatabaseConfig.readResource("/db/schema.sql"));
                    st.executeUpdate(DatabaseConfig.readResource("/db/seed.sql"));
                }
                recordVersion(conn, 1, BASELINE_DESCRIPTION);
                log.info("Database bootstrapped: baseline schema + seed applied");
            }
            current = 1;
        }

        for (Migration migration : MIGRATIONS) {
            if (migration.version <= current) {
                continue;
            }
            applyMigration(conn, migration);
            log.info("Applied migration {} - {}", migration.version(), migration.description());
        }
    }

    private static void applyMigration(Connection conn, Migration m) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(DatabaseConfig.readResource(m.scriptPath()));
            }
            recordVersion(conn, m.version(), m.description());
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private static void ensureVersionTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version     INTEGER PRIMARY KEY,
                        description TEXT NOT NULL,
                        applied_at  TEXT NOT NULL DEFAULT (datetime('now'))
                    )""");
        }
    }

    private static int currentVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void recordVersion(Connection conn, int version, String description) throws SQLException {
        String sql = "INSERT INTO schema_version (version, description) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setString(2, description);
            ps.executeUpdate();
        }
    }
}

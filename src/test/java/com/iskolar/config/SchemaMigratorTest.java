package com.iskolar.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link SchemaMigrator} against throwaway in-memory databases:
 * a fresh database, a pre-existing baseline database (the upgrade path for
 * installs created before the migration runner existed), and idempotency.
 */
class SchemaMigratorTest {

    private static Connection newConnection() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:sqlite:file:migrationtestdb" + System.nanoTime() + "?mode=memory&cache=shared");
    }

    private static String readResource(String path) throws Exception {
        try (InputStream in = SchemaMigratorTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int maxVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static boolean tableExists(Connection conn, String name) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '" + name + "'")) {
            return rs.next();
        }
    }

    private static int count(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void freshDatabaseGetsBaselineAndAllMigrations() throws Exception {
        try (Connection conn = newConnection()) {
            SchemaMigrator.migrate(conn);

            assertEquals(2, maxVersion(conn), "fresh database should be at the latest version");
            assertTrue(tableExists(conn, "users"), "baseline users table");
            assertTrue(tableExists(conn, "sync_state"), "v2 sync_state table");
            assertTrue(tableExists(conn, "auth_cache"), "v2 auth_cache table");
            assertEquals(1, count(conn, "users"), "seed data should be present");
        }
    }

    @Test
    void legacyDatabaseIsMarkedBaselineThenUpgradedWithoutDataLoss() throws Exception {
        try (Connection conn = newConnection()) {
            // Simulate an install from before the migration runner existed:
            // the original schema.sql, no schema_version table, and user data.
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(readResource("/db/schema.sql"));
                st.executeUpdate("""
                        INSERT INTO users (email, password_hash, role, theme_pref)
                        VALUES ('legacy@example.com', 'x', 'STUDENT', 'LIGHT')""");
            }

            SchemaMigrator.migrate(conn);

            assertEquals(2, maxVersion(conn), "legacy database should upgrade to the latest version");
            assertTrue(tableExists(conn, "sync_state"));
            assertTrue(tableExists(conn, "auth_cache"));
            assertEquals(1, count(conn, "users"), "existing user data must not be reseeded or lost");
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT email FROM users")) {
                rs.next();
                assertEquals("legacy@example.com", rs.getString(1));
            }
        }
    }

    @Test
    void migrateIsIdempotent() throws Exception {
        try (Connection conn = newConnection()) {
            SchemaMigrator.migrate(conn);
            SchemaMigrator.migrate(conn);

            assertEquals(2, maxVersion(conn), "second run must not advance or duplicate versions");
            assertEquals(1, count(conn, "users"), "seed data must not be duplicated");
            assertTrue(tableExists(conn, "sync_state"));
            assertTrue(tableExists(conn, "auth_cache"));
        }
    }
}

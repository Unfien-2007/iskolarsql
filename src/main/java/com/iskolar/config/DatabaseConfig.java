package com.iskolar.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * HikariCP connection-pool factory for the SQLite database.
 *
 * <p>Lazily initialised singleton. On first access it:
 * <ol>
 *   <li>Creates the data directory (see {@link AppPaths}).</li>
 *   <li>Enables WAL journal mode on the database file.</li>
 *   <li>Applies {@code db/schema.sql} + {@code db/seed.sql} on first boot
 *       (detected by the absence of the {@code users} table).</li>
 *   <li>Builds the Hikari pool with per-connection SQLite pragmas
 *       ({@code foreign_keys}, {@code busy_timeout}).</li>
 * </ol>
 */
public final class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    private static final int MAX_POOL_SIZE = 5;
    private static final long CONNECTION_TIMEOUT_MS = 5_000;

    /** SQLite pragmas are per-connection; HikariCP runs this on every new connection. */
    private static final String CONNECTION_INIT_SQL =
            "PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 5000;";

    private static volatile HikariDataSource pool;

    private DatabaseConfig() {
    }

    /** Returns the shared pool, initialising the database on first call. */
    public static DataSource dataSource() {
        HikariDataSource ds = pool;
        if (ds == null) {
            synchronized (DatabaseConfig.class) {
                ds = pool;
                if (ds == null) {
                    ds = createPool();
                    pool = ds;
                }
            }
        }
        return ds;
    }

    /**
     * Creates a throwaway in-memory pool for tests and CLI tools.
     * The shared-cache URI makes ALL pooled connections see the same
     * in-memory database (a classic SQLite pooling pitfall).
     */
    public static HikariDataSource newInMemoryDataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("IskolarTestPool");
        // unique shared-cache name so parallel test classes never see each other's data
        cfg.setJdbcUrl("jdbc:sqlite:file:iskolartestdb" + System.nanoTime() + "?mode=memory&cache=shared");
        cfg.setMaximumPoolSize(3);
        cfg.setConnectionInitSql("PRAGMA foreign_keys = ON");
        HikariDataSource ds = new HikariDataSource(cfg);
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate(readResource("/db/schema.sql"));
        } catch (SQLException e) {
            ds.close();
            throw new IllegalStateException("Failed to initialise in-memory test database", e);
        }
        return ds;
    }

    /** Closes the shared pool (call on application shutdown). */
    public static synchronized void shutdown() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    private static HikariDataSource createPool() {
        Path dbPath = AppPaths.dbPath();
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create data directory: " + dbPath.getParent(), e);
        }
        bootstrapDatabase(dbPath);

        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("IskolarPool");
        cfg.setJdbcUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(MAX_POOL_SIZE);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        cfg.setConnectionInitSql(CONNECTION_INIT_SQL);
        return new HikariDataSource(cfg);
    }

    private static void bootstrapDatabase(Path dbPath) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement st = conn.createStatement()) {
            // journal_mode returns a result row — consume it, otherwise the open
            // cursor blocks the next statement on this connection (SQLITE_BUSY).
            try (ResultSet ignored = st.executeQuery("PRAGMA journal_mode = WAL")) {
                // no-op
            }
            if (!tableExists(conn, "users")) {
                log.info("First boot - creating schema and loading seed data at {}", dbPath);
                // executeUpdate drives xerial's multi-statement loop reliably;
                // execute() bails early on scripts producing result rows.
                st.executeUpdate(readResource("/db/schema.sql"));
                st.executeUpdate(readResource("/db/seed.sql"));
                log.info("Database bootstrapped: schema + seed applied at {}", dbPath);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to bootstrap SQLite database at " + dbPath, e);
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String readResource(String name) {
        try (InputStream in = DatabaseConfig.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read classpath resource: " + name, e);
        }
    }
}

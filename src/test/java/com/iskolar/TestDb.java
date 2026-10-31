package com.iskolar;

import com.iskolar.config.DatabaseConfig;
import com.iskolar.config.SchemaMigrator;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/** Test fixture: an in-memory SQLite loaded with schema + seed data + migrations. */
public final class TestDb {

    private TestDb() {
    }

    public static HikariDataSource newSeededDataSource() {
        HikariDataSource ds = DatabaseConfig.newInMemoryDataSource();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             InputStream in = TestDb.class.getResourceAsStream("/db/seed.sql")) {
            if (in == null) {
                throw new IllegalStateException("db/seed.sql missing from classpath");
            }
            st.executeUpdate(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            // Sample student profiles (Maria/Juan) are test-only data now that
            // production seeding ships no demo accounts.
            try (InputStream profiles = TestDb.class.getResourceAsStream("/db/test_profiles.sql")) {
                if (profiles == null) {
                    throw new IllegalStateException("db/test_profiles.sql missing from classpath");
                }
                st.executeUpdate(new String(profiles.readAllBytes(), StandardCharsets.UTF_8));
            }
            // Bring the fixture to the same schema version as a real install
            // (sync_state + auth_cache and any future migrations).
            SchemaMigrator.migrate(c);
        } catch (Exception e) {
            ds.close();
            throw new IllegalStateException("Failed to seed in-memory test database", e);
        }
        return ds;
    }
}

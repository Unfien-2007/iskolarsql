package com.iskolar;

import com.iskolar.config.DatabaseConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/** Test fixture: an in-memory SQLite loaded with schema + seed data. */
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
        } catch (Exception e) {
            ds.close();
            throw new IllegalStateException("Failed to seed in-memory test database", e);
        }
        return ds;
    }
}

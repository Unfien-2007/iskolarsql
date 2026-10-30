package com.iskolar.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DatabaseUrlEnvironmentPostProcessorTest {

    private static DatabaseUrlEnvironmentPostProcessor.Normalized normalize(String url,
            boolean explicitUser, boolean explicitPassword) {
        return DatabaseUrlEnvironmentPostProcessor.normalize(url, explicitUser, explicitPassword);
    }

    @Test
    void convertsPostgresSchemeAndExtractsUrlEncodedCredentials() {
        var n = normalize(
                "postgres://neondb_owner:s3cr%40t@ep-host.ap-southeast-1.aws.neon.tech/neondb?sslmode=require",
                false, false);
        assertEquals(
                "jdbc:postgresql://ep-host.ap-southeast-1.aws.neon.tech/neondb?sslmode=require",
                n.url());
        assertEquals("neondb_owner", n.username());
        assertEquals("s3cr@t", n.password());
    }

    @Test
    void convertsPostgresqlSchemeAndKeepsQueryParameters() {
        var n = normalize(
                "postgresql://user:pass@localhost:5432/iskolar?sslmode=require&connect_timeout=10",
                false, false);
        assertEquals("jdbc:postgresql://localhost:5432/iskolar?sslmode=require&connect_timeout=10",
                n.url());
        assertEquals("user", n.username());
        assertEquals("pass", n.password());
    }

    @Test
    void explicitVariablesWinAndUrlPassesThroughUntouched() {
        var n = normalize("postgresql://urluser:urlpass@localhost:5432/iskolar", true, true);
        assertEquals("jdbc:postgresql://urluser:urlpass@localhost:5432/iskolar", n.url());
        assertNull(n.username());
        assertNull(n.password());
    }

    @Test
    void explicitUsernameOnlyExtractsJustThePassword() {
        var n = normalize("postgresql://urluser:urlpass@localhost:5432/iskolar", true, false);
        assertEquals("jdbc:postgresql://localhost:5432/iskolar", n.url());
        assertNull(n.username());
        assertEquals("urlpass", n.password());
    }

    @Test
    void urlWithoutCredentialsIsJustConverted() {
        var n = normalize("postgresql://localhost:5432/iskolar", false, false);
        assertEquals("jdbc:postgresql://localhost:5432/iskolar", n.url());
        assertNull(n.username());
        assertNull(n.password());
    }

    @Test
    void unrelatedUrlsPassThrough() {
        assertEquals("jdbc:mysql://x/y", normalize("jdbc:mysql://x/y", false, false).url());
    }
}

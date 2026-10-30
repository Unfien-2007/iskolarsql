package com.iskolar.service;

import com.iskolar.TestDb;
import com.iskolar.dao.SyncCatalogDao;
import com.iskolar.sync.CatalogApiClient;
import com.iskolar.sync.SyncException;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end sync pipeline against a stub HTTP server (JDK HttpServer):
 * version polling, snapshot pull + local replace, up-to-date no-op, offline
 * failure, and the login endpoint's response/error mapping.
 */
class SyncServiceTest {

    private static final String SNAPSHOT = """
            {
              "version": 2,
              "degreePrograms": [
                {"programId": 1, "programCode": "BSIT", "programName": "BS Information Technology", "fieldCategory": "ICT"}
              ],
              "tags": [
                {"tagId": 1, "tagCode": "PWD", "tagName": "Person with Disability", "description": null}
              ],
              "documentTypes": [
                {"documentTypeId": 1, "docCode": "GRADES_CTC", "docName": "Grades / Certificate of True Copy", "description": null}
              ],
              "scholarships": [
                {
                  "scholarshipId": 1,
                  "createdBy": 1,
                  "title": "Stub Scholarship",
                  "providerName": "Stub Provider",
                  "providerType": "GOVERNMENT",
                  "description": "Stubbed.",
                  "tuitionBenefit": "FULL TUITION",
                  "monthlyStipend": 6000.00,
                  "returnServiceYears": 0,
                  "applicationUrl": "https://example.com",
                  "openDate": "2026-01-01",
                  "deadlineDate": "2026-12-31",
                  "isActive": true,
                  "criteria": {
                    "minGwaPercentage": 80.0,
                    "allowFailingGrade": false,
                    "maxAnnualIncome": 400000.00,
                    "allowedSchoolType": null,
                    "requiredProvince": null,
                    "requiredCity": null
                  },
                  "allowedProgramIds": [1],
                  "allowedYearLevels": [1],
                  "requiredTags": [{"tagId": 1, "mandatory": false}],
                  "requiredDocumentIds": [1]
                }
              ]
            }
            """;

    private HttpServer server;
    private int port;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/catalog/version", exchange -> respond(exchange, 200,
                "{\"version\":2}"));
        server.createContext("/api/catalog", exchange -> respond(exchange, 200, SNAPSHOT));
        server.createContext("/api/auth/login", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("\"wrong\"")) {
                respond(exchange, 401, "{\"error\":\"Invalid email or password.\"}");
            } else {
                respond(exchange, 200,
                        "{\"userId\":1,\"email\":\"admin@iskolar.app\",\"role\":\"ADMIN\",\"token\":\"jwt-123\"}");
            }
        });
        server.start();
        port = server.getAddress().getPort();
        ds = TestDb.newSeededDataSource();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        ds.close();
    }

    private SyncService service() {
        return new SyncService(new SyncCatalogDao(ds), new CatalogApiClient(baseUrl()));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void syncPullsSnapshotWhenVersionsDiffer() throws Exception {
        SyncService.SyncResult result = service().syncNow();

        assertTrue(result.success());
        assertTrue(result.changed());
        assertEquals(2, result.version());
        assertEquals(1, count("SELECT COUNT(*) FROM scholarships"));
        assertEquals("Stub Scholarship", scalar("SELECT title FROM scholarships WHERE scholarship_id = 1"));
        assertEquals("80.0",
                scalar("SELECT min_gwa_percentage FROM scholarship_criteria WHERE scholarship_id = 1"));
        assertEquals("2", scalar("SELECT value FROM sync_state WHERE key = 'catalog_version'"));
    }

    @Test
    void syncSkipsWhenVersionIsUpToDate() throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO sync_state (key, value) VALUES ('catalog_version', '2')");
        }

        SyncService.SyncResult result = service().syncNow();

        assertTrue(result.success());
        assertFalse(result.changed());
        assertEquals(2, result.version());
        assertEquals(14, count("SELECT COUNT(*) FROM scholarships")); // local catalog untouched
    }

    @Test
    void syncReportsFailureWhenServerIsUnreachable() {
        SyncService service = new SyncService(new SyncCatalogDao(ds),
                new CatalogApiClient("http://localhost:1")); // nothing listens on port 1
        SyncService.SyncResult result = service.syncNow();

        assertFalse(result.success());
        assertFalse(result.changed());
        assertTrue(result.error().contains("Could not reach"));
        assertEquals(14, count("SELECT COUNT(*) FROM scholarships")); // local catalog untouched
    }

    @Test
    void fetchVersionParsesServerResponse() {
        assertEquals(2, new CatalogApiClient(baseUrl()).fetchVersion());
    }

    @Test
    void loginReturnsTokenAndMapsBadCredentialsToUnauthorized() {
        CatalogApiClient client = new CatalogApiClient(baseUrl());
        var auth = client.login("admin@iskolar.app", "password");
        assertEquals("jwt-123", auth.token());
        assertEquals("ADMIN", auth.role());

        assertThrows(SyncException.UnauthorizedException.class,
                () -> client.login("admin@iskolar.app", "wrong"));
    }

    @Test
    void syncRetriesAfterTransientFailures() throws IOException {
        server.removeContext("/api/catalog/version");
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/api/catalog/version", exchange -> {
            if (attempts.incrementAndGet() < 3) {
                respond(exchange, 500, "{\"error\":\"temporary\"}");
            } else {
                respond(exchange, 200, "{\"version\":2}");
            }
        });

        SyncService service = new SyncService(new SyncCatalogDao(ds),
                new CatalogApiClient(baseUrl()), new long[] { 20, 20 });

        SyncService.SyncResult result = service.syncWithRetry();

        assertTrue(result.success());
        assertTrue(result.changed());
        assertEquals(2, result.version());
        assertEquals(3, attempts.get()); // two failures, then success
        assertEquals("Stub Scholarship", scalar("SELECT title FROM scholarships WHERE scholarship_id = 1"));
    }

    // ------------------------------------------------------------------

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private int count(String sql) {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String scalar(String sql) {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

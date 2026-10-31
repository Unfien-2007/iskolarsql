package com.iskolar.service;

import com.iskolar.TestDb;
import com.iskolar.dao.AuthCacheDao;
import com.iskolar.dao.UserDao;
import com.iskolar.model.Role;
import com.iskolar.model.ThemePref;
import com.iskolar.sync.CatalogApiClient;
import com.iskolar.sync.TokenStore;
import com.iskolar.util.PasswordUtils;
import com.iskolar.util.SessionManager;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Central authentication against a stub HTTP server: online-first login,
 * authoritative server rejection, offline fallback via {@code users} and
 * {@code auth_cache}, online-only registration, and online-only admin
 * sign-in.
 */
class AuthServiceTest {

    private HttpServer server;
    private int port;
    private HikariDataSource ds;
    private UserDao userDao;
    private AuthCacheDao authCacheDao;

    @BeforeEach
    void setUp() throws IOException {
        SessionManager.logout();
        TokenStore.clear();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/auth/login", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("rejected@email.com") || body.contains("\"wrong\"")) {
                respond(exchange, 401, "{\"error\":\"Invalid email or password.\"}");
            } else if (body.contains("admin@iskolar.app")) {
                respond(exchange, 200,
                        "{\"userId\":1,\"email\":\"admin@iskolar.app\",\"role\":\"ADMIN\",\"token\":\"admin-token\"}");
            } else {
                respond(exchange, 200,
                        "{\"userId\":9,\"email\":\"maria.santos@email.com\",\"role\":\"STUDENT\",\"token\":\"jwt-9\"}");
            }
        });
        server.createContext("/api/auth/register", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("dupe@email.com")) {
                respond(exchange, 409, "{\"error\":\"An account with this email already exists.\"}");
            } else {
                respond(exchange, 201,
                        "{\"userId\":42,\"email\":\"new.student@email.com\",\"role\":\"STUDENT\",\"token\":\"jwt-42\"}");
            }
        });
        server.start();
        port = server.getAddress().getPort();
        ds = TestDb.newSeededDataSource();
        userDao = new UserDao(ds);
        authCacheDao = new AuthCacheDao(ds);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        ds.close();
        SessionManager.logout();
        TokenStore.clear();
    }

    private AuthService online() {
        return new AuthService(userDao, authCacheDao, new CatalogApiClient(baseUrl()));
    }

    private AuthService offline() {
        return new AuthService(userDao, authCacheDao, new CatalogApiClient("http://localhost:1"));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void onlineLoginStartsSessionRefreshesLocalRowAndCachesCredentials() {
        AuthResult result = online().login("maria.santos@email.com", "password");

        assertTrue(result.success());
        assertTrue(SessionManager.current().isPresent());
        assertEquals("maria.santos@email.com", SessionManager.current().get().email());
        assertEquals(3, count("SELECT COUNT(*) FROM users")); // upsert, not insert
        assertEquals(1, authCacheDao.count());
        assertEquals(9L, authCacheDao.findByEmail("maria.santos@email.com").orElseThrow().serverUserId());
        assertTrue(TokenStore.token().isPresent());
        assertEquals("jwt-9", TokenStore.token().get());
    }

    @Test
    void onlineAdminLoginWorks() {
        AuthResult result = online().login("admin@iskolar.app", "password");

        assertTrue(result.success());
        assertTrue(SessionManager.isAdmin());
        assertEquals("admin-token", TokenStore.token().get());
    }

    @Test
    void serverRejectionIsAuthoritativeEvenWhenLocalCredentialsMatch() {
        userDao.insert("rejected@email.com", PasswordUtils.hash("password"), Role.STUDENT, ThemePref.LIGHT);

        AuthResult result = online().login("rejected@email.com", "password");

        assertFalse(result.success());
        assertEquals("Invalid email or password.", result.message());
        assertTrue(SessionManager.current().isEmpty());
        assertEquals(0, authCacheDao.count());
    }

    @Test
    void offlineLoginFallsBackToLocalUsers() {
        AuthResult result = offline().login("maria.santos@email.com", "password");

        assertTrue(result.success());
        assertTrue(SessionManager.current().isPresent());
        assertTrue(TokenStore.token().isEmpty());
        assertEquals(0, authCacheDao.count());
    }

    @Test
    void offlineLoginFallsBackToCachedCredentialsAndCreatesLocalRow() {
        authCacheDao.upsert(77, "cached@email.com", PasswordUtils.hash("secret123"), Role.STUDENT);
        assertEquals(3, count("SELECT COUNT(*) FROM users")); // no local row yet

        AuthResult result = offline().login("cached@email.com", "secret123");

        assertTrue(result.success());
        assertEquals("cached@email.com", SessionManager.current().get().email());
        assertEquals(4, count("SELECT COUNT(*) FROM users")); // materialized locally
        assertTrue(PasswordUtils.verify("secret123",
                userDao.findByEmail("cached@email.com").orElseThrow().passwordHash()));
    }

    @Test
    void offlineAdminLoginWorks() {
        AuthResult result = offline().login("admin@iskolar.app", "password");

        assertTrue(result.success());
        assertTrue(SessionManager.isAdmin());
    }

    @Test
    void offlineLoginForUnknownAccountReportsConnectionProblem() {
        AuthResult result = offline().login("nobody@email.com", "password123");

        assertFalse(result.success());
        assertEquals("Could not reach the server. Check your connection and try again.",
                result.message());
        assertTrue(SessionManager.current().isEmpty());
    }

    @Test
    void registerCreatesAccountOnlineAndSignsIn() {
        AuthResult result = online().register("new.student@email.com", "password123");

        assertTrue(result.success());
        assertTrue(SessionManager.current().isPresent());
        assertEquals("new.student@email.com", SessionManager.current().get().email());
        assertEquals(4, count("SELECT COUNT(*) FROM users"));
        assertEquals(1, authCacheDao.count());
        assertEquals(42L, authCacheDao.findByEmail("new.student@email.com").orElseThrow().serverUserId());
        assertEquals("jwt-42", TokenStore.token().get());
    }

    @Test
    void registerRequiresInternet() {
        AuthResult result = offline().register("new.student@email.com", "password123");

        assertFalse(result.success());
        assertEquals("Registration requires an internet connection.", result.message());
        assertEquals(3, count("SELECT COUNT(*) FROM users"));
        assertTrue(SessionManager.current().isEmpty());
    }

    @Test
    void registerSurfacesServerValidationErrors() {
        AuthResult result = online().register("dupe@email.com", "password123");

        assertFalse(result.success());
        assertEquals("An account with this email already exists.", result.message());
        assertEquals(3, count("SELECT COUNT(*) FROM users"));
    }

    @Test
    void registerValidatesLocallyBeforeTouchingTheNetwork() {
        assertFalse(online().register("not-an-email", "password123").success());
        assertFalse(online().register("ok@email.com", "short").success());
        assertEquals(3, count("SELECT COUNT(*) FROM users"));
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
}

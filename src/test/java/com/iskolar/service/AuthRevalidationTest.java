package com.iskolar.service;

import com.iskolar.TestDb;
import com.iskolar.dao.AuthCacheDao;
import com.iskolar.dao.UserDao;
import com.iskolar.model.Role;
import com.iskolar.sync.CatalogApiClient;
import com.iskolar.sync.TokenStore;
import com.iskolar.util.SessionManager;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Account revocation on reconnect: after a successful sync the app re-validates
 * the stored token against {@code GET /api/auth/me}. Disabled accounts are
 * signed out and scrubbed from the offline cache; expired tokens keep cached
 * credentials usable. Also covers the online-only password-reset calls.
 */
class AuthRevalidationTest {

    private HttpServer server;
    private int port;
    private HikariDataSource ds;
    private UserDao userDao;
    private AuthCacheDao authCacheDao;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/auth/me", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String token = auth == null ? "" : auth.replace("Bearer ", "");
            switch (token) {
                case "good-token" -> respond(exchange, 200,
                        "{\"userId\":9,\"email\":\"maria.santos@email.com\",\"role\":\"STUDENT\",\"token\":null}");
                case "disabled-token" -> respond(exchange, 401,
                        "{\"error\":\"This account has been disabled.\"}");
                default -> respond(exchange, 401,
                        "{\"error\":\"Authentication required.\"}");
            }
        });
        server.createContext("/api/auth/forgot", exchange -> respond(exchange, 200,
                "{\"message\":\"If an account with this email exists, a reset link has been sent.\"}"));
        server.createContext("/api/auth/reset", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("bad-token")) {
                respond(exchange, 400, "{\"error\":\"This reset link is invalid or has expired.\"}");
            } else {
                respond(exchange, 200, "{\"message\":\"Password updated. You can now sign in.\"}");
            }
        });
        server.start();
        port = server.getAddress().getPort();
        ds = TestDb.newSeededDataSource();
        userDao = new UserDao(ds);
        authCacheDao = new AuthCacheDao(ds);

        SessionManager.logout();
        TokenStore.clear();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        ds.close();
        SessionManager.logout();
        TokenStore.clear();
    }

    private AuthService online() {
        return new AuthService(userDao, authCacheDao,
                new CatalogApiClient("http://localhost:" + port));
    }

    private AuthService offline() {
        return new AuthService(userDao, authCacheDao,
                new CatalogApiClient("http://localhost:1"));
    }

    @Test
    void revalidateRefreshesCacheTimestamp() {
        authCacheDao.upsert(9, "maria.santos@email.com", "hash", Role.STUDENT);
        TokenStore.save("good-token", "maria.santos@email.com");

        assertEquals(AuthService.Revalidation.REFRESHED, online().revalidateOnReconnect());
        assertEquals(1, authCacheDao.count());
    }

    @Test
    void revalidateSignsOutDisabledAccountAndScrubsCache() {
        authCacheDao.upsert(9, "maria.santos@email.com", "hash", Role.STUDENT);
        TokenStore.save("disabled-token", "maria.santos@email.com");
        SessionManager.start(2, "maria.santos@email.com", Role.STUDENT);

        assertEquals(AuthService.Revalidation.SIGNED_OUT, online().revalidateOnReconnect());

        assertTrue(SessionManager.current().isEmpty());
        assertEquals(0, authCacheDao.count());
        assertTrue(TokenStore.token().isEmpty());
    }

    @Test
    void revalidateIgnoresExpiredTokenAndKeepsCache() {
        authCacheDao.upsert(9, "maria.santos@email.com", "hash", Role.STUDENT);
        TokenStore.save("expired-token", "maria.santos@email.com");
        SessionManager.start(2, "maria.santos@email.com", Role.STUDENT);

        assertEquals(AuthService.Revalidation.NONE, online().revalidateOnReconnect());

        assertTrue(SessionManager.current().isPresent());
        assertEquals(1, authCacheDao.count());
        assertEquals("expired-token", TokenStore.token().orElse(""));
    }

    @Test
    void revalidateDoesNothingWithoutToken() {
        assertEquals(AuthService.Revalidation.NONE, online().revalidateOnReconnect());
    }

    @Test
    void forgotPasswordReturnsServerMessage() {
        AuthResult result = online().forgotPassword("maria.santos@email.com");
        assertTrue(result.success());
        assertTrue(result.message().contains("reset link"));
    }

    @Test
    void forgotPasswordRequiresInternet() {
        AuthResult result = offline().forgotPassword("maria.santos@email.com");
        assertFalse(result.success());
        assertEquals("Password reset requires an internet connection.", result.message());
    }

    @Test
    void resetPasswordValidatesLocallyAndSurfacesServerErrors() {
        assertFalse(online().resetPassword("", "newpassword1").success());
        assertFalse(online().resetPassword("tok-1", "short").success());
        assertFalse(online().resetPassword("bad-token", "newpassword1").success());
        assertTrue(online().resetPassword("tok-1", "newpassword1").success());
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
}

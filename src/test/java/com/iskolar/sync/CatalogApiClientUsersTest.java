package com.iskolar.sync;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the admin user-management calls against a stub HTTP server: JSON
 * parsing, PATCH payload shape, and 401 mapping.
 */
class CatalogApiClientUsersTest {

    private static final AtomicReference<String> LAST_PATCH_BODY = new AtomicReference<>("");

    private HttpServer server;
    private CatalogApiClient client;

    @BeforeEach
    void startServer() throws IOException {
        LAST_PATCH_BODY.set("");
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/admin/users", exchange -> {
            if ("PATCH".equals(exchange.getRequestMethod())) {
                LAST_PATCH_BODY.set(new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, 200,
                        "{\"userId\":5,\"email\":\"student@iskolar.app\",\"role\":\"ADMIN\","
                                + "\"isActive\":false,\"createdAt\":\"2026-10-30T10:00:00Z\"}");
                return;
            }
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (!"Bearer good-token".equals(auth)) {
                respond(exchange, 401, "{\"error\":\"Authentication required.\"}");
                return;
            }
            respond(exchange, 200, "["
                    + "{\"userId\":1,\"email\":\"admin@iskolar.app\",\"role\":\"ADMIN\","
                    + "\"isActive\":true,\"createdAt\":\"2026-10-01T08:00:00Z\"},"
                    + "{\"userId\":2,\"email\":\"student@iskolar.app\",\"role\":\"STUDENT\","
                    + "\"isActive\":true,\"createdAt\":\"2026-10-02T09:30:00Z\"}"
                    + "]");
        });
        server.start();
        client = new CatalogApiClient("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void listUsersParsesAccounts() {
        List<CatalogDtos.AdminUserDto> users = client.listUsers("good-token");

        assertEquals(2, users.size());
        assertEquals("admin@iskolar.app", users.get(0).email());
        assertEquals("ADMIN", users.get(0).role());
        assertTrue(users.get(0).isActive());
        assertEquals("STUDENT", users.get(1).role());
        assertEquals("2026-10-02T09:30:00Z", users.get(1).createdAt());
    }

    @Test
    void updateUserSendsPatchAndParsesResponse() {
        CatalogDtos.AdminUserDto updated =
                client.updateUser("good-token", 5, "ADMIN", false);

        assertEquals(5, updated.userId());
        assertEquals("ADMIN", updated.role());
        assertFalse(updated.isActive());

        String sent = LAST_PATCH_BODY.get();
        assertTrue(sent.contains("\"role\":\"ADMIN\""), "PATCH body: " + sent);
        assertTrue(sent.contains("\"isActive\":false"), "PATCH body: " + sent);
    }

    @Test
    void listUsersRejectsBadToken() {
        SyncException.UnauthorizedException ex = assertThrows(
                SyncException.UnauthorizedException.class,
                () -> client.listUsers("bad-token"));
        assertEquals("Authentication required.", ex.getMessage());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

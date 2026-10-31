package com.iskolar.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iskolar.sync.CatalogDtos.AdminUserDto;
import com.iskolar.sync.CatalogDtos.AuthResponse;
import com.iskolar.sync.CatalogDtos.CatalogSnapshot;
import com.iskolar.sync.CatalogDtos.ScholarshipWriteDto;
import com.iskolar.sync.CatalogDtos.UserUpdateDto;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client for the central catalog server, built on the JDK
 * {@code java.net.http} client. All failures surface as {@link SyncException}
 * so callers never deal with raw IO.
 */
public final class CatalogApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl; // no trailing slash
    private final HttpClient http;

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public CatalogApiClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public CatalogApiClient(String baseUrl, HttpClient http) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = http;
    }

    public String baseUrl() {
        return baseUrl;
    }

    // ------------------------------------------------------------------
    // Catalog polling
    // ------------------------------------------------------------------

    /** {@code GET /api/catalog/version} — current server catalog version. */
    public int fetchVersion() {
        HttpResponse<String> response = send(request("/api/catalog/version"));
        expectOk(response);
        try {
            return json.readTree(response.body()).path("version").asInt(-1);
        } catch (IOException e) {
            throw new SyncException("Invalid response from the catalog server.", e);
        }
    }

    /** {@code GET /api/catalog} — full catalog snapshot. */
    public CatalogSnapshot fetchSnapshot() {
        HttpResponse<String> response = send(request("/api/catalog"));
        expectOk(response);
        try {
            return json.readValue(response.body(), CatalogSnapshot.class);
        } catch (IOException e) {
            throw new SyncException("Invalid catalog payload from the server.", e);
        }
    }

    // ------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------

    /** {@code POST /api/auth/login} — exchanges credentials for a JWT. */
    public AuthResponse login(String email, String password) {
        HttpResponse<String> response = send(request("/api/auth/login")
                .POST(jsonBody(Map.of("email", email, "password", password))));
        if (response.statusCode() == 401) {
            throw new SyncException.UnauthorizedException(errorMessage(response));
        }
        expectOk(response);
        try {
            return json.readValue(response.body(), AuthResponse.class);
        } catch (IOException e) {
            throw new SyncException("Invalid login response from the server.", e);
        }
    }

    /**
     * {@code POST /api/auth/register} — creates a STUDENT account on the
     * server and returns credentials plus a JWT (the server auto-logs new
     * accounts in).
     */
    public AuthResponse register(String email, String password) {
        HttpResponse<String> response = send(request("/api/auth/register")
                .POST(jsonBody(Map.of("email", email, "password", password))));
        expectOk(response);
        try {
            return json.readValue(response.body(), AuthResponse.class);
        } catch (IOException e) {
            throw new SyncException("Invalid registration response from the server.", e);
        }
    }

    /** {@code GET /api/auth/me} — revalidates a stored token; 401 when revoked or the account was disabled. */
    public AuthResponse me(String token) {
        HttpResponse<String> response = send(request("/api/auth/me")
                .GET()
                .header("Authorization", bearer(token)));
        if (response.statusCode() == 401) {
            throw new SyncException.UnauthorizedException(errorMessage(response));
        }
        expectOk(response);
        try {
            return json.readValue(response.body(), AuthResponse.class);
        } catch (IOException e) {
            throw new SyncException("Invalid response from the catalog server.", e);
        }
    }

    /** {@code POST /api/auth/forgot} — requests a password-reset token. */
    public String forgot(String email) {
        HttpResponse<String> response = send(request("/api/auth/forgot")
                .POST(jsonBody(Map.of("email", email))));
        expectOk(response);
        return messageOr(response, "If an account with this email exists, a reset link has been sent.");
    }

    /** {@code POST /api/auth/reset} — exchanges a reset token for a new password. */
    public String resetPassword(String token, String newPassword) {
        HttpResponse<String> response = send(request("/api/auth/reset")
                .POST(jsonBody(Map.of("token", token, "newPassword", newPassword))));
        expectOk(response);
        return messageOr(response, "Password updated.");
    }

    // ------------------------------------------------------------------
    // Admin writes (JWT required)
    // ------------------------------------------------------------------

    /** {@code POST /api/admin/scholarships} — returns the new server id. */
    public long createScholarship(String token, ScholarshipWriteDto write) {
        HttpResponse<String> response = send(request("/api/admin/scholarships")
                .POST(jsonBody(write))
                .header("Authorization", bearer(token)));
        if (response.statusCode() == 401) {
            throw new SyncException.UnauthorizedException(errorMessage(response));
        }
        expectOk(response);
        try {
            return json.readTree(response.body()).path("scholarshipId").asLong(-1);
        } catch (IOException e) {
            throw new SyncException("Invalid response from the catalog server.", e);
        }
    }

    /** {@code PUT /api/admin/scholarships/{id}} */
    public void updateScholarship(String token, long id, ScholarshipWriteDto write) {
        HttpResponse<String> response = send(request("/api/admin/scholarships/" + id)
                .PUT(jsonBody(write))
                .header("Authorization", bearer(token)));
        if (response.statusCode() == 401) {
            throw new SyncException.UnauthorizedException(errorMessage(response));
        }
        expectOk(response);
    }

    /** {@code DELETE /api/admin/scholarships/{id}} */
    public void deleteScholarship(String token, long id) {
        HttpResponse<String> response = send(request("/api/admin/scholarships/" + id)
                .DELETE()
                .header("Authorization", bearer(token)));
        if (response.statusCode() == 401) {
            throw new SyncException.UnauthorizedException(errorMessage(response));
        }
        expectOk(response);
    }

    // ------------------------------------------------------------------
    // Admin user management (JWT required)
    // ------------------------------------------------------------------

    /** {@code GET /api/admin/users} — every account, oldest first. */
    public List<AdminUserDto> listUsers(String token) {
        HttpResponse<String> response = send(request("/api/admin/users")
                .GET()
                .header("Authorization", bearer(token)));
        if (response.statusCode() == 401) {
            throw new SyncException.UnauthorizedException(errorMessage(response));
        }
        expectOk(response);
        try {
            return json.readValue(response.body(), new TypeReference<List<AdminUserDto>>() { });
        } catch (IOException e) {
            throw new SyncException("Invalid user list from the server.", e);
        }
    }

    /** {@code PATCH /api/admin/users/{id}} — role and/or active flag. */
    public AdminUserDto updateUser(String token, long userId, String role, Boolean isActive) {
        HttpResponse<String> response = send(request("/api/admin/users/" + userId)
                .method("PATCH", jsonBody(new UserUpdateDto(role, isActive)))
                .header("Authorization", bearer(token)));
        if (response.statusCode() == 401) {
            throw new SyncException.UnauthorizedException(errorMessage(response));
        }
        expectOk(response);
        try {
            return json.readValue(response.body(), AdminUserDto.class);
        } catch (IOException e) {
            throw new SyncException("Invalid response from the catalog server.", e);
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private HttpRequest.BodyPublisher jsonBody(Object payload) {
        try {
            return HttpRequest.BodyPublishers.ofString(
                    json.writeValueAsString(payload));
        } catch (IOException e) {
            throw new SyncException("Failed to encode request payload.", e);
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SyncException("Could not reach the catalog server.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SyncException("Catalog request was interrupted.", e);
        }
    }

    private static void expectOk(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SyncException(errorMessage(response));
        }
    }

    /** Server error bodies look like {@code {"error": "..."}} — surface the message. */
    private static String errorMessage(HttpResponse<String> response) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return "The catalog server returned HTTP " + response.statusCode() + ".";
        }
        try {
            return new ObjectMapper().readTree(body).path("error").asText(
                    "The catalog server returned HTTP " + response.statusCode() + ".");
        } catch (IOException e) {
            return "The catalog server returned HTTP " + response.statusCode() + ".";
        }
    }

    /** Success bodies look like {@code {"message": "..."}} — surface the message. */
    private static String messageOr(HttpResponse<String> response, String fallback) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return fallback;
        }
        try {
            String message = new ObjectMapper().readTree(body).path("message").asText("");
            return message.isBlank() ? fallback : message;
        } catch (IOException e) {
            return fallback;
        }
    }
}

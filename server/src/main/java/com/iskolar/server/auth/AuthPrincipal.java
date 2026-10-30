package com.iskolar.server.auth;

/**
 * Authenticated caller, resolved from a valid JWT by {@code JwtAuthFilter}.
 */
public record AuthPrincipal(long userId, String email, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}

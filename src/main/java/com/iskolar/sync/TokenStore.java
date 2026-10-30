package com.iskolar.sync;

import com.iskolar.util.Prefs;

import java.util.Optional;

/**
 * Preferences-backed storage for the admin's catalog-server JWT. Populated by
 * the admin write gateway after a server sign-in; cleared when the server
 * rejects the token so the next save re-prompts.
 */
public final class TokenStore {

    private TokenStore() {
    }

    public static Optional<String> token() {
        String token = Prefs.jwt();
        return token == null || token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    /** Email the stored token belongs to (empty when no token is stored). */
    public static String email() {
        return Prefs.jwtEmail();
    }

    public static void save(String token, String email) {
        Prefs.jwt(token, email);
    }

    public static void clear() {
        Prefs.clearJwt();
    }
}

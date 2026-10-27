package com.iskolar.util;

import com.iskolar.model.Role;

import java.util.Optional;

/**
 * Current-user state, persisted across launches via {@code Preferences}
 * (the session is restored lazily on first access).
 */
public final class SessionManager {

    public record Session(long userId, String email, Role role) {
    }

    private static volatile Session current;

    private SessionManager() {
    }

    /** The active session, restoring it from Preferences on first access. */
    public static Optional<Session> current() {
        if (current == null) {
            current = Prefs.session().orElse(null);
        }
        return Optional.ofNullable(current);
    }

    public static void start(long userId, String email, Role role) {
        current = new Session(userId, email, role);
        Prefs.session(userId, email, role.name());
    }

    public static void logout() {
        current = null;
        Prefs.clearSession();
    }

    public static boolean isAdmin() {
        return current().map(Session::role).map(r -> r == Role.ADMIN).orElse(false);
    }
}

package com.iskolar.util;

import com.iskolar.model.Role;
import com.iskolar.model.ThemePref;

import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * The single {@code java.util.prefs} node backing theme, session and app-level
 * flag persistence (clients typically use {@link ThemeManager} /
 * {@link SessionManager}, but app-wide UI flags live here).
 */
public final class Prefs {

    private static final Preferences NODE = Preferences.userRoot().node("com.iskolar");

    private static final String KEY_THEME = "theme";
    private static final String KEY_SESSION_USER = "session.userId";
    private static final String KEY_SESSION_EMAIL = "session.email";
    private static final String KEY_SESSION_ROLE = "session.role";

    private Prefs() {
    }

    // ---- theme -------------------------------------------------------

    static Optional<ThemePref> theme() {
        String value = NODE.get(KEY_THEME, null);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ThemePref.fromDb(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static void theme(ThemePref theme) {
        NODE.put(KEY_THEME, theme.name());
    }

    // ---- session ------------------------------------------------------

    static Optional<SessionManager.Session> session() {
        long userId = NODE.getLong(KEY_SESSION_USER, -1L);
        if (userId < 0) {
            return Optional.empty();
        }
        String email = NODE.get(KEY_SESSION_EMAIL, "");
        String role = NODE.get(KEY_SESSION_ROLE, "");
        try {
            return Optional.of(new SessionManager.Session(userId, email, Role.fromDb(role)));
        } catch (IllegalArgumentException e) {
            clearSession();
            return Optional.empty();
        }
    }

    static void session(long userId, String email, String role) {
        NODE.putLong(KEY_SESSION_USER, userId);
        NODE.put(KEY_SESSION_EMAIL, email);
        NODE.put(KEY_SESSION_ROLE, role);
    }

    static void clearSession() {
        NODE.remove(KEY_SESSION_USER);
        NODE.remove(KEY_SESSION_EMAIL);
        NODE.remove(KEY_SESSION_ROLE);
    }
}

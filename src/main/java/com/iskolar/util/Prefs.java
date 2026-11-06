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
    private static final String KEY_API_URL = "sync.apiUrl";
    private static final String KEY_JWT = "server.jwt";
    private static final String KEY_JWT_EMAIL = "server.jwt.email";
    private static final String KEY_INTRO_PLAYED = "ui.introPlayed";
    private static final String KEY_REDUCE_MOTION = "ui.reduceMotion";
    private static final String KEY_WINDOW_WIDTH = "ui.windowWidth";
    private static final String KEY_WINDOW_HEIGHT = "ui.windowHeight";
    private static final String KEY_WINDOW_MAXIMIZED = "ui.windowMaximized";

    /**
     * Default catalog-server base URL; overridable with {@code -Diskolar.apiUrl=...}.
     * Ships pointed at the deployed Render service so packaged builds work out of
     * the box; local development overrides via the system property.
     */
    private static final String DEFAULT_API_URL =
            System.getProperty("iskolar.apiUrl", "https://iskolarpath-api.onrender.com");

    private Prefs() {
    }

    // ---- sync (catalog server) ------------------------------------------

    /** Base URL of the central catalog server (no trailing slash). */
    public static String apiUrl() {
        return NODE.get(KEY_API_URL, DEFAULT_API_URL);
    }

    public static void apiUrl(String url) {
        NODE.put(KEY_API_URL, url);
    }

    public static String jwt() {
        return NODE.get(KEY_JWT, null);
    }

    public static String jwtEmail() {
        return NODE.get(KEY_JWT_EMAIL, "");
    }

    public static void jwt(String token, String email) {
        NODE.put(KEY_JWT, token);
        NODE.put(KEY_JWT_EMAIL, email);
    }

    public static void clearJwt() {
        NODE.remove(KEY_JWT);
        NODE.remove(KEY_JWT_EMAIL);
    }

    // ---- ui flags -----------------------------------------------------

    /** {@code true} once the opening splash has played on this machine. */
    public static boolean introPlayed() {
        return NODE.getBoolean(KEY_INTRO_PLAYED, false);
    }

    public static void introPlayed(boolean played) {
        NODE.putBoolean(KEY_INTRO_PLAYED, played);
    }

    /** Preference counterpart of {@code -Diskolar.reduceMotion}; {@link com.iskolar.view.Motion} reads both. */
    public static boolean reduceMotion() {
        return NODE.getBoolean(KEY_REDUCE_MOTION, false);
    }

    public static void reduceMotion(boolean reduce) {
        NODE.putBoolean(KEY_REDUCE_MOTION, reduce);
    }

    // ---- window geometry ---------------------------------------------

    /** Restored window size; the app clamps it to its own minimum and the screen. */
    public static double windowWidth() {
        return NODE.getDouble(KEY_WINDOW_WIDTH, 1280);
    }

    public static double windowHeight() {
        return NODE.getDouble(KEY_WINDOW_HEIGHT, 800);
    }

    public static void windowSize(double width, double height) {
        NODE.putDouble(KEY_WINDOW_WIDTH, width);
        NODE.putDouble(KEY_WINDOW_HEIGHT, height);
    }

    /** {@code true} when the last session ended maximized. */
    public static boolean windowMaximized() {
        return NODE.getBoolean(KEY_WINDOW_MAXIMIZED, false);
    }

    public static void windowMaximized(boolean maximized) {
        NODE.putBoolean(KEY_WINDOW_MAXIMIZED, maximized);
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

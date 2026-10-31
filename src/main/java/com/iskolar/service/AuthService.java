package com.iskolar.service;

import com.iskolar.config.DatabaseConfig;
import com.iskolar.dao.AuthCacheDao;
import com.iskolar.dao.UserDao;
import com.iskolar.model.Role;
import com.iskolar.model.User;
import com.iskolar.sync.CatalogApiClient;
import com.iskolar.sync.CatalogDtos.AuthResponse;
import com.iskolar.sync.SyncException;
import com.iskolar.sync.TokenStore;
import com.iskolar.util.PasswordUtils;
import com.iskolar.util.Prefs;
import com.iskolar.util.SessionManager;

import java.util.regex.Pattern;

/**
 * Central authentication. Sign-in is online-first: credentials are verified
 * against the catalog server when it is reachable (a definitive rejection is
 * authoritative), and fall back to the local {@code users} table and the
 * {@code auth_cache} offline credential cache when it is not. Every
 * successful online sign-in refreshes the cached hash, stores the JWT, and
 * syncs the local user row. Only registration requires the server; sign-in
 * falls back to cached credentials (admins included — catalog writes are
 * still online-only).
 *
 * <p>Network calls block; callers run this off the JavaFX thread.</p>
 */
public class AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserDao userDao;
    private final AuthCacheDao authCacheDao;
    private final CatalogApiClient apiClient;

    public AuthService(UserDao userDao) {
        this(userDao,
                new AuthCacheDao(DatabaseConfig.dataSource()),
                new CatalogApiClient(Prefs.apiUrl()));
    }

    public AuthService(UserDao userDao, AuthCacheDao authCacheDao, CatalogApiClient apiClient) {
        this.userDao = userDao;
        this.authCacheDao = authCacheDao;
        this.apiClient = apiClient;
    }

    public AuthResult login(String email, String password) {
        if (isBlank(email) || isBlank(password)) {
            return AuthResult.failure("Enter your email and password.");
        }
        String normalized = email.trim();
        try {
            AuthResponse response = apiClient.login(normalized, password);
            return completeOnlineLogin(response, normalized, password);
        } catch (SyncException.UnauthorizedException e) {
            // Server reachable and rejected the credentials: authoritative.
            return AuthResult.failure(e.getMessage() == null || e.getMessage().isBlank()
                    ? "Invalid email or password."
                    : e.getMessage());
        } catch (SyncException e) {
            return loginOffline(normalized, password);
        }
    }

    /** Students self-register on the central server (online-only). */
    public AuthResult register(String email, String password) {
        String normalized = email == null ? "" : email.trim();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            return AuthResult.failure("Enter a valid email address.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return AuthResult.failure("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        try {
            AuthResponse response = apiClient.register(normalized, password);
            return completeOnlineLogin(response, normalized, password);
        } catch (SyncException e) {
            return AuthResult.failure(isNetworkFailure(e)
                    ? "Registration requires an internet connection."
                    : e.getMessage());
        }
    }

    private AuthResult loginOffline(String email, String password) {
        User local = userDao.findByEmail(email).orElse(null);
        if (local != null) {
            return PasswordUtils.verify(password, local.passwordHash())
                    ? startLocalSession(local)
                    : AuthResult.failure("Invalid email or password.");
        }
        AuthCacheDao.CachedAuth cached = authCacheDao.findByEmail(email).orElse(null);
        if (cached != null) {
            if (!PasswordUtils.verify(password, cached.passwordHash())) {
                return AuthResult.failure("Invalid email or password.");
            }
            // Account was verified online before (possibly on another device):
            // materialize the local user row and sign in.
            User materialized = userDao.upsertFromServer(email, cached.passwordHash(), cached.role());
            return startLocalSession(materialized);
        }
        // Neither a local row nor a cached credential: we cannot tell whether
        // the account exists, so report the real problem (no connection).
        return AuthResult.failure("Could not reach the server. Check your connection and try again.");
    }

    private AuthResult startLocalSession(User user) {
        SessionManager.start(user.userId(), user.email(), user.role());
        return AuthResult.success(user);
    }

    private AuthResult completeOnlineLogin(AuthResponse response, String email, String password) {
        Role role = Role.fromDb(response.role());
        String hash = PasswordUtils.hash(password);
        User local = userDao.upsertFromServer(email, hash, role);
        authCacheDao.upsert(response.userId(), email, hash, role);
        TokenStore.save(response.token(), email);
        SessionManager.start(local.userId(), local.email(), local.role());
        return AuthResult.success(local);
    }

    /** Network-level failures carry an IO cause; server responses do not. */
    private static boolean isNetworkFailure(SyncException e) {
        return e.getCause() != null;
    }

    // ------------------------------------------------------------------
    // Revalidation (account revocation on reconnect)
    // ------------------------------------------------------------------

    /** Outcome of a background revalidation against the server. */
    public enum Revalidation {
        NONE, REFRESHED, SIGNED_OUT
    }

    /**
     * Background maintenance: when the server is reachable, re-validates the
     * stored token via {@code GET /api/auth/me}. Disabled accounts are signed
     * out locally and scrubbed from the offline credential cache (account
     * revocation on reconnect); an expired token only means "sign in again
     * online next time" and keeps the cached credentials usable offline.
     */
    public Revalidation revalidateOnReconnect() {
        String token = TokenStore.token().orElse("");
        String email = TokenStore.email();
        if (token.isBlank() || email == null || email.isBlank()) {
            return Revalidation.NONE;
        }
        try {
            apiClient.me(token);
            authCacheDao.touch(email);
            return Revalidation.REFRESHED;
        } catch (SyncException.UnauthorizedException e) {
            String message = e.getMessage();
            if (message != null && message.toLowerCase().contains("disabled")) {
                authCacheDao.remove(email);
                TokenStore.clear();
                SessionManager.current()
                        .filter(s -> s.email().equalsIgnoreCase(email))
                        .ifPresent(s -> SessionManager.logout());
                return Revalidation.SIGNED_OUT;
            }
            return Revalidation.NONE; // expired/revoked token: credentials stay cached
        } catch (SyncException e) {
            return Revalidation.NONE;
        }
    }

    // ------------------------------------------------------------------
    // Password reset (online-only)
    // ------------------------------------------------------------------

    /** Requests a password-reset token for the given email. */
    public AuthResult forgotPassword(String email) {
        String normalized = email == null ? "" : email.trim();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            return AuthResult.failure("Enter a valid email address.");
        }
        try {
            return AuthResult.successMessage(apiClient.forgot(normalized));
        } catch (SyncException e) {
            return AuthResult.failure(isNetworkFailure(e)
                    ? "Password reset requires an internet connection."
                    : e.getMessage());
        }
    }

    /** Exchanges a reset token for a new password. */
    public AuthResult resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            return AuthResult.failure("Enter the reset token from your email.");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            return AuthResult.failure("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        try {
            return AuthResult.successMessage(apiClient.resetPassword(token, newPassword));
        } catch (SyncException e) {
            return AuthResult.failure(isNetworkFailure(e)
                    ? "Password reset requires an internet connection."
                    : e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

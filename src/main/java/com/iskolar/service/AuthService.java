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
 * syncs the local user row. Registration and admin sign-in require the
 * server.
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
        if (local != null && PasswordUtils.verify(password, local.passwordHash())) {
            return startLocalSession(local);
        }
        AuthCacheDao.CachedAuth cached = authCacheDao.findByEmail(email).orElse(null);
        if (cached != null && PasswordUtils.verify(password, cached.passwordHash())) {
            // Account was verified online before (possibly on another device):
            // materialize the local user row and sign in.
            User materialized = userDao.upsertFromServer(email, cached.passwordHash(), cached.role());
            return startLocalSession(materialized);
        }
        return AuthResult.failure("Invalid email or password.");
    }

    private AuthResult startLocalSession(User user) {
        if (user.role() == Role.ADMIN) {
            return AuthResult.failure("Admin sign-in requires an internet connection.");
        }
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

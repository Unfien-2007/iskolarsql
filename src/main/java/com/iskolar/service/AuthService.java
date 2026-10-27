package com.iskolar.service;

import com.iskolar.dao.UserDao;
import com.iskolar.model.Role;
import com.iskolar.model.User;
import com.iskolar.util.PasswordUtils;
import com.iskolar.util.SessionManager;
import com.iskolar.util.ThemeManager;

import java.util.regex.Pattern;

/**
 * BCrypt login/registration with {@code STUDENT}/{@code ADMIN} roles.
 * Successful attempts start a {@code SessionManager} session.
 */
public class AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserDao userDao;

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    public AuthResult login(String email, String password) {
        if (isBlank(email) || isBlank(password)) {
            return AuthResult.failure("Enter your email and password.");
        }
        User user = userDao.findByEmail(email.trim()).orElse(null);
        if (user == null || !PasswordUtils.verify(password, user.passwordHash())) {
            return AuthResult.failure("Invalid email or password.");
        }
        SessionManager.start(user.userId(), user.email(), user.role());
        return AuthResult.success(user);
    }

    /** Students self-register; the role is pinned to STUDENT here. */
    public AuthResult register(String email, String password) {
        return register(email, password, Role.STUDENT);
    }

    public AuthResult register(String email, String password, Role role) {
        String normalized = email == null ? "" : email.trim();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            return AuthResult.failure("Enter a valid email address.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return AuthResult.failure("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (userDao.findByEmail(normalized).isPresent()) {
            return AuthResult.failure("An account with this email already exists.");
        }
        User user = userDao.insert(normalized, PasswordUtils.hash(password), role, ThemeManager.current());
        SessionManager.start(user.userId(), user.email(), user.role());
        return AuthResult.success(user);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

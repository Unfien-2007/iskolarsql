package com.iskolar.server.auth;

import com.iskolar.server.common.ApiException;
import com.iskolar.server.config.JwtService;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Registration and login against the central users table.
 *
 * <p>Passwords are bcrypt-hashed (cost 12) exactly like the desktop app, so
 * the same credentials work in both places. Public registration always
 * creates STUDENT accounts; ADMIN accounts are provisioned manually.</p>
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final JwtService jwt;

    public AuthService(UserRepository users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    public AuthDtos.AuthResponse register(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        if (users.findByEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "An account with this email already exists.");
        }
        String hash = BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
        UserRepository.UserRow row;
        try {
            row = users.insert(email, hash, "STUDENT");
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "An account with this email already exists.");
        }
        return toResponse(row);
    }

    public AuthDtos.AuthResponse login(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        UserRepository.UserRow row = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "Invalid email or password."));
        if (!row.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "This account has been disabled.");
        }
        if (!BCrypt.checkpw(rawPassword, row.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Invalid email or password.");
        }
        return toResponse(row);
    }

    /** Re-resolves the caller against the database (revokes disabled accounts). */
    public AuthDtos.UserDto me(AuthPrincipal principal) {
        UserRepository.UserRow row = users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "Account no longer exists."));
        if (!row.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "This account has been disabled.");
        }
        return new AuthDtos.UserDto(row.userId(), row.email(), row.role());
    }

    private AuthDtos.AuthResponse toResponse(UserRepository.UserRow row) {
        return new AuthDtos.AuthResponse(
                row.userId(), row.email(), row.role(),
                jwt.issue(row.userId(), row.email(), row.role()));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

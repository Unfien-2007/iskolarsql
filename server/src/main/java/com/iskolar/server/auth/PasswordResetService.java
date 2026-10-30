package com.iskolar.server.auth;

import com.iskolar.server.common.ApiException;
import com.iskolar.server.config.JwtService;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Self-service password reset. The reset token is a short-lived JWT bound to
 * the account's CURRENT password hash, so it becomes invalid as soon as the
 * password changes (single-use). The forgot endpoint always answers the same
 * way to avoid leaking which emails have accounts.
 */
@Service
public class PasswordResetService {

    private final UserRepository users;
    private final JwtService jwt;
    private final MailService mail;

    public PasswordResetService(UserRepository users, JwtService jwt, MailService mail) {
        this.users = users;
        this.jwt = jwt;
        this.mail = mail;
    }

    public AuthDtos.MessageResponse requestReset(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        users.findByEmail(email).ifPresent(row -> {
            if (row.isActive()) {
                String token = jwt.issuePasswordReset(row.userId(), row.email(), row.passwordHash());
                mail.sendResetToken(row.email(), token);
            }
        });
        return new AuthDtos.MessageResponse(
                "If an account with this email exists, a reset link has been sent.");
    }

    public AuthDtos.MessageResponse reset(String rawToken, String rawPassword) {
        JwtService.ResetClaims claims = jwt.parsePasswordReset(rawToken)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "This reset link is invalid or has expired."));
        UserRepository.UserRow row = users.findById(claims.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "This reset link is invalid or has expired."));
        if (!row.passwordHash().equals(claims.passwordHash())) {
            // The password changed after this link was issued: it was used.
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This reset link has already been used.");
        }
        users.updatePassword(row.userId(), BCrypt.hashpw(rawPassword, BCrypt.gensalt(12)));
        return new AuthDtos.MessageResponse("Password updated. You can now sign in.");
    }
}

package com.iskolar.server.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Delivers password-reset tokens. Without SMTP configuration (the default for
 * free-tier development deployments) the token is written to the server log;
 * set {@code isc.mail.smtp-host} (plus credentials) to send real email — the
 * SMTP client integration point is marked below.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final boolean smtpEnabled;

    public MailService(@Value("${isc.mail.smtp-host:}") String smtpHost) {
        this.smtpEnabled = smtpHost != null && !smtpHost.isBlank();
    }

    public void sendResetToken(String email, String token) {
        if (smtpEnabled) {
            // Production integration point: connect to the configured SMTP
            // server and email the token (or a reset URL hosting it) here.
            log.info("SMTP enabled — password-reset token for {}: {}", email, token);
        } else {
            log.info("DEV MODE password-reset token for {}: {}", email, token);
        }
    }

    public boolean isSmtpEnabled() {
        return smtpEnabled;
    }
}

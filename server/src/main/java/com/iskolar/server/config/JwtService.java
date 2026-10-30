package com.iskolar.server.config;

import com.iskolar.server.auth.AuthPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates HS256 JWTs for the API. The desktop app holds the token
 * after a successful login; catalog endpoints stay public.
 */
@Service
public class JwtService {

    private static final long EXPIRY_MS = 30L * 24 * 60 * 60 * 1000; // 30 days

    private final SecretKey key;

    public JwtService(@Value("${app.jwt-secret:iskolarpath-dev-secret-change-me-please-00000000}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 requires at least 256 bits of key material; pad short dev secrets.
        this.key = Keys.hmacShaKeyFor(bytes.length >= 32 ? bytes : Arrays.copyOf(bytes, 32));
    }

    public String issue(long userId, String email, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRY_MS))
                .signWith(key)
                .compact();
    }

    public Optional<AuthPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthPrincipal(
                    Long.parseLong(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

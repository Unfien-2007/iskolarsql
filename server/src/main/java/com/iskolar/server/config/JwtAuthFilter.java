package com.iskolar.server.config;

import com.iskolar.server.auth.AccountStatusService;
import com.iskolar.server.auth.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads {@code Authorization: Bearer <jwt>} and, when valid, exposes the
 * caller as the {@code "auth"} request attribute. The role comes from the
 * database (not the token claim) so promotions and demotions take effect
 * immediately, and {@code "authActive"} carries the live active flag so
 * protected endpoints can reject disabled accounts. Invalid or missing tokens
 * simply pass through — protected endpoints enforce their own 401.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String AUTH_ATTRIBUTE = "auth";
    public static final String ACTIVE_ATTRIBUTE = "authActive";

    private final JwtService jwt;
    private final AccountStatusService statuses;

    public JwtAuthFilter(JwtService jwt, AccountStatusService statuses) {
        this.jwt = jwt;
        this.statuses = statuses;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwt.parse(header.substring(7)).ifPresent(principal -> {
                AccountStatusService.AccountStatus status =
                        statuses.status(principal.userId(), principal.role());
                request.setAttribute(AUTH_ATTRIBUTE,
                        new AuthPrincipal(principal.userId(), principal.email(), status.role()));
                request.setAttribute(ACTIVE_ATTRIBUTE, status.active());
                if (status.active()) {
                    statuses.touch(principal.userId());
                }
            });
        }
        chain.doFilter(request, response);
    }
}

package com.iskolar.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads {@code Authorization: Bearer <jwt>} and, when valid, exposes the
 * caller as the {@code "auth"} request attribute. Invalid or missing tokens
 * simply pass through — protected endpoints enforce their own 401.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String ATTR = "auth";

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwt.parse(header.substring(7))
                    .ifPresent(principal -> request.setAttribute(ATTR, principal));
        }
        chain.doFilter(request, response);
    }
}

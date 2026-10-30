package com.iskolar.server.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request/response payloads for the auth endpoints.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Enter a valid email address.")
            String email,
            @NotBlank(message = "Password is required.")
            @Size(min = 8, max = 72, message = "Password must be 8–72 characters.")
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required.")
            String email,
            @NotBlank(message = "Password is required.")
            String password) {
    }

    public record UserDto(long userId, String email, String role) {
    }

    public record AuthResponse(long userId, String email, String role, String token) {
    }
}

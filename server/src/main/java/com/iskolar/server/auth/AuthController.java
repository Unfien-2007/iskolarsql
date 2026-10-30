package com.iskolar.server.auth;

import com.iskolar.server.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * POST /api/auth/register — create a STUDENT account
 * POST /api/auth/login — verify credentials, return JWT
 * GET  /api/auth/me     — current caller (Bearer token)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return auth.register(request.email(), request.password());
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @GetMapping("/me")
    public AuthDtos.UserDto me(HttpServletRequest request) {
        AuthPrincipal principal = (AuthPrincipal) request.getAttribute("auth");
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        return auth.me(principal);
    }
}

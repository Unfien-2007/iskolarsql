package com.iskolar.server.common;

import org.springframework.http.HttpStatus;

/**
 * Business/API error carrying an HTTP status. Thrown by services and
 * translated into a JSON {@code {"error": "…"}} body by
 * {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}

package com.iskolar.service;

import com.iskolar.model.User;

/**
 * Outcome of a login/registration attempt — message is safe to show directly
 * in the UI.
 */
public record AuthResult(boolean success, String message, User user) {

    public static AuthResult success(User user) {
        return new AuthResult(true, "Welcome, " + user.email() + "!", user);
    }

    public static AuthResult successMessage(String message) {
        return new AuthResult(true, message, null);
    }

    public static AuthResult failure(String message) {
        return new AuthResult(false, message, null);
    }
}

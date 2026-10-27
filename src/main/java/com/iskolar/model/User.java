package com.iskolar.model;

import java.time.LocalDateTime;

/** Row projection of {@code users}. */
public record User(
        long userId,
        String email,
        String passwordHash,
        Role role,
        ThemePref themePref,
        LocalDateTime createdAt) {
}

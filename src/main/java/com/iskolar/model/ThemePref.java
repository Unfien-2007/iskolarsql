package com.iskolar.model;

/** Persisted UI theme preference (users.theme_pref). */
public enum ThemePref {
    LIGHT,
    DARK;

    public static ThemePref fromDb(String value) {
        return switch (value) {
            case "DARK" -> DARK;
            case "LIGHT" -> LIGHT;
            case null -> LIGHT;
            default -> throw new IllegalArgumentException("Unknown theme preference: " + value);
        };
    }
}

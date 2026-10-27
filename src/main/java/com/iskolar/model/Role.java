package com.iskolar.model;

/** Application roles. */
public enum Role {
    STUDENT,
    ADMIN;

    public static Role fromDb(String value) {
        return switch (value) {
            case "STUDENT" -> STUDENT;
            case "ADMIN" -> ADMIN;
            case null, default -> throw new IllegalArgumentException("Unknown role: " + value);
        };
    }
}

package com.iskolar.model;

/** School sector (student_profiles.school_type). */
public enum SchoolType {
    PUBLIC("Public"),
    PRIVATE("Private");

    private final String displayName;

    SchoolType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Nullable in the schema (NULL = both sectors allowed for a scholarship). */
    public static SchoolType fromDb(String value) {
        return value == null ? null : switch (value) {
            case "PUBLIC" -> PUBLIC;
            case "PRIVATE" -> PRIVATE;
            default -> throw new IllegalArgumentException("Unknown school type: " + value);
        };
    }
}

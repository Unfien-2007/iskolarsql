package com.iskolar.model;

/** Biological sex as collected in the profile wizard. */
public enum Sex {
    MALE("Male"),
    FEMALE("Female"),
    PREFER_NOT_TO_SAY("Prefer not to say");

    private final String displayName;

    Sex(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Nullable in the schema. */
    public static Sex fromDb(String value) {
        return value == null ? null : switch (value) {
            case "MALE" -> MALE;
            case "FEMALE" -> FEMALE;
            case "PREFER_NOT_TO_SAY" -> PREFER_NOT_TO_SAY;
            default -> throw new IllegalArgumentException("Unknown sex: " + value);
        };
    }
}

package com.iskolar.model;

/** Broad CHED field categories (degree_programs.field_category). */
public enum FieldCategory {
    ICT("ICT"),
    ENGINEERING("Engineering"),
    BUSINESS("Business"),
    HEALTH("Health & Medical"),
    SCIENCE("Science"),
    EDUCATION("Education"),
    HUMANITIES("Humanities"),
    AGRICULTURE("Agriculture"),
    DESIGN("Design & Architecture"),
    SOCIAL_SCIENCE("Social Science");

    private final String displayName;

    FieldCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static FieldCategory fromDb(String value) {
        return switch (value) {
            case "ICT" -> ICT;
            case "ENGINEERING" -> ENGINEERING;
            case "BUSINESS" -> BUSINESS;
            case "HEALTH" -> HEALTH;
            case "SCIENCE" -> SCIENCE;
            case "EDUCATION" -> EDUCATION;
            case "HUMANITIES" -> HUMANITIES;
            case "AGRICULTURE" -> AGRICULTURE;
            case "DESIGN" -> DESIGN;
            case "SOCIAL_SCIENCE" -> SOCIAL_SCIENCE;
            case null, default -> throw new IllegalArgumentException("Unknown field category: " + value);
        };
    }
}

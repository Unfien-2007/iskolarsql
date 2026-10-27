package com.iskolar.model;

/** Scholarship provider categories (scholarships.provider_type). */
public enum ProviderType {
    GOVERNMENT("Government"),
    GOVERNMENT_OWNED_CORP("Government-Owned Corporation"),
    PRIVATE_CORPORATE("Private Corporation"),
    NGO_FOUNDATION("NGO / Foundation"),
    UNIVERSITY("University"),
    LOCAL_GOVERNMENT("Local Government"),
    INTERNATIONAL("International");

    private final String displayName;

    ProviderType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ProviderType fromDb(String value) {
        return switch (value) {
            case "GOVERNMENT" -> GOVERNMENT;
            case "GOVERNMENT_OWNED_CORP" -> GOVERNMENT_OWNED_CORP;
            case "PRIVATE_CORPORATE" -> PRIVATE_CORPORATE;
            case "NGO_FOUNDATION" -> NGO_FOUNDATION;
            case "UNIVERSITY" -> UNIVERSITY;
            case "LOCAL_GOVERNMENT" -> LOCAL_GOVERNMENT;
            case "INTERNATIONAL" -> INTERNATIONAL;
            case null, default -> throw new IllegalArgumentException("Unknown provider type: " + value);
        };
    }
}

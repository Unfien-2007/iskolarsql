package com.iskolar.model;

import java.util.List;

/**
 * Application tracker (Kanban) stages.
 *
 * <p>Canonical flow:
 * {@code SAVED -> PREPARING_DOCS -> APPLIED -> INTERVIEW -> AWARDED | REJECTED}
 */
public enum ApplicationStatus {
    SAVED("Saved"),
    PREPARING_DOCS("Preparing Documents"),
    APPLIED("Applied"),
    INTERVIEW("Interview"),
    AWARDED("Awarded"),
    REJECTED("Rejected");

    private final String displayName;

    ApplicationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ApplicationStatus fromDb(String value) {
        return switch (value) {
            case "SAVED" -> SAVED;
            case "PREPARING_DOCS" -> PREPARING_DOCS;
            case "APPLIED" -> APPLIED;
            case "INTERVIEW" -> INTERVIEW;
            case "AWARDED" -> AWARDED;
            case "REJECTED" -> REJECTED;
            case null, default -> throw new IllegalArgumentException("Unknown application status: " + value);
        };
    }

    /** Allowed forward transitions from this stage (Kanban buttons). */
    public List<ApplicationStatus> nextTransitions() {
        return switch (this) {
            case SAVED -> List.of(PREPARING_DOCS);
            case PREPARING_DOCS -> List.of(APPLIED);
            case APPLIED -> List.of(INTERVIEW, REJECTED);
            case INTERVIEW -> List.of(AWARDED, REJECTED);
            case AWARDED, REJECTED -> List.of();
        };
    }
}

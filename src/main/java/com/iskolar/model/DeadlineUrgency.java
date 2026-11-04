package com.iskolar.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Color-coded deadline urgency used by discovery-grid cards.
 *
 * <p>Spec: Green 30+ days · Amber &le;7 days · Red &le;48 hours.
 * Implemented as: red &le;48h, amber &le;7 days, green otherwise (the pill
 * itself always displays the exact days/hours remaining).
 */
public enum DeadlineUrgency {
    GREEN("On track"),
    AMBER("Closing soon"),
    RED("Almost closed"),
    EXPIRED("Deadline passed");

    private final String tooltip;

    DeadlineUrgency(String tooltip) {
        this.tooltip = tooltip;
    }

    public String tooltip() {
        return tooltip;
    }

    /**
     * Human label for a deadline: {@code "Due today"}, {@code "1 day left"},
     * {@code "5 days left"}, {@code "Deadline passed"} or {@code "No deadline"}.
     *
     * <p>Lives beside {@link #of(LocalDate)} so the pill's colour and its text
     * can never disagree — a deadline that falls today reads "Due today"
     * rather than "48h left".</p>
     */
    public static String text(LocalDate deadline) {
        if (deadline == null) {
            return "No deadline";
        }
        LocalDate today = LocalDate.now();
        if (deadline.isBefore(today)) {
            return "Deadline passed";
        }
        long days = ChronoUnit.DAYS.between(today, deadline);
        if (days == 0) {
            return "Due today";
        }
        if (days == 1) {
            return "1 day left";
        }
        return days + " days left";
    }

    /** Classifies a deadline relative to today (end-of-day cutoff). */
    public static DeadlineUrgency of(LocalDate deadline) {
        if (deadline == null) {
            return EXPIRED;
        }
        long hours = ChronoUnit.HOURS.between(LocalDateTime.now(), deadline.atTime(23, 59, 59));
        if (hours <= 0) {
            return EXPIRED;
        }
        if (hours <= 48) {
            return RED;
        }
        if (hours <= 7 * 24L) {
            return AMBER;
        }
        return GREEN;
    }
}

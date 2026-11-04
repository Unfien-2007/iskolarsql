package com.iskolar.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Date/time conversion helpers for SQLite's ISO-8601 TEXT storage.
 *
 * <p>SQLite {@code date('now')} yields {@code 'YYYY-MM-DD'}; SQLite
 * {@code datetime('now')} yields {@code 'YYYY-MM-DD HH:MM:SS'} (UTC).
 */
public final class Dates {

    private static final DateTimeFormatter SQLITE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Human-facing form used by cards, the details drawer and the comparison. */
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private Dates() {
    }

    /** {@code "Nov 5, 2026"} for UI surfaces; {@code null} renders as an em dash. */
    public static String displayDate(LocalDate date) {
        return date == null ? "—" : DISPLAY_DATE.format(date);
    }

    /** Parses {@code 'YYYY-MM-DD'}; {@code null}/blank returns {@code null}. */
    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    /** Parses SQLite timestamps ({@code 'YYYY-MM-DD HH:MM:SS'} or ISO {@code T} form). */
    public static LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            return v.contains("T") ? LocalDateTime.parse(v) : LocalDateTime.parse(v, SQLITE_TIMESTAMP);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Unparseable SQLite timestamp: " + value, e);
        }
    }

    public static String toDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    public static String toTimestamp(LocalDateTime timestamp) {
        return timestamp == null ? null : SQLITE_TIMESTAMP.format(timestamp);
    }

    public static String nowTimestamp() {
        return SQLITE_TIMESTAMP.format(LocalDateTime.now());
    }
}

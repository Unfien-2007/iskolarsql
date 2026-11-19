package com.iskolar.server.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Catalog housekeeping queries for time-based scholarship availability.
 *
 * <p>Deadline semantics match the desktop app: a scholarship whose deadline is
 * today is still open; a {@code null} deadline is open-ended. Only rows whose
 * deadline is strictly before the database's current date are expired.</p>
 */
@Repository
public class ScholarshipLifecycleRepository {

    private final JdbcTemplate jdbc;

    public ScholarshipLifecycleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Marks every currently active scholarship whose deadline has passed as
     * inactive. Returns the number of rows changed.
     */
    public int deactivateExpired() {
        return jdbc.update(
                "UPDATE scholarships SET is_active = FALSE "
                        + "WHERE is_active = TRUE "
                        + "AND deadline_date IS NOT NULL "
                        + "AND deadline_date < CURRENT_DATE");
    }

    /**
     * Reactivates every non-expired scholarship that is currently inactive.
     * This intentionally includes rows an admin disabled manually. Returns the
     * number of rows changed.
     */
    public int reactivateNonExpired() {
        return jdbc.update(
                "UPDATE scholarships SET is_active = TRUE "
                        + "WHERE is_active = FALSE "
                        + "AND (deadline_date IS NULL OR deadline_date >= CURRENT_DATE)");
    }
}

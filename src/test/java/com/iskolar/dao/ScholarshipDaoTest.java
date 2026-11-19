package com.iskolar.dao;

import com.iskolar.TestDb;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Housekeeping behaviour of {@link ScholarshipDao#deactivateExpired()}: an
 * active scholarship whose deadline has passed is flipped to inactive, while a
 * still-open deadline and an open-ended (null) deadline are left alone.
 */
class ScholarshipDaoTest {

    private static final long EXPIRED_ID = 901;
    private static final long FUTURE_ID = 902;
    private static final long NO_DEADLINE_ID = 903;

    private HikariDataSource ds;
    private ScholarshipDao dao;

    @BeforeEach
    void setUp() throws SQLException {
        ds = TestDb.newSeededDataSource();
        dao = new ScholarshipDao(ds);
        insertTestRows();
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    @Test
    void deactivateExpiredFlipsOnlyPastDeadlines() {
        int changed = dao.deactivateExpired();

        assertTrue(changed >= 1, "at least the expired test row is flipped");
        assertFalse(dao.findById(EXPIRED_ID).orElseThrow().isActive());
        assertTrue(dao.findById(FUTURE_ID).orElseThrow().isActive());
        assertTrue(dao.findById(NO_DEADLINE_ID).orElseThrow().isActive());
    }

    @Test
    void deactivateExpiredIsIdempotent() {
        dao.deactivateExpired();
        int secondPass = dao.deactivateExpired();

        // Nothing left to flip the second time around for the already-inactive row.
        assertFalse(dao.findById(EXPIRED_ID).orElseThrow().isActive());
        assertTrue(secondPass >= 0);
    }

    private void insertTestRows() throws SQLException {
        String past = "'" + LocalDate.now().minusDays(1) + "'";
        String future = "'" + LocalDate.now().plusDays(30) + "'";
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(insert(EXPIRED_ID, past, 1));
            st.executeUpdate(insert(FUTURE_ID, future, 1));
            st.executeUpdate(insert(NO_DEADLINE_ID, "NULL", 1));
        }
    }

    private static String insert(long id, String deadline, int active) {
        return "INSERT INTO scholarships (scholarship_id, created_by, title, provider_name,"
                + " provider_type, description, tuition_benefit, monthly_stipend,"
                + " return_service_years, application_url, open_date, deadline_date, is_active)"
                + " SELECT " + id + ", user_id, 'Expiry Test " + id + "', 'Test Provider', 'GOVERNMENT',"
                + " 'desc', 'FULL TUITION', 5000, 0, NULL, NULL, " + deadline + ", " + active
                + " FROM users ORDER BY user_id LIMIT 1";
    }
}

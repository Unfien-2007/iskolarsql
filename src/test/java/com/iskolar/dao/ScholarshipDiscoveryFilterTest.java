package com.iskolar.dao;

import com.iskolar.TestDb;
import com.iskolar.dto.DiscoveryFilter;
import com.iskolar.model.ProviderType;
import com.iskolar.model.Scholarship;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Free-text search behaviour of {@link ScholarshipDao#findActiveForDiscovery}:
 * the keyword must match title or provider case-insensitively, treat
 * {@code %} / {@code _} literally, and compose with the other filters.
 */
class ScholarshipDiscoveryFilterTest {

    private static final long DOST_ID = 901;
    private static final long UNITED_ID = 902;
    private static final long PERCENT_ID = 903;
    private static final long EXPIRED_ID = 904;

    private HikariDataSource ds;
    private ScholarshipDao dao;

    @BeforeEach
    void setUp() throws SQLException {
        ds = TestDb.newSeededDataSource();
        dao = new ScholarshipDao(ds);
        insertDiscoveryRows();
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    @Test
    void keywordMatchesTitleIgnoringCase() {
        List<Long> ids = ids(dao.findActiveForDiscovery(withKeyword("dost")));

        assertTrue(ids.contains(DOST_ID));
        assertFalse(ids.contains(UNITED_ID));
        assertFalse(ids.contains(PERCENT_ID));
    }

    @Test
    void keywordMatchesProviderName() {
        List<Long> ids = ids(dao.findActiveForDiscovery(withKeyword("united found")));

        assertTrue(ids.contains(UNITED_ID));
        assertFalse(ids.contains(DOST_ID));
    }

    @Test
    void likeWildcardsInKeywordStayLiteral() {
        List<Long> ids = ids(dao.findActiveForDiscovery(withKeyword("%")));

        // Only the row that literally contains a percent sign matches.
        assertTrue(ids.contains(PERCENT_ID));
        assertFalse(ids.contains(DOST_ID));
        assertFalse(ids.contains(UNITED_ID));
    }

    @Test
    void keywordCombinesWithProviderType() {
        DiscoveryFilter filter = new DiscoveryFilter(
                null, null, null, ProviderType.GOVERNMENT, "search");
        List<Long> ids = ids(dao.findActiveForDiscovery(filter));

        assertTrue(ids.contains(DOST_ID));
        assertTrue(ids.contains(PERCENT_ID));
        assertFalse(ids.contains(UNITED_ID)); // NGO provider, filtered out
    }

    @Test
    void blankKeywordDoesNotFilter() {
        List<Long> unfiltered = ids(dao.findActiveForDiscovery(DiscoveryFilter.empty()));
        List<Long> blank = ids(dao.findActiveForDiscovery(withKeyword("   ")));

        assertEquals(unfiltered, blank);
        assertTrue(unfiltered.contains(DOST_ID));
    }

    @Test
    void expiredScholarshipIsNeverDiscoverable() {
        List<Long> ids = ids(dao.findActiveForDiscovery(DiscoveryFilter.empty()));

        assertFalse(ids.contains(EXPIRED_ID));
    }

    private static DiscoveryFilter withKeyword(String keyword) {
        return new DiscoveryFilter(null, null, null, null, keyword);
    }

    private static List<Long> ids(List<Scholarship> scholarships) {
        return scholarships.stream().map(Scholarship::scholarshipId).toList();
    }

    /** Four rows with distinct, search-friendly titles/providers plus one expired row. */
    private void insertDiscoveryRows() throws SQLException {
        LocalDate open = LocalDate.now().plusMonths(3);
        String deadline = "'" + open + "'";
        String past = "'" + LocalDate.now().minusDays(1) + "'";
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(insert(DOST_ID, "DOST-SEI Merit Search Grant",
                    "Department of Science and Technology", "GOVERNMENT", deadline));
            st.executeUpdate(insert(UNITED_ID, "United Foundation Search Grant",
                    "United Foundation", "NGO_FOUNDATION", deadline));
            st.executeUpdate(insert(PERCENT_ID, "100% Tuition Search Grant",
                    "Percent Provider", "GOVERNMENT", deadline));
            st.executeUpdate(insert(EXPIRED_ID, "DOST-SEI Legacy Search Grant",
                    "Department of Science and Technology", "GOVERNMENT", past));
        }
    }

    private static String insert(long id, String title, String provider, String type, String deadline) {
        return "INSERT INTO scholarships (scholarship_id, created_by, title, provider_name,"
                + " provider_type, description, tuition_benefit, monthly_stipend,"
                + " return_service_years, application_url, open_date, deadline_date, is_active)"
                + " SELECT " + id + ", user_id, '" + title + "', '" + provider + "', '" + type + "',"
                + " 'desc', 'FULL TUITION', 5000, 0, NULL, NULL, " + deadline + ", 1"
                + " FROM users ORDER BY user_id LIMIT 1";
    }
}
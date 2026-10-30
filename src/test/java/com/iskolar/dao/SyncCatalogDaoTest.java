package com.iskolar.dao;

import com.iskolar.TestDb;
import com.iskolar.sync.CatalogDtos.CatalogSnapshot;
import com.iskolar.sync.CatalogDtos.DegreeProgramDto;
import com.iskolar.sync.CatalogDtos.DocumentTypeDto;
import com.iskolar.sync.CatalogDtos.ScholarshipCriteriaDto;
import com.iskolar.sync.CatalogDtos.ScholarshipDto;
import com.iskolar.sync.CatalogDtos.TagDto;
import com.iskolar.sync.CatalogDtos.TagRequirementDto;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replaces the local catalog from a server snapshot and checks that lookups
 * upsert, missing scholarships cascade away, and student data survives.
 */
class SyncCatalogDaoTest {

    private HikariDataSource ds;
    private SyncCatalogDao dao;

    @BeforeEach
    void setUp() {
        ds = TestDb.newSeededDataSource();
        dao = new SyncCatalogDao(ds);
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    @Test
    void replaceCatalogUpsertsLookupsAndRebuildsScholarships() throws SQLException {
        int beforeTags = count("SELECT COUNT(*) FROM student_tags");
        int beforeDocs = count("SELECT COUNT(*) FROM student_documents");

        // Snapshot: keep scholarships 1 + 2 (one renamed), add 99, drop the rest.
        // Lookups are copied from the seed so student FKs keep pointing at them.
        CatalogSnapshot snapshot = new CatalogSnapshot(
                7,
                readPrograms(),
                readTags(),
                readDocs(),
                List.of(
                        scholarship(1, "DOST-SEI RENAMED", 83.0, List.of(1L, 2L), List.of(1), 5),
                        scholarship(2, "CHED Merit", 90.0, List.of(), List.of(1), 4),
                        newScholarship(99, "Brand New Award", 88.0,
                                List.of(1L, 2L), List.of(1, 2),
                                List.of(new TagRequirementDto(1, false)),
                                List.of(1L), true)));

        dao.replaceCatalog(snapshot);

        assertEquals(7, dao.catalogVersion());
        assertTrue(dao.lastSyncAt().isPresent());

        // Scholarships: 1 + 2 updated, 99 inserted, everything else deleted.
        assertEquals(3, count("SELECT COUNT(*) FROM scholarships"));
        assertEquals("DOST-SEI RENAMED", scalar("SELECT title FROM scholarships WHERE scholarship_id = 1"));
        assertEquals("Brand New Award", scalar("SELECT title FROM scholarships WHERE scholarship_id = 99"));
        assertEquals("88.0",
                scalar("SELECT min_gwa_percentage FROM scholarship_criteria WHERE scholarship_id = 99"));
        assertEquals(2, count("SELECT COUNT(*) FROM scholarship_allowed_programs WHERE scholarship_id = 99"));
        assertEquals(1, count("SELECT COUNT(*) FROM scholarship_required_tags WHERE scholarship_id = 99"));
        assertEquals(1, count("SELECT COUNT(*) FROM scholarship_documents WHERE scholarship_id = 99"));
        assertEquals(0, count("SELECT COUNT(*) FROM scholarships WHERE scholarship_id IN (3,4,5,6,7,8,9,10,11,12,13,14)"));

        // Deleted scholarships cascade their applications away; survivors keep theirs.
        assertEquals(3, count("SELECT COUNT(*) FROM applications"));
        assertEquals(0, count("SELECT COUNT(*) FROM applications WHERE scholarship_id NOT IN (1,2)"));

        // Student data untouched (compare against pre-sync counts).
        assertEquals(2, count("SELECT COUNT(*) FROM student_profiles"));
        assertEquals(beforeTags, count("SELECT COUNT(*) FROM student_tags"));
        assertEquals(beforeDocs, count("SELECT COUNT(*) FROM student_documents"));
    }

    @Test
    void replaceCatalogIsIdempotent() throws SQLException {
        CatalogSnapshot snapshot = new CatalogSnapshot(
                3,
                readPrograms(),
                readTags(),
                readDocs(),
                List.of(scholarship(1, "DOST-SEI", 83.0, List.of(1L), List.of(1), 5)));

        dao.replaceCatalog(snapshot);
        dao.replaceCatalog(snapshot);

        assertEquals(1, count("SELECT COUNT(*) FROM scholarships"));
        assertEquals(1, count("SELECT COUNT(*) FROM scholarship_criteria"));
        assertEquals(3, dao.catalogVersion());
    }

    @Test
    void catalogVersionIsZeroBeforeFirstSync() {
        assertEquals(0, dao.catalogVersion());
        assertTrue(dao.lastSyncAt().isEmpty());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ScholarshipDto scholarship(long id, String title, double minGwa,
                                       List<Long> programs, List<Integer> years, long docId) {
        return new ScholarshipDto(
                id, 1, title, "Provider " + id, "GOVERNMENT", "desc", "FULL TUITION",
                new BigDecimal("5000.00"), 2, "https://example.com",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), true,
                new ScholarshipCriteriaDto(minGwa, false, new BigDecimal("300000"), null, null, null),
                programs, years, List.of(), List.of(docId));
    }

    private ScholarshipDto newScholarship(long id, String title, double minGwa,
                                          List<Long> programs, List<Integer> years,
                                          List<TagRequirementDto> tags, List<Long> docs,
                                          boolean active) {
        return new ScholarshipDto(
                id, 1, title, "Fresh Provider", "NGO_FOUNDATION", "desc", "PARTIAL",
                BigDecimal.ZERO, 0, null, null, LocalDate.of(2026, 6, 30), active,
                new ScholarshipCriteriaDto(minGwa, true, null, null, "RIZAL", null),
                programs, years, tags, docs);
    }

    private List<DegreeProgramDto> readPrograms() throws SQLException {
        List<DegreeProgramDto> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT program_id, program_code, program_name, field_category FROM degree_programs")) {
            while (rs.next()) {
                result.add(new DegreeProgramDto(rs.getLong(1), rs.getString(2),
                        rs.getString(3), rs.getString(4)));
            }
        }
        return result;
    }

    private List<TagDto> readTags() throws SQLException {
        List<TagDto> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT tag_id, tag_code, tag_name, description FROM tags")) {
            while (rs.next()) {
                result.add(new TagDto(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        }
        return result;
    }

    private List<DocumentTypeDto> readDocs() throws SQLException {
        List<DocumentTypeDto> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT document_type_id, doc_code, doc_name, description FROM document_types")) {
            while (rs.next()) {
                result.add(new DocumentTypeDto(rs.getLong(1), rs.getString(2),
                        rs.getString(3), rs.getString(4)));
            }
        }
        return result;
    }

    private int count(String sql) throws SQLException {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String scalar(String sql) throws SQLException {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}

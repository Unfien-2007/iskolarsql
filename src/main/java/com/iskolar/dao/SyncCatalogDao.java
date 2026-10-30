package com.iskolar.dao;

import com.iskolar.sync.CatalogDtos.CatalogSnapshot;
import com.iskolar.sync.CatalogDtos.DegreeProgramDto;
import com.iskolar.sync.CatalogDtos.DocumentTypeDto;
import com.iskolar.sync.CatalogDtos.ScholarshipCriteriaDto;
import com.iskolar.sync.CatalogDtos.ScholarshipDto;
import com.iskolar.sync.CatalogDtos.TagDto;
import com.iskolar.sync.CatalogDtos.TagRequirementDto;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Applies a server catalog snapshot to the local SQLite cache in ONE
 * transaction: lookups (degree programs, tags, document types) are upserted,
 * scholarships missing from the snapshot are deleted (FK cascades clean their
 * rules and applications), and {@code sync_state} records the new version.
 *
 * <p>Student-owned tables ({@code student_profiles}, {@code student_tags},
 * {@code student_documents}, {@code applications} of surviving scholarships)
 * are never touched. {@code created_by} is mapped to the local admin user so
 * the foreign key holds without importing server users.
 */
public class SyncCatalogDao {

    private static final String UPDATE_PROGRAM = """
            UPDATE degree_programs SET program_code = ?, program_name = ?, field_category = ?
            WHERE program_id = ?
            """;
    private static final String INSERT_PROGRAM = """
            INSERT INTO degree_programs (program_id, program_code, program_name, field_category)
            VALUES (?, ?, ?, ?)
            """;
    private static final String UPDATE_TAG = """
            UPDATE tags SET tag_code = ?, tag_name = ?, description = ? WHERE tag_id = ?
            """;
    private static final String INSERT_TAG = """
            INSERT INTO tags (tag_id, tag_code, tag_name, description) VALUES (?, ?, ?, ?)
            """;
    private static final String UPDATE_DOC = """
            UPDATE document_types SET doc_code = ?, doc_name = ?, description = ?
            WHERE document_type_id = ?
            """;
    private static final String INSERT_DOC = """
            INSERT INTO document_types (document_type_id, doc_code, doc_name, description)
            VALUES (?, ?, ?, ?)
            """;

    private static final String UPDATE_SCHOLARSHIP = """
            UPDATE scholarships SET created_by = ?, title = ?, provider_name = ?, provider_type = ?,
                   description = ?, tuition_benefit = ?, monthly_stipend = ?, return_service_years = ?,
                   application_url = ?, open_date = ?, deadline_date = ?, is_active = ?
            WHERE scholarship_id = ?
            """;
    private static final String INSERT_SCHOLARSHIP = """
            INSERT INTO scholarships (scholarship_id, created_by, title, provider_name, provider_type,
                                      description, tuition_benefit, monthly_stipend, return_service_years,
                                      application_url, open_date, deadline_date, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_CRITERIA = """
            UPDATE scholarship_criteria SET min_gwa_percentage = ?, allow_failing_grade = ?,
                   max_annual_income = ?, allowed_school_type = ?, required_province = ?, required_city = ?
            WHERE scholarship_id = ?
            """;
    private static final String INSERT_CRITERIA = """
            INSERT INTO scholarship_criteria (scholarship_id, min_gwa_percentage, allow_failing_grade,
                                              max_annual_income, allowed_school_type,
                                              required_province, required_city)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public SyncCatalogDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Replaces the cached catalog with {@code snapshot} in a single transaction. */
    public void replaceCatalog(CatalogSnapshot snapshot) {
        inTransaction(c -> {
            long adminId = localAdminId(c);

            Set<Long> existing = new HashSet<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT scholarship_id FROM scholarships")) {
                while (rs.next()) {
                    existing.add(rs.getLong(1));
                }
            }
            Set<Long> incoming = snapshot.scholarships().stream()
                    .map(ScholarshipDto::scholarshipId)
                    .collect(java.util.stream.Collectors.toSet());

            // Scholarships the server no longer has disappear locally too
            // (cascades remove criteria, junctions, and their applications).
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM scholarships WHERE scholarship_id = ?")) {
                for (Long id : existing) {
                    if (!incoming.contains(id)) {
                        del.setLong(1, id);
                        del.addBatch();
                    }
                }
                del.executeBatch();
            }

            for (ScholarshipDto s : snapshot.scholarships()) {
                upsertScholarship(c, s, adminId);
            }

            for (DegreeProgramDto p : snapshot.degreePrograms()) {
                upsert(c, UPDATE_PROGRAM, INSERT_PROGRAM, ps -> {
                    ps.setString(1, p.programCode());
                    ps.setString(2, p.programName());
                    ps.setString(3, p.fieldCategory());
                    ps.setLong(4, p.programId());
                });
            }
            for (TagDto t : snapshot.tags()) {
                upsert(c, UPDATE_TAG, INSERT_TAG, ps -> {
                    ps.setString(1, t.tagCode());
                    ps.setString(2, t.tagName());
                    ps.setString(3, t.description());
                    ps.setLong(4, t.tagId());
                });
            }
            for (DocumentTypeDto d : snapshot.documentTypes()) {
                upsert(c, UPDATE_DOC, INSERT_DOC, ps -> {
                    ps.setString(1, d.docCode());
                    ps.setString(2, d.docName());
                    ps.setString(3, d.description());
                    ps.setLong(4, d.documentTypeId());
                });
            }

            setState(c, "catalog_version", String.valueOf(snapshot.version()));
            setState(c, "last_sync_at", java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        });
    }

    /** Local catalog version, or 0 before the first sync. */
    public int catalogVersion() {
        return getState("catalog_version").map(s -> {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }).orElse(0);
    }

    /** Timestamp ({@code yyyy-MM-dd HH:mm:ss}) of the last successful sync. */
    public Optional<String> lastSyncAt() {
        return getState("last_sync_at");
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void upsertScholarship(Connection c, ScholarshipDto s, long adminId) throws SQLException {
        boolean exists;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM scholarships WHERE scholarship_id = ?")) {
            ps.setLong(1, s.scholarshipId());
            try (ResultSet rs = ps.executeQuery()) {
                exists = rs.next();
            }
        }

        if (exists) {
            try (PreparedStatement ps = c.prepareStatement(UPDATE_SCHOLARSHIP)) {
                ps.setLong(1, adminId);
                ps.setString(2, s.title());
                ps.setString(3, s.providerName());
                ps.setString(4, s.providerType());
                ps.setString(5, s.description());
                ps.setString(6, s.tuitionBenefit());
                ps.setBigDecimal(7, s.monthlyStipend());
                ps.setInt(8, s.returnServiceYears() == null ? 0 : s.returnServiceYears());
                ps.setString(9, s.applicationUrl());
                ps.setString(10, s.openDate() == null ? null : s.openDate().toString());
                ps.setString(11, s.deadlineDate() == null ? null : s.deadlineDate().toString());
                ps.setInt(12, s.isActive() ? 1 : 0);
                ps.setLong(13, s.scholarshipId());
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement(INSERT_SCHOLARSHIP)) {
                ps.setLong(1, s.scholarshipId());
                ps.setLong(2, adminId);
                ps.setString(3, s.title());
                ps.setString(4, s.providerName());
                ps.setString(5, s.providerType());
                ps.setString(6, s.description());
                ps.setString(7, s.tuitionBenefit());
                ps.setBigDecimal(8, s.monthlyStipend());
                ps.setInt(9, s.returnServiceYears() == null ? 0 : s.returnServiceYears());
                ps.setString(10, s.applicationUrl());
                ps.setString(11, s.openDate() == null ? null : s.openDate().toString());
                ps.setString(12, s.deadlineDate() == null ? null : s.deadlineDate().toString());
                ps.setInt(13, s.isActive() ? 1 : 0);
                ps.executeUpdate();
            }
        }

        upsertCriteria(c, s.scholarshipId(), s.criteria());
        replaceJunction(c, s.scholarshipId(), "scholarship_allowed_programs",
                "program_id", s.allowedProgramIds());
        replaceJunction(c, s.scholarshipId(), "scholarship_allowed_years",
                "year_level", s.allowedYearLevels());
        replaceTagRequirements(c, s.scholarshipId(), s.requiredTags());
        replaceJunction(c, s.scholarshipId(), "scholarship_documents",
                "document_type_id", s.requiredDocumentIds());
    }

    private void upsertCriteria(Connection c, long scholarshipId, ScholarshipCriteriaDto dto)
            throws SQLException {
        if (dto == null) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(UPDATE_CRITERIA)) {
            ps.setObject(1, dto.minGwaPercentage());
            ps.setInt(2, Boolean.TRUE.equals(dto.allowFailingGrade()) ? 1 : 0);
            ps.setBigDecimal(3, dto.maxAnnualIncome());
            ps.setString(4, dto.allowedSchoolType());
            ps.setString(5, dto.requiredProvince());
            ps.setString(6, dto.requiredCity());
            ps.setLong(7, scholarshipId);
            if (ps.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement ps = c.prepareStatement(INSERT_CRITERIA)) {
            ps.setLong(1, scholarshipId);
            ps.setObject(2, dto.minGwaPercentage());
            ps.setInt(3, Boolean.TRUE.equals(dto.allowFailingGrade()) ? 1 : 0);
            ps.setBigDecimal(4, dto.maxAnnualIncome());
            ps.setString(5, dto.allowedSchoolType());
            ps.setString(6, dto.requiredProvince());
            ps.setString(7, dto.requiredCity());
            ps.executeUpdate();
        }
    }

    private void replaceJunction(Connection c, long scholarshipId, String table, String column,
                                 java.util.List<? extends Number> values) throws SQLException {
        try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM " + table + " WHERE scholarship_id = ?")) {
            del.setLong(1, scholarshipId);
            del.executeUpdate();
        }
        if (values == null || values.isEmpty()) {
            return;
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO " + table + " (scholarship_id, " + column + ") VALUES (?, ?)")) {
            for (Number value : values) {
                ins.setLong(1, scholarshipId);
                ins.setLong(2, value.longValue());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private void replaceTagRequirements(Connection c, long scholarshipId,
                                        java.util.List<TagRequirementDto> requirements)
            throws SQLException {
        try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM scholarship_required_tags WHERE scholarship_id = ?")) {
            del.setLong(1, scholarshipId);
            del.executeUpdate();
        }
        if (requirements == null || requirements.isEmpty()) {
            return;
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO scholarship_required_tags (scholarship_id, tag_id, is_mandatory) "
                        + "VALUES (?, ?, ?)")) {
            for (TagRequirementDto req : requirements) {
                ins.setLong(1, scholarshipId);
                ins.setLong(2, req.tagId());
                ins.setInt(3, req.mandatory() ? 1 : 0);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    /** UPDATE-or-INSERT helper keyed on the id in parameter slot 4. */
    private void upsert(Connection c, String updateSql, String insertSql,
                        SqlBinder binder) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(updateSql)) {
            binder.bind(ps);
            if (ps.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement ps = c.prepareStatement(insertSql)) {
            binder.bind(ps);
            ps.executeUpdate();
        }
    }

    private long localAdminId(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT user_id FROM users WHERE role = 'ADMIN' ORDER BY user_id LIMIT 1")) {
            return rs.next() ? rs.getLong(1) : 1L;
        }
    }

    private void setState(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sync_state (key, value, updated_at) VALUES (?, ?, datetime('now'))
                ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
                """)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private Optional<String> getState(String key) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT value FROM sync_state WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read sync state: " + key, e);
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection c) throws SQLException;
    }

    private void inTransaction(SqlConsumer action) {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                action.accept(c);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw new DataAccessException("Catalog sync transaction rolled back", e);
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to manage catalog sync transaction", e);
        }
    }
}

package com.iskolar.dao;

import com.iskolar.dto.DiscoveryFilter;
import com.iskolar.dto.ScholarshipDraft;
import com.iskolar.dto.ScholarshipEligibilityBundle;
import com.iskolar.dto.TagRequirement;
import com.iskolar.model.ProviderType;
import com.iskolar.model.Scholarship;
import com.iskolar.model.ScholarshipCriteria;
import com.iskolar.model.SchoolType;
import com.iskolar.model.Tag;
import com.iskolar.util.Dates;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DAO for the scholarship aggregate: {@code scholarships} + criteria and all
 * {@code scholarship_*} junctions. The catalog is small, so bulk eligibility
 * bundles are loaded with a handful of grouped queries instead of N+1.
 */
public class ScholarshipDao {

    private static final String SCHOLARSHIP_COLUMNS = """
            scholarship_id, created_by, title, provider_name, provider_type, description,
            tuition_benefit, monthly_stipend, return_service_years, application_url,
            open_date, deadline_date, is_active
            """;

    private static final String INSERT_SCHOLARSHIP = """
            INSERT INTO scholarships (created_by, title, provider_name, provider_type, description,
                                      tuition_benefit, monthly_stipend, return_service_years,
                                      application_url, open_date, deadline_date, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_SCHOLARSHIP = """
            UPDATE scholarships SET created_by = ?, title = ?, provider_name = ?, provider_type = ?,
                   description = ?, tuition_benefit = ?, monthly_stipend = ?, return_service_years = ?,
                   application_url = ?, open_date = ?, deadline_date = ?, is_active = ?
            WHERE scholarship_id = ?
            """;

    private static final String INSERT_CRITERIA = """
            INSERT INTO scholarship_criteria (scholarship_id, min_gwa_percentage, allow_failing_grade,
                                              max_annual_income, allowed_school_type,
                                              required_province, required_city)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public ScholarshipDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /**
     * Guest discovery query: active, currently open, not-yet-expired
     * scholarships with optional program / year / provider / province filters.
     */
    public List<Scholarship> findActiveForDiscovery(DiscoveryFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.scholarship_id, s.created_by, s.title, s.provider_name, s.provider_type,
                       s.description, s.tuition_benefit, s.monthly_stipend, s.return_service_years,
                       s.application_url, s.open_date, s.deadline_date, s.is_active
                FROM scholarships s
                LEFT JOIN scholarship_criteria c ON c.scholarship_id = s.scholarship_id
                WHERE s.is_active = 1
                  AND s.deadline_date >= date('now')
                  AND (s.open_date IS NULL OR s.open_date <= date('now'))
                """);
        List<Object> params = new ArrayList<>();

        if (filter.programId() instanceof Integer programId) {
            sql.append("""
                      AND (EXISTS (SELECT 1 FROM scholarship_allowed_programs ap
                                   WHERE ap.scholarship_id = s.scholarship_id AND ap.program_id = ?)
                           OR NOT EXISTS (SELECT 1 FROM scholarship_allowed_programs ap2
                                          WHERE ap2.scholarship_id = s.scholarship_id))
                    """);
            params.add(programId);
        }
        if (filter.yearLevel() instanceof Integer yearLevel) {
            sql.append("""
                      AND (EXISTS (SELECT 1 FROM scholarship_allowed_years ay
                                   WHERE ay.scholarship_id = s.scholarship_id AND ay.year_level = ?)
                           OR NOT EXISTS (SELECT 1 FROM scholarship_allowed_years ay2
                                          WHERE ay2.scholarship_id = s.scholarship_id))
                    """);
            params.add(yearLevel);
        }
        if (filter.providerType() != null) {
            sql.append(" AND s.provider_type = ?");
            params.add(filter.providerType().name());
        }
        if (filter.province() != null && !filter.province().isBlank()) {
            sql.append(" AND (c.required_province IS NULL OR UPPER(c.required_province) = UPPER(?))");
            params.add(filter.province().trim());
        }
        sql.append(" ORDER BY s.deadline_date ASC");

        return queryScholarships(sql.toString(), params);
    }

    /** Full catalog (admin dashboard), newest-active first. */
    public List<Scholarship> findAll() {
        String sql = "SELECT " + SCHOLARSHIP_COLUMNS
                + " FROM scholarships ORDER BY is_active DESC, deadline_date ASC";
        try (Connection c = dataSource.getConnection()) {
            return findAll(c);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to open connection for catalog load", e);
        }
    }

    private List<Scholarship> findAll(Connection c) throws SQLException {
        String sql = "SELECT " + SCHOLARSHIP_COLUMNS
                + " FROM scholarships ORDER BY is_active DESC, deadline_date ASC";
        List<Scholarship> result = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(mapScholarship(rs));
            }
        }
        return result;
    }

    public Optional<Scholarship> findById(long scholarshipId) {
        String sql = "SELECT " + SCHOLARSHIP_COLUMNS + " FROM scholarships WHERE scholarship_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, scholarshipId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapScholarship(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find scholarship: " + scholarshipId, e);
        }
    }

    public Optional<ScholarshipCriteria> findCriteria(long scholarshipId) {
        String sql = "SELECT * FROM scholarship_criteria WHERE scholarship_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, scholarshipId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapCriteria(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find criteria for scholarship " + scholarshipId, e);
        }
    }

    public List<Integer> findAllowedProgramIds(long scholarshipId) {
        return queryIntList(
                "SELECT program_id FROM scholarship_allowed_programs WHERE scholarship_id = ?",
                scholarshipId);
    }

    public List<Integer> findAllowedYearLevels(long scholarshipId) {
        return queryIntList(
                "SELECT year_level FROM scholarship_allowed_years WHERE scholarship_id = ?",
                scholarshipId);
    }

    public List<Integer> findRequiredDocumentIds(long scholarshipId) {
        return queryIntList(
                "SELECT document_type_id FROM scholarship_documents WHERE scholarship_id = ?",
                scholarshipId);
    }

    public List<TagRequirement> findTagRequirements(long scholarshipId) {
        String sql = """
                SELECT rt.is_mandatory, t.tag_id, t.tag_code, t.tag_name, t.description
                FROM scholarship_required_tags rt
                JOIN tags t ON t.tag_id = rt.tag_id
                WHERE rt.scholarship_id = ?
                ORDER BY rt.is_mandatory DESC, t.tag_name
                """;
        List<TagRequirement> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, scholarshipId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapTagRequirement(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load tag requirements for scholarship " + scholarshipId, e);
        }
    }

    /** Complete eligibility read-model for one scholarship. */
    public Optional<ScholarshipEligibilityBundle> findEligibilityBundle(long scholarshipId) {
        return findById(scholarshipId).map(s -> new ScholarshipEligibilityBundle(
                s,
                findCriteria(scholarshipId).orElseGet(() -> defaultCriteria(scholarshipId)),
                findAllowedProgramIds(scholarshipId),
                findAllowedYearLevels(scholarshipId),
                findTagRequirements(scholarshipId),
                findRequiredDocumentIds(scholarshipId)));
    }

    /**
     * All eligibility bundles in a handful of grouped queries — what the
     * Phase 3 matching engine iterates when re-scoring the discovery grid.
     */
    public List<ScholarshipEligibilityBundle> findEligibilityBundles() {
        try (Connection c = dataSource.getConnection()) {
            Map<Long, Scholarship> scholarships = new HashMap<>();
            for (Scholarship s : findAll(c)) {
                scholarships.put(s.scholarshipId(), s);
            }

            Map<Long, ScholarshipCriteria> criteria = new HashMap<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM scholarship_criteria")) {
                while (rs.next()) {
                    ScholarshipCriteria sc = mapCriteria(rs);
                    criteria.put(sc.scholarshipId(), sc);
                }
            }

            Map<Long, List<Integer>> programs = groupIntPairs(c,
                    "SELECT scholarship_id, program_id FROM scholarship_allowed_programs");
            Map<Long, List<Integer>> years = groupIntPairs(c,
                    "SELECT scholarship_id, year_level FROM scholarship_allowed_years");
            Map<Long, List<Integer>> docs = groupIntPairs(c,
                    "SELECT scholarship_id, document_type_id FROM scholarship_documents");
            Map<Long, List<TagRequirement>> tags = groupTagRequirements(c);

            return scholarships.values().stream()
                    .map(s -> new ScholarshipEligibilityBundle(
                            s,
                            criteria.getOrDefault(s.scholarshipId(), defaultCriteria(s.scholarshipId())),
                            programs.getOrDefault(s.scholarshipId(), List.of()),
                            years.getOrDefault(s.scholarshipId(), List.of()),
                            tags.getOrDefault(s.scholarshipId(), List.of()),
                            docs.getOrDefault(s.scholarshipId(), List.of())))
                    .toList();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load eligibility bundles", e);
        }
    }

    // ------------------------------------------------------------------
    // Admin CRUD (atomic — draft written in one transaction)
    // ------------------------------------------------------------------

    /** Inserts a scholarship with all its rules. Returns the generated id. */
    public long create(ScholarshipDraft draft) {
        AtomicLong generatedId = new AtomicLong();
        inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(INSERT_SCHOLARSHIP, Statement.RETURN_GENERATED_KEYS)) {
                bindScholarship(ps, draft.scholarship());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("No generated key returned for scholarship");
                    }
                    generatedId.set(keys.getLong(1));
                }
            }
            long id = generatedId.get();
            insertCriteria(c, id, draft.criteria());
            replacePrograms(c, id, draft.programIds());
            replaceYears(c, id, draft.yearLevels());
            replaceTagRequirements(c, id, draft.tagRequirements());
            replaceDocuments(c, id, draft.documentIds());
        });
        return generatedId.get();
    }

    /** Replaces a scholarship and all its rules in one transaction. */
    public void update(long scholarshipId, ScholarshipDraft draft) {
        inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(UPDATE_SCHOLARSHIP)) {
                bindScholarship(ps, draft.scholarship());
                ps.setLong(13, scholarshipId);
                ps.executeUpdate();
            }
            replaceCriteria(c, scholarshipId, draft.criteria());
            replacePrograms(c, scholarshipId, draft.programIds());
            replaceYears(c, scholarshipId, draft.yearLevels());
            replaceTagRequirements(c, scholarshipId, draft.tagRequirements());
            replaceDocuments(c, scholarshipId, draft.documentIds());
        });
    }

    public void setActive(long scholarshipId, boolean active) {
        String sql = "UPDATE scholarships SET is_active = ? WHERE scholarship_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setLong(2, scholarshipId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set active flag on scholarship " + scholarshipId, e);
        }
    }

    /** Deletes the scholarship (FK cascades remove criteria, junctions, applications). */
    public void delete(long scholarshipId) {
        String sql = "DELETE FROM scholarships WHERE scholarship_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, scholarshipId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete scholarship " + scholarshipId, e);
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private List<Scholarship> queryScholarships(String sql, List<Object> params) {
        List<Scholarship> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapScholarship(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query scholarships", e);
        }
    }

    private List<Integer> queryIntList(String sql, long scholarshipId) {
        List<Integer> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, scholarshipId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getInt(1));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load junction rows for scholarship " + scholarshipId, e);
        }
    }

    private static Map<Long, List<Integer>> groupIntPairs(Connection c, String sql) throws SQLException {
        Map<Long, List<Integer>> map = new HashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getInt(2));
            }
        }
        return map;
    }

    private static Map<Long, List<TagRequirement>> groupTagRequirements(Connection c) throws SQLException {
        String sql = """
                SELECT rt.scholarship_id, rt.is_mandatory, t.tag_id, t.tag_code, t.tag_name, t.description
                FROM scholarship_required_tags rt
                JOIN tags t ON t.tag_id = rt.tag_id
                """;
        Map<Long, List<TagRequirement>> map = new HashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                map.computeIfAbsent(rs.getLong("scholarship_id"), k -> new ArrayList<>())
                        .add(mapTagRequirement(rs));
            }
        }
        return map;
    }

    private static void bindScholarship(PreparedStatement ps, Scholarship s) throws SQLException {
        ps.setLong(1, s.createdBy());
        ps.setString(2, s.title());
        ps.setString(3, s.providerName());
        ps.setString(4, s.providerType().name());
        ps.setString(5, s.description());
        ps.setString(6, s.tuitionBenefit());
        ps.setBigDecimal(7, s.monthlyStipend());
        ps.setInt(8, s.returnServiceYears());
        ps.setString(9, s.applicationUrl());
        ps.setString(10, Dates.toDate(s.openDate()));
        ps.setString(11, Dates.toDate(s.deadlineDate()));
        ps.setInt(12, s.isActive() ? 1 : 0);
    }

    private static void insertCriteria(Connection c, long scholarshipId, ScholarshipCriteria criteria)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(INSERT_CRITERIA)) {
            bindCriteria(ps, scholarshipId, criteria);
            ps.executeUpdate();
        }
    }

    private static void replaceCriteria(Connection c, long scholarshipId, ScholarshipCriteria criteria)
            throws SQLException {
        try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM scholarship_criteria WHERE scholarship_id = ?")) {
            del.setLong(1, scholarshipId);
            del.executeUpdate();
        }
        insertCriteria(c, scholarshipId, criteria);
    }

    private static void bindCriteria(PreparedStatement ps, long scholarshipId, ScholarshipCriteria criteria)
            throws SQLException {
        ps.setLong(1, scholarshipId);
        ps.setObject(2, criteria.minGwaPercentage());
        ps.setInt(3, criteria.allowFailingGrade() ? 1 : 0);
        ps.setBigDecimal(4, criteria.maxAnnualIncome());
        ps.setString(5, criteria.allowedSchoolType() == null ? null : criteria.allowedSchoolType().name());
        ps.setString(6, criteria.requiredProvince());
        ps.setString(7, criteria.requiredCity());
    }

    private static void replacePrograms(Connection c, long scholarshipId, List<Integer> programIds)
            throws SQLException {
        replaceJunction(c, "DELETE FROM scholarship_allowed_programs WHERE scholarship_id = ?",
                "INSERT INTO scholarship_allowed_programs (scholarship_id, program_id) VALUES (?, ?)",
                scholarshipId, programIds);
    }

    private static void replaceYears(Connection c, long scholarshipId, List<Integer> yearLevels)
            throws SQLException {
        replaceJunction(c, "DELETE FROM scholarship_allowed_years WHERE scholarship_id = ?",
                "INSERT INTO scholarship_allowed_years (scholarship_id, year_level) VALUES (?, ?)",
                scholarshipId, yearLevels);
    }

    private static void replaceDocuments(Connection c, long scholarshipId, List<Integer> documentIds)
            throws SQLException {
        replaceJunction(c, "DELETE FROM scholarship_documents WHERE scholarship_id = ?",
                "INSERT INTO scholarship_documents (scholarship_id, document_type_id) VALUES (?, ?)",
                scholarshipId, documentIds);
    }

    private static void replaceTagRequirements(Connection c, long scholarshipId,
                                               List<TagRequirement> requirements) throws SQLException {
        try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM scholarship_required_tags WHERE scholarship_id = ?")) {
            del.setLong(1, scholarshipId);
            del.executeUpdate();
        }
        if (requirements == null || requirements.isEmpty()) {
            return;
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO scholarship_required_tags (scholarship_id, tag_id, is_mandatory) VALUES (?, ?, ?)")) {
            for (TagRequirement req : requirements) {
                ins.setLong(1, scholarshipId);
                ins.setInt(2, req.tag().tagId());
                ins.setInt(3, req.mandatory() ? 1 : 0);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private static void replaceJunction(Connection c, String deleteSql, String insertSql,
                                        long scholarshipId, List<Integer> values) throws SQLException {
        try (PreparedStatement del = c.prepareStatement(deleteSql)) {
            del.setLong(1, scholarshipId);
            del.executeUpdate();
        }
        if (values == null || values.isEmpty()) {
            return;
        }
        try (PreparedStatement ins = c.prepareStatement(insertSql)) {
            for (Integer value : values) {
                ins.setLong(1, scholarshipId);
                ins.setInt(2, value);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private static ScholarshipCriteria defaultCriteria(long scholarshipId) {
        return new ScholarshipCriteria(0, scholarshipId, null, true, null, null, null, null);
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
                throw new DataAccessException("Transaction rolled back", e);
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to manage transaction", e);
        }
    }

    static Scholarship mapScholarship(ResultSet rs) throws SQLException {
        BigDecimal stipend = rs.getBigDecimal("monthly_stipend");
        return new Scholarship(
                rs.getLong("scholarship_id"),
                rs.getLong("created_by"),
                rs.getString("title"),
                rs.getString("provider_name"),
                ProviderType.fromDb(rs.getString("provider_type")),
                rs.getString("description"),
                rs.getString("tuition_benefit"),
                stipend == null ? BigDecimal.ZERO : stipend,
                rs.getInt("return_service_years"),
                rs.getString("application_url"),
                Dates.parseDate(rs.getString("open_date")),
                Dates.parseDate(rs.getString("deadline_date")),
                rs.getInt("is_active") != 0);
    }

    static ScholarshipCriteria mapCriteria(ResultSet rs) throws SQLException {
        return new ScholarshipCriteria(
                rs.getLong("criteria_id"),
                rs.getLong("scholarship_id"),
                (Double) rs.getObject("min_gwa_percentage"),
                rs.getInt("allow_failing_grade") != 0,
                rs.getBigDecimal("max_annual_income"),
                SchoolType.fromDb(rs.getString("allowed_school_type")),
                rs.getString("required_province"),
                rs.getString("required_city"));
    }

    static TagRequirement mapTagRequirement(ResultSet rs) throws SQLException {
        Tag tag = new Tag(
                rs.getInt("tag_id"),
                rs.getString("tag_code"),
                rs.getString("tag_name"),
                rs.getString("description"));
        return new TagRequirement(tag, rs.getInt("is_mandatory") != 0);
    }
}

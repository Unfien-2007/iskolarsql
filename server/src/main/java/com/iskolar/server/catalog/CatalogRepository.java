package com.iskolar.server.catalog;

import com.iskolar.server.catalog.CatalogDtos.DegreeProgramDto;
import com.iskolar.server.catalog.CatalogDtos.DocumentTypeDto;
import com.iskolar.server.catalog.CatalogDtos.ScholarshipCriteriaDto;
import com.iskolar.server.catalog.CatalogDtos.TagDto;
import com.iskolar.server.catalog.CatalogDtos.TagRequirementDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Reads the central catalog tables. The whole catalog is small enough that a
 * single snapshot query set is the entire Phase 2 read surface.
 */
@Repository
public class CatalogRepository {

    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int catalogVersion() {
        List<String> values = jdbc.query(
                "SELECT value FROM app_state WHERE key = 'catalog_version'",
                (rs, rowNum) -> rs.getString(1));
        return values.stream().findFirst().map(Integer::parseInt).orElse(0);
    }

    public List<DegreeProgramDto> programs() {
        return jdbc.query(
                "SELECT program_id, program_code, program_name, field_category FROM degree_programs ORDER BY program_id",
                (rs, rowNum) -> new DegreeProgramDto(
                        rs.getLong("program_id"), rs.getString("program_code"),
                        rs.getString("program_name"), rs.getString("field_category")));
    }

    public List<TagDto> tags() {
        return jdbc.query(
                "SELECT tag_id, tag_code, tag_name, description FROM tags ORDER BY tag_id",
                (rs, rowNum) -> new TagDto(
                        rs.getLong("tag_id"), rs.getString("tag_code"),
                        rs.getString("tag_name"), rs.getString("description")));
    }

    public List<DocumentTypeDto> documentTypes() {
        return jdbc.query(
                "SELECT document_type_id, doc_code, doc_name, description FROM document_types ORDER BY document_type_id",
                (rs, rowNum) -> new DocumentTypeDto(
                        rs.getLong("document_type_id"), rs.getString("doc_code"),
                        rs.getString("doc_name"), rs.getString("description")));
    }

    public record ScholarshipRow(
            long scholarshipId, long createdBy, String title, String providerName,
            String providerType, String description, String tuitionBenefit,
            java.math.BigDecimal monthlyStipend, Integer returnServiceYears,
            String applicationUrl, java.time.LocalDate openDate,
            java.time.LocalDate deadlineDate, boolean isActive) {
    }

    public List<ScholarshipRow> scholarships() {
        return jdbc.query(
                "SELECT scholarship_id, created_by, title, provider_name, provider_type, description, "
                        + "tuition_benefit, monthly_stipend, return_service_years, application_url, "
                        + "open_date, deadline_date, is_active FROM scholarships ORDER BY scholarship_id",
                (rs, rowNum) -> new ScholarshipRow(
                        rs.getLong("scholarship_id"), rs.getLong("created_by"),
                        rs.getString("title"), rs.getString("provider_name"),
                        rs.getString("provider_type"), rs.getString("description"),
                        rs.getString("tuition_benefit"), rs.getBigDecimal("monthly_stipend"),
                        (Integer) rs.getObject("return_service_years"),
                        rs.getString("application_url"),
                        rs.getObject("open_date", java.time.LocalDate.class),
                        rs.getObject("deadline_date", java.time.LocalDate.class),
                        rs.getBoolean("is_active")));
    }

    public Optional<ScholarshipCriteriaDto> criteria(long scholarshipId) {
        List<ScholarshipCriteriaDto> rows = jdbc.query(
                "SELECT min_gwa_percentage, allow_failing_grade, max_annual_income, "
                        + "allowed_school_type, required_province, required_city "
                        + "FROM scholarship_criteria WHERE scholarship_id = ?",
                (rs, rowNum) -> {
                    java.math.BigDecimal gwa = rs.getBigDecimal("min_gwa_percentage");
                    return new ScholarshipCriteriaDto(
                            gwa == null ? null : gwa.doubleValue(),
                            rs.getBoolean("allow_failing_grade"),
                            rs.getBigDecimal("max_annual_income"),
                            rs.getString("allowed_school_type"),
                            rs.getString("required_province"),
                            rs.getString("required_city"));
                },
                scholarshipId);
        return rows.stream().findFirst();
    }

    public List<Long> allowedProgramIds(long scholarshipId) {
        return jdbc.queryForList(
                "SELECT program_id FROM scholarship_allowed_programs WHERE scholarship_id = ? ORDER BY program_id",
                Long.class, scholarshipId);
    }

    public List<Integer> allowedYearLevels(long scholarshipId) {
        return jdbc.queryForList(
                "SELECT year_level FROM scholarship_allowed_years WHERE scholarship_id = ? ORDER BY year_level",
                Integer.class, scholarshipId);
    }

    public List<TagRequirementDto> requiredTags(long scholarshipId) {
        return jdbc.query(
                "SELECT tag_id, is_mandatory FROM scholarship_required_tags WHERE scholarship_id = ? ORDER BY tag_id",
                (rs, rowNum) -> new TagRequirementDto(
                        rs.getLong("tag_id"), rs.getBoolean("is_mandatory")),
                scholarshipId);
    }

    public List<Long> requiredDocumentIds(long scholarshipId) {
        return jdbc.queryForList(
                "SELECT document_type_id FROM scholarship_documents WHERE scholarship_id = ? ORDER BY document_type_id",
                Long.class, scholarshipId);
    }
}

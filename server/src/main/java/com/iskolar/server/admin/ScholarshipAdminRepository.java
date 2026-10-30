package com.iskolar.server.admin;

import com.iskolar.server.admin.AdminDtos.ScholarshipCriteriaWrite;
import com.iskolar.server.admin.AdminDtos.ScholarshipWrite;
import com.iskolar.server.catalog.CatalogDtos.TagRequirementDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Writes the catalog. Every mutating operation is expected to run inside a
 * transaction provided by the controller, and the caller bumps
 * {@code catalog_version} afterwards.
 */
@Repository
public class ScholarshipAdminRepository {

    private final JdbcTemplate jdbc;

    public ScholarshipAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(ScholarshipWrite w, long createdBy) {
        Long id = jdbc.queryForObject(
                "INSERT INTO scholarships (created_by, title, provider_name, provider_type, description, "
                        + "tuition_benefit, monthly_stipend, return_service_years, application_url, "
                        + "open_date, deadline_date, is_active) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) RETURNING scholarship_id",
                Long.class,
                createdBy, w.title(), w.providerName(), w.providerType(), w.description(),
                w.tuitionBenefit(), w.monthlyStipend(), w.returnServiceYears(),
                w.applicationUrl(), w.openDate(), w.deadlineDate(),
                w.isActive() == null || w.isActive());
        replaceDetails(id, w);
        return id;
    }

    public void update(long id, ScholarshipWrite w) {
        jdbc.update(
                "UPDATE scholarships SET title=?, provider_name=?, provider_type=?, description=?, "
                        + "tuition_benefit=?, monthly_stipend=?, return_service_years=?, application_url=?, "
                        + "open_date=?, deadline_date=?, is_active=? WHERE scholarship_id=?",
                w.title(), w.providerName(), w.providerType(), w.description(),
                w.tuitionBenefit(), w.monthlyStipend(), w.returnServiceYears(),
                w.applicationUrl(), w.openDate(), w.deadlineDate(),
                w.isActive() == null || w.isActive(), id);
        replaceDetails(id, w);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM scholarships WHERE scholarship_id = ?", id);
    }

    public void bumpCatalogVersion() {
        jdbc.update(
                "UPDATE app_state SET value = (value::int + 1)::text, updated_at = now() "
                        + "WHERE key = 'catalog_version'");
    }

    private void replaceDetails(long id, ScholarshipWrite w) {
        ScholarshipCriteriaWrite c = w.criteria();
        jdbc.update(
                """
                        INSERT INTO scholarship_criteria (scholarship_id, min_gwa_percentage,
                            allow_failing_grade, max_annual_income, allowed_school_type,
                            required_province, required_city)
                        VALUES (?,?,?,?,?,?,?)
                        ON CONFLICT (scholarship_id) DO UPDATE SET
                            min_gwa_percentage   = EXCLUDED.min_gwa_percentage,
                            allow_failing_grade  = EXCLUDED.allow_failing_grade,
                            max_annual_income    = EXCLUDED.max_annual_income,
                            allowed_school_type  = EXCLUDED.allowed_school_type,
                            required_province    = EXCLUDED.required_province,
                            required_city        = EXCLUDED.required_city
                        """,
                id,
                c == null ? null : c.minGwaPercentage(),
                c == null || c.allowFailingGrade() == null ? false : c.allowFailingGrade(),
                c == null ? null : c.maxAnnualIncome(),
                c == null ? null : c.allowedSchoolType(),
                c == null ? null : c.requiredProvince(),
                c == null ? null : c.requiredCity());

        jdbc.update("DELETE FROM scholarship_allowed_programs WHERE scholarship_id = ?", id);
        if (w.programIds() != null) {
            jdbc.batchUpdate(
                    "INSERT INTO scholarship_allowed_programs (scholarship_id, program_id) VALUES (?,?)",
                    w.programIds(), 100,
                    (ps, p) -> {
                        ps.setLong(1, id);
                        ps.setLong(2, p);
                    });
        }

        jdbc.update("DELETE FROM scholarship_allowed_years WHERE scholarship_id = ?", id);
        if (w.yearLevels() != null) {
            jdbc.batchUpdate(
                    "INSERT INTO scholarship_allowed_years (scholarship_id, year_level) VALUES (?,?)",
                    w.yearLevels(), 100,
                    (ps, y) -> {
                        ps.setLong(1, id);
                        ps.setInt(2, y);
                    });
        }

        jdbc.update("DELETE FROM scholarship_required_tags WHERE scholarship_id = ?", id);
        if (w.requiredTags() != null) {
            jdbc.batchUpdate(
                    "INSERT INTO scholarship_required_tags (scholarship_id, tag_id, is_mandatory) VALUES (?,?,?)",
                    w.requiredTags(), 100,
                    (ps, t) -> {
                        ps.setLong(1, id);
                        ps.setLong(2, t.tagId());
                        ps.setBoolean(3, t.mandatory());
                    });
        }

        jdbc.update("DELETE FROM scholarship_documents WHERE scholarship_id = ?", id);
        if (w.documentIds() != null) {
            jdbc.batchUpdate(
                    "INSERT INTO scholarship_documents (scholarship_id, document_type_id) VALUES (?,?)",
                    w.documentIds(), 100,
                    (ps, d) -> {
                        ps.setLong(1, id);
                        ps.setLong(2, d);
                    });
        }
    }
}

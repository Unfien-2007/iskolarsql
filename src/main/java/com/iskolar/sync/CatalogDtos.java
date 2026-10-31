package com.iskolar.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * JSON shapes exchanged with the central catalog server. Field names mirror
 * the server's DTOs (see {@code server/…/catalog/CatalogDtos.java}) so Jackson
 * maps them mechanically. Unknown fields are ignored for forward-compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CatalogSnapshot(
            int version,
            List<DegreeProgramDto> degreePrograms,
            List<TagDto> tags,
            List<DocumentTypeDto> documentTypes,
            List<ScholarshipDto> scholarships) {
    }

    public record DegreeProgramDto(
            long programId, String programCode, String programName, String fieldCategory) {
    }

    public record TagDto(long tagId, String tagCode, String tagName, String description) {
    }

    public record DocumentTypeDto(
            long documentTypeId, String docCode, String docName, String description) {
    }

    public record TagRequirementDto(long tagId, boolean mandatory) {
    }

    public record ScholarshipCriteriaDto(
            Double minGwaPercentage,
            Boolean allowFailingGrade,
            BigDecimal maxAnnualIncome,
            String allowedSchoolType,
            String requiredProvince,
            String requiredCity) {
    }

    public record ScholarshipDto(
            long scholarshipId,
            long createdBy,
            String title,
            String providerName,
            String providerType,
            String description,
            String tuitionBenefit,
            BigDecimal monthlyStipend,
            Integer returnServiceYears,
            String applicationUrl,
            LocalDate openDate,
            LocalDate deadlineDate,
            boolean isActive,
            ScholarshipCriteriaDto criteria,
            List<Long> allowedProgramIds,
            List<Integer> allowedYearLevels,
            List<TagRequirementDto> requiredTags,
            List<Long> requiredDocumentIds) {
    }

    /** {@code GET /api/catalog/version} response. */
    public record VersionResponse(int version) {
    }

    /** {@code POST /api/auth/login} response. */
    public record AuthResponse(long userId, String email, String role, String token) {
    }

    // ------------------------------------------------------------------
    // Admin write payloads (mirror server AdminDtos)
    // ------------------------------------------------------------------

    public record CriteriaWriteDto(
            Double minGwaPercentage,
            Boolean allowFailingGrade,
            BigDecimal maxAnnualIncome,
            String allowedSchoolType,
            String requiredProvince,
            String requiredCity) {
    }

    public record ScholarshipWriteDto(
            String title,
            String providerName,
            String providerType,
            String description,
            String tuitionBenefit,
            BigDecimal monthlyStipend,
            Integer returnServiceYears,
            String applicationUrl,
            LocalDate openDate,
            LocalDate deadlineDate,
            Boolean isActive,
            CriteriaWriteDto criteria,
            List<Long> programIds,
            List<Integer> yearLevels,
            List<TagRequirementDto> requiredTags,
            List<Long> documentIds) {
    }

    // ------------------------------------------------------------------
    // Admin user payloads (mirror server AdminDtos)
    // ------------------------------------------------------------------

    /** {@code GET /api/admin/users} row. */
    public record AdminUserDto(long userId, String email, String role,
                               boolean isActive, String createdAt) {
    }

    /** {@code PATCH /api/admin/users/{id}} body — either field may be omitted. */
    public record UserUpdateDto(String role, Boolean isActive) {
    }
}

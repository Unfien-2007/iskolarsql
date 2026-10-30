package com.iskolar.server.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * JSON shapes for the catalog snapshot the desktop app downloads and stores
 * in its local SQLite cache. Field names mirror the client's model so the
 * Phase 3 sync mapper is mechanical.
 */
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
}

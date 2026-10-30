package com.iskolar.server.admin;

import com.iskolar.server.catalog.CatalogDtos.TagRequirementDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payloads for admin scholarship writes. Mirrors the desktop app's
 * {@code ScholarshipDraft} so the two can eventually share JSON directly.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record ScholarshipCriteriaWrite(
            Double minGwaPercentage,
            Boolean allowFailingGrade,
            BigDecimal maxAnnualIncome,
            String allowedSchoolType,
            String requiredProvince,
            String requiredCity) {
    }

    public record ScholarshipWrite(
            @NotBlank String title,
            @NotBlank String providerName,
            @NotBlank String providerType,
            String description,
            String tuitionBenefit,
            BigDecimal monthlyStipend,
            Integer returnServiceYears,
            String applicationUrl,
            LocalDate openDate,
            @NotNull LocalDate deadlineDate,
            Boolean isActive,
            ScholarshipCriteriaWrite criteria,
            List<Long> programIds,
            List<Integer> yearLevels,
            List<TagRequirementDto> requiredTags,
            List<Long> documentIds) {
    }
}

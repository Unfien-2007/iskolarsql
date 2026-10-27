package com.iskolar.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Row projection of {@code scholarships}. */
public record Scholarship(
        long scholarshipId,
        long createdBy,
        String title,
        String providerName,
        ProviderType providerType,
        String description,
        String tuitionBenefit,
        BigDecimal monthlyStipend,
        int returnServiceYears,
        String applicationUrl,
        LocalDate openDate,
        LocalDate deadlineDate,
        boolean isActive) {

    /** Human-friendly stipend, e.g. {@code "PHP 8,000 / month"} or {@code "None"}. */
    public String stipendLabel() {
        return monthlyStipend != null && monthlyStipend.signum() > 0
                ? String.format("PHP %,.0f / month", monthlyStipend)
                : "None";
    }
}

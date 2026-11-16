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
        Double acceptanceRate,
        Integer awardsAvailable,
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

    /** Estimated acceptance rate without trailing zeros, e.g. {@code "8"} or {@code "3.5"}. */
    public String acceptancePercent() {
        if (acceptanceRate == null) {
            return null;
        }
        return BigDecimal.valueOf(acceptanceRate).stripTrailingZeros().toPlainString();
    }

    /** Compact card label, e.g. {@code "~8% acceptance"}; {@code null} when unknown. */
    public String acceptanceLabel() {
        String percent = acceptancePercent();
        return percent == null ? null : "~" + percent + "% acceptance";
    }
}

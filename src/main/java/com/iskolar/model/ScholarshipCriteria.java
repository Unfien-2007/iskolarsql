package com.iskolar.model;

import java.math.BigDecimal;

/**
 * Row projection of {@code scholarship_criteria} (1:1 with a scholarship).
 *
 * <p>Null semantics (see schema.sql):
 * <ul>
 *   <li>{@code minGwaPercentage == null} — no academic minimum</li>
 *   <li>{@code maxAnnualIncome == null} — merit-only, no income cap</li>
 *   <li>{@code allowedSchoolType == null} — PUBLIC and PRIVATE both allowed</li>
 *   <li>{@code requiredProvince == null} — no hard residency gate</li>
 * </ul>
 */
public record ScholarshipCriteria(
        long criteriaId,
        long scholarshipId,
        Double minGwaPercentage,
        boolean allowFailingGrade,
        BigDecimal maxAnnualIncome,
        SchoolType allowedSchoolType,
        String requiredProvince,
        String requiredCity) {
}

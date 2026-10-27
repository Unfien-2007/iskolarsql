package com.iskolar.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Row projection of {@code student_profiles} — the 4-step profile wizard's
 * persisted state (Steps 1-3 live here; Step 4 documents live in
 * {@code student_documents}).
 *
 * <p>{@code studentId == 0} marks an unsaved/new profile.
 */
public record StudentProfile(
        int studentId,
        long userId,
        String firstName,
        String lastName,
        LocalDate birthdate,
        Sex sex,
        String province,
        String cityMunicipality,
        String schoolName,
        SchoolType schoolType,
        Integer programId,
        Integer yearLevel,
        double gwaPercentage,
        boolean hasFailingGrade,
        BigDecimal annualFamilyIncome,
        Integer householdSize) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}

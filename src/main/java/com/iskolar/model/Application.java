package com.iskolar.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Row projection of {@code applications} — the student's tracker card for a
 * scholarship ({@code UNIQUE(student_id, scholarship_id)}).
 */
public record Application(
        long applicationId,
        int studentId,
        long scholarshipId,
        ApplicationStatus status,
        LocalDate appliedDate,
        LocalDateTime updatedAt,
        String notes) {
}

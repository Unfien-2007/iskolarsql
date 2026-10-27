package com.iskolar.model;

import java.time.LocalDateTime;

/**
 * Row projection of {@code student_documents} — one row per document type in
 * the student's Readiness Vault.
 */
public record StudentDocument(
        int studentId,
        int documentTypeId,
        boolean isReady,
        LocalDateTime updatedAt) {
}

package com.iskolar.model;

/** Row projection of {@code document_types}. */
public record DocumentType(
        int documentTypeId,
        String docCode,
        String docName,
        String description) {
}

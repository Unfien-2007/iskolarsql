package com.iskolar.model;

/** Row projection of {@code tags}. */
public record Tag(
        int tagId,
        String tagCode,
        String tagName,
        String description) {
}

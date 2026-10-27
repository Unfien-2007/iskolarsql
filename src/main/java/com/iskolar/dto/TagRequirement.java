package com.iskolar.dto;

import com.iskolar.model.Tag;

/**
 * A scholarship's tag expectation.
 *
 * @param tag       the expected demographic/attribute tag
 * @param mandatory {@code true} = Stage 1 hard disqualifier when the student
 *                  lacks it; {@code false} = preferred tag earning
 *                  proportional Stage 2 bonus points only
 */
public record TagRequirement(Tag tag, boolean mandatory) {
}

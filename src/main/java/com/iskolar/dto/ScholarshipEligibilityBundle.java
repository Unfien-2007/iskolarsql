package com.iskolar.dto;

import com.iskolar.model.Scholarship;
import com.iskolar.model.ScholarshipCriteria;

import java.util.List;

/**
 * Complete eligibility picture for one scholarship — everything the two-stage
 * matching engine needs in a single read model.
 *
 * <p>Empty {@code allowedProgramIds} / {@code allowedYearLevels} mean
 * "open to all" (see schema conventions).
 */
public record ScholarshipEligibilityBundle(
        Scholarship scholarship,
        ScholarshipCriteria criteria,
        List<Integer> allowedProgramIds,
        List<Integer> allowedYearLevels,
        List<TagRequirement> tagRequirements,
        List<Integer> requiredDocumentIds) {
}

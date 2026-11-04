package com.iskolar.dto;

import com.iskolar.model.Scholarship;
import com.iskolar.model.ScholarshipCriteria;

/**
 * One column of the side-by-side comparison modal.
 *
 * @param match this student's match verdict for the scholarship, or
 *              {@code null} for guests / profile-less accounts
 */
public record ComparisonRow(
        Scholarship scholarship,
        ScholarshipCriteria criteria,
        MatchResult match) {
}

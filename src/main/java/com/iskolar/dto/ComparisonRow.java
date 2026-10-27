package com.iskolar.dto;

import com.iskolar.model.Scholarship;
import com.iskolar.model.ScholarshipCriteria;

/** One column of the side-by-side comparison modal. */
public record ComparisonRow(
        Scholarship scholarship,
        ScholarshipCriteria criteria) {
}

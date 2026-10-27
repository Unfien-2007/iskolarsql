package com.iskolar.dto;

import com.iskolar.model.Scholarship;
import com.iskolar.model.ScholarshipCriteria;

import java.util.List;

/**
 * Aggregate input for the admin CRUD form — a scholarship together with all
 * of its structured eligibility rules, persisted atomically by
 * {@code ScholarshipDao.create/update}.
 */
public record ScholarshipDraft(
        Scholarship scholarship,
        ScholarshipCriteria criteria,
        List<Integer> programIds,
        List<Integer> yearLevels,
        List<TagRequirement> tagRequirements,
        List<Integer> documentIds) {
}

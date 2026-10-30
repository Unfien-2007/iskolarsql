package com.iskolar.server.catalog;

import com.iskolar.server.catalog.CatalogDtos.CatalogSnapshot;
import com.iskolar.server.catalog.CatalogDtos.ScholarshipDto;
import org.springframework.stereotype.Service;

/**
 * Assembles the full catalog snapshot. The client downloads this wholesale
 * and replaces its local catalog tables in one transaction, so the snapshot
 * must contain every table the matching engine reads.
 */
@Service
public class CatalogService {

    private final CatalogRepository repo;

    public CatalogService(CatalogRepository repo) {
        this.repo = repo;
    }

    public int version() {
        return repo.catalogVersion();
    }

    public CatalogSnapshot snapshot() {
        var scholarships = repo.scholarships().stream()
                .map(row -> new ScholarshipDto(
                        row.scholarshipId(), row.createdBy(), row.title(),
                        row.providerName(), row.providerType(), row.description(),
                        row.tuitionBenefit(), row.monthlyStipend(), row.returnServiceYears(),
                        row.applicationUrl(), row.openDate(), row.deadlineDate(), row.isActive(),
                        repo.criteria(row.scholarshipId()).orElse(null),
                        repo.allowedProgramIds(row.scholarshipId()),
                        repo.allowedYearLevels(row.scholarshipId()),
                        repo.requiredTags(row.scholarshipId()),
                        repo.requiredDocumentIds(row.scholarshipId())))
                .toList();
        return new CatalogSnapshot(
                version(),
                repo.programs(),
                repo.tags(),
                repo.documentTypes(),
                scholarships);
    }
}

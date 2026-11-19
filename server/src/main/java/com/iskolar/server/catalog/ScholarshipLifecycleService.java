package com.iskolar.server.catalog;

import com.iskolar.server.admin.ScholarshipAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies time-based catalog maintenance and bumps the catalog version only
 * when something actually changed, so clients do not download redundant
 * snapshots.
 */
@Service
public class ScholarshipLifecycleService {

    private final ScholarshipLifecycleRepository lifecycle;
    private final ScholarshipAdminRepository admin;

    public ScholarshipLifecycleService(ScholarshipLifecycleRepository lifecycle,
                                       ScholarshipAdminRepository admin) {
        this.lifecycle = lifecycle;
        this.admin = admin;
    }

    /** Deactivates expired scholarships and returns how many rows changed. */
    @Transactional
    public int deactivateExpired() {
        int changed = lifecycle.deactivateExpired();
        if (changed > 0) {
            admin.bumpCatalogVersion();
        }
        return changed;
    }

    /**
     * Reactivates all non-expired inactive scholarships and returns how many
     * rows changed.
     */
    @Transactional
    public int reactivateNonExpired() {
        int changed = lifecycle.reactivateNonExpired();
        if (changed > 0) {
            admin.bumpCatalogVersion();
        }
        return changed;
    }
}

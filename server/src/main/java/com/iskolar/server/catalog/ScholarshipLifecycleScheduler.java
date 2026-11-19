package com.iskolar.server.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the expired-scholarship cleanup once a day in Philippine time. The
 * cleanup is intentionally not executed at application startup so tests and
 * seed data remain deterministic.
 */
@Component
public class ScholarshipLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScholarshipLifecycleScheduler.class);

    private final ScholarshipLifecycleService lifecycle;

    public ScholarshipLifecycleScheduler(ScholarshipLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Scheduled(cron = "0 15 2 * * *", zone = "Asia/Manila")
    public void deactivateExpiredScholarships() {
        int changed = lifecycle.deactivateExpired();
        if (changed > 0) {
            log.info("Deactivated {} expired scholarship(s).", changed);
        }
    }
}

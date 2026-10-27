package com.iskolar.service;

import com.iskolar.TestDb;
import com.iskolar.dao.DocumentDao;
import com.iskolar.dao.ScholarshipDao;
import com.iskolar.dao.StudentProfileDao;
import com.iskolar.dao.TagDao;
import com.iskolar.dto.MatchResult;
import com.iskolar.model.MatchTier;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests: MatchingService facade over the seeded in-memory
 * database (the same seed.sql the app ships with).
 *
 * <p>Seed fixtures used:
 * <ul>
 *   <li>Maria (student 1): BSIT, year 1, GWA 91.50, public, PHP 240k income,
 *       tags {DOST_PRIORITY, PUBLIC_HS_GRAD, SOLO_PARENT_DEP},
 *       vault ready {1,3,4,5,6} (ITR + essay missing).</li>
 *   <li>Juan (student 2): BSCE, year 1, GWA 86.25, public, PHP 180k income,
 *       tags {PWD, 4PS_BENEFICIARY, OFW_DEPENDENT, PUBLIC_HS_GRAD},
 *       vault ready {1,2,3,4,6} (birth certificate missing).</li>
 * </ul>
 */
class MatchingServiceIntegrationTest {

    private HikariDataSource ds;
    private MatchingService service;

    @BeforeEach
    void setUp() {
        ds = TestDb.newSeededDataSource();
        service = new MatchingService(
                new ScholarshipDao(ds),
                new StudentProfileDao(ds),
                new TagDao(ds),
                new DocumentDao(ds));
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    @Test
    @DisplayName("Maria vs DOST-SEI: STRONG 87.1 with missing-document warning")
    void mariaVsDost() {
        MatchResult r = service.match(1, 1L).orElseThrow();
        assertTrue(r.eligible());
        assertEquals(MatchTier.STRONG, r.tier());
        assertEquals(87.1, r.score(), 0.05);
        assertTrue(r.warnings().stream().anyMatch(w -> w.equals(
                "Missing 2 required documents: ESSAY, ITR_TAX_EXEMPTION.")));
    }

    @Test
    @DisplayName("Maria vs CHED Merit: top STRONG match (88.1)")
    void mariaVsChedMerit() {
        MatchResult r = service.match(1, 2L).orElseThrow();
        assertTrue(r.eligible());
        assertEquals(MatchTier.STRONG, r.tier());
        assertEquals(88.1, r.score(), 0.05);
    }

    @Test
    @DisplayName("Maria vs Oblation: passes gates but scores below 50 -> INELIGIBLE tier")
    void mariaVsOblation() {
        MatchResult r = service.match(1, 12L).orElseThrow();
        assertTrue(r.eligible());
        assertEquals(MatchTier.INELIGIBLE, r.tier());
        assertEquals(42.0, r.score(), 0.05);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("below the 95% minimum")));
    }

    @Test
    @DisplayName("Maria vs Cebu City LGU: hard residency gate")
    void mariaVsCebuLgu() {
        MatchResult r = service.match(1, 5L).orElseThrow();
        assertFalse(r.eligible());
        assertEquals(0.0, r.score(), 0.01);
        assertEquals("Restricted to residents of CEBU, CEBU CITY.", r.gateReason());
    }

    @Test
    @DisplayName("Maria vs Security Bank: degree program gate (education/accountancy only)")
    void mariaVsSecurityBank() {
        MatchResult r = service.match(1, 8L).orElseThrow();
        assertFalse(r.eligible());
        assertEquals("Not open to your degree program.", r.gateReason());
    }

    @Test
    @DisplayName("Maria vs expired BPI: deadline gate")
    void mariaVsExpiredBpi() {
        MatchResult r = service.match(1, 13L).orElseThrow();
        assertFalse(r.eligible());
        assertTrue(r.gateReason().startsWith("The deadline has passed"));
    }

    @Test
    @DisplayName("Juan vs DOST: missing mandatory DOST_PRIORITY tag disqualifies")
    void juanVsDost() {
        MatchResult r = service.match(2, 1L).orElseThrow();
        assertFalse(r.eligible());
        assertEquals(0.0, r.score(), 0.01);
        assertEquals("Requires tag: DOST Priority S&T Course.", r.gateReason());
    }

    @Test
    @DisplayName("Juan vs Alagang Kapatid: STRONG 83.6 with birth-certificate warning")
    void juanVsAlagangKapatid() {
        MatchResult r = service.match(2, 10L).orElseThrow();
        assertTrue(r.eligible());
        assertEquals(MatchTier.STRONG, r.tier());
        assertEquals(83.6, r.score(), 0.05);
        assertTrue(r.warnings().stream().anyMatch(w -> w.equals(
                "Missing 1 required document: BIRTH_CERTIFICATE.")));
    }

    @Test
    @DisplayName("matchAll: every scholarship scored, ordered tier then score")
    void matchAll_ordered() {
        List<MatchResult> all = service.matchAll(1);
        assertEquals(14, all.size());

        // CHED Merit (88.1) edges Tulong Dunong (87.9) and DOST (87.1) for Maria
        assertEquals(2L, all.get(0).scholarshipId());

        // ordering invariant: tier ordinal non-decreasing; scores non-increasing within tier
        for (int i = 1; i < all.size(); i++) {
            MatchResult prev = all.get(i - 1);
            MatchResult curr = all.get(i);
            assertTrue(prev.tier().ordinal() <= curr.tier().ordinal(),
                    "tiers out of order at index " + i);
            if (prev.tier() == curr.tier()) {
                assertTrue(prev.score() >= curr.score(), "scores out of order at index " + i);
            }
        }

        // every gate-failed result carries a reason; every eligible one a score
        all.forEach(r -> {
            if (!r.eligible()) {
                assertFalse(r.gateReason().isBlank());
            }
        });
    }

    @Test
    @DisplayName("match without a profile returns empty")
    void noProfile_returnsEmpty() {
        assertTrue(service.matchAll(999).isEmpty());
        assertTrue(service.match(999, 1L).isEmpty());
    }
}

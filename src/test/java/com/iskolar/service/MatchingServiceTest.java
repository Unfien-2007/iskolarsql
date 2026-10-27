package com.iskolar.service;

import com.iskolar.dto.MatchResult;
import com.iskolar.dto.ScholarshipEligibilityBundle;
import com.iskolar.dto.TagRequirement;
import com.iskolar.model.MatchTier;
import com.iskolar.model.ProviderType;
import com.iskolar.model.Scholarship;
import com.iskolar.model.ScholarshipCriteria;
import com.iskolar.model.SchoolType;
import com.iskolar.model.Sex;
import com.iskolar.model.StudentProfile;
import com.iskolar.model.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the two-stage engine — no database involved.
 * Stage 1 gate and Stage 2 component methods are package-private, so each
 * branch is asserted directly; {@code match(...)} tests verify composition.
 */
class MatchingServiceTest {

    private static final double TOL = 0.05;

    // ------------------------------------------------------------------
    // Fabrication helpers
    // ------------------------------------------------------------------

    private static Scholarship scholarship(long id, boolean active, LocalDate deadline) {
        return new Scholarship(id, 1L, "Scholarship " + id, "Provider X", ProviderType.GOVERNMENT,
                "desc", "FULL", BigDecimal.ZERO, 0, "http://example.com",
                LocalDate.now().minusDays(10), deadline, active);
    }

    private static ScholarshipCriteria criteria(Double minGwa, boolean allowFailing, BigDecimal maxIncome,
                                                SchoolType schoolType, String province, String city) {
        return new ScholarshipCriteria(1L, 1L, minGwa, allowFailing, maxIncome, schoolType, province, city);
    }

    private static ScholarshipEligibilityBundle bundle(Scholarship s, ScholarshipCriteria c,
                                                       List<Integer> programs, List<Integer> years,
                                                       List<TagRequirement> tags, List<Integer> docs) {
        return new ScholarshipEligibilityBundle(s, c, programs, years, tags, docs);
    }

    private static StudentProfile profile(Integer programId, Integer yearLevel, double gwa, boolean failing,
                                          SchoolType schoolType, BigDecimal income,
                                          String province, String city) {
        return new StudentProfile(1, 2L, "Test", "Student", LocalDate.of(2007, 1, 1), Sex.FEMALE,
                province, city, "Test High School", schoolType, programId, yearLevel, gwa, failing, income, 4);
    }

    /** An active, open, no-requirements scholarship. */
    private static ScholarshipEligibilityBundle openBundle() {
        Scholarship s = scholarship(1, true, LocalDate.now().plusDays(30));
        return bundle(s, criteria(null, true, null, null, null, null),
                List.of(), List.of(), List.of(), List.of());
    }

    private static StudentProfile anyProfile() {
        return profile(1, 1, 90.0, false, SchoolType.PUBLIC, new BigDecimal("200000"), "LAGUNA", "CALAMBA");
    }

    private static TagRequirement req(int tagId, String code, String name, boolean mandatory) {
        return new TagRequirement(new Tag(tagId, code, name, null), mandatory);
    }

    // ==================================================================
    // Stage 1 — hard gates
    // ==================================================================

    @Nested
    @DisplayName("Stage 1 hard eligibility gates")
    class Stage1Gates {

        @Test
        void openScholarship_passesAllGates() {
            assertNull(MatchingService.stage1Gate(openBundle(), anyProfile()));
        }

        @Test
        void inactiveScholarship_isRejected() {
            Scholarship s = scholarship(1, false, LocalDate.now().plusDays(30));
            ScholarshipEligibilityBundle b = bundle(s, criteria(null, true, null, null, null, null),
                    List.of(), List.of(), List.of(), List.of());
            assertTrue(MatchingService.stage1Gate(b, anyProfile()).contains("no longer accepting"));
        }

        @Test
        void expiredDeadline_isRejected() {
            Scholarship s = scholarship(1, true, LocalDate.now().minusDays(1));
            ScholarshipEligibilityBundle b = bundle(s, criteria(null, true, null, null, null, null),
                    List.of(), List.of(), List.of(), List.of());
            assertTrue(MatchingService.stage1Gate(b, anyProfile()).contains("deadline has passed"));
        }

        @Test
        void deadlineToday_stillPasses() {
            Scholarship s = scholarship(1, true, LocalDate.now());
            ScholarshipEligibilityBundle b = bundle(s, criteria(null, true, null, null, null, null),
                    List.of(), List.of(), List.of(), List.of());
            assertNull(MatchingService.stage1Gate(b, anyProfile()));
        }

        @Test
        void programMismatch_isRejected() {
            ScholarshipEligibilityBundle b = bundle(scholarship(1, true, LocalDate.now().plusDays(30)),
                    criteria(null, true, null, null, null, null),
                    List.of(2, 3), List.of(), List.of(), List.of());
            assertEquals("Not open to your degree program.",
                    MatchingService.stage1Gate(b, profile(1, 1, 90, false, SchoolType.PUBLIC, null, null, null)));
        }

        @Test
        void missingProfileProgram_isRejectedWithHint() {
            ScholarshipEligibilityBundle b = bundle(scholarship(1, true, LocalDate.now().plusDays(30)),
                    criteria(null, true, null, null, null, null),
                    List.of(2, 3), List.of(), List.of(), List.of());
            assertTrue(MatchingService.stage1Gate(b, profile(null, 1, 90, false, SchoolType.PUBLIC, null, null, null))
                    .contains("degree program"));
        }

        @Test
        void emptyProgramJunction_isOpenToAll() {
            assertNull(MatchingService.stage1Gate(openBundle(),
                    profile(99, 4, 90, false, SchoolType.PUBLIC, null, null, null)));
        }

        @Test
        void yearMismatch_isRejected() {
            ScholarshipEligibilityBundle b = bundle(scholarship(1, true, LocalDate.now().plusDays(30)),
                    criteria(null, true, null, null, null, null),
                    List.of(), List.of(2, 3), List.of(), List.of());
            assertEquals("Requires year level(s): 2, 3",
                    MatchingService.stage1Gate(b, profile(1, 1, 90, false, SchoolType.PUBLIC, null, null, null)));
        }

        @Test
        void schoolSectorMismatch_isRejected() {
            ScholarshipEligibilityBundle b = bundle(scholarship(1, true, LocalDate.now().plusDays(30)),
                    criteria(null, true, null, SchoolType.PUBLIC, null, null),
                    List.of(), List.of(), List.of(), List.of());
            assertEquals("Open to PUBLIC school students only.",
                    MatchingService.stage1Gate(b, profile(1, 1, 90, false, SchoolType.PRIVATE, null, null, null)));
        }

        @Test
        void nullSchoolSector_allowsBoth() {
            ScholarshipEligibilityBundle b = bundle(scholarship(1, true, LocalDate.now().plusDays(30)),
                    criteria(null, true, null, null, null, null),
                    List.of(), List.of(), List.of(), List.of());
            assertNull(MatchingService.stage1Gate(b, profile(1, 1, 90, false, SchoolType.PRIVATE, null, null, null)));
        }

        @Test
        void failingGrade_notAllowed_isRejected() {
            ScholarshipEligibilityBundle b = bundle(scholarship(1, true, LocalDate.now().plusDays(30)),
                    criteria(null, false, null, null, null, null),
                    List.of(), List.of(), List.of(), List.of());
            assertEquals("Does not allow students with failing grades.",
                    MatchingService.stage1Gate(b, profile(1, 1, 90, true, SchoolType.PUBLIC, null, null, null)));
        }

        @Test
        void failingGrade_allowed_passes() {
            ScholarshipEligibilityBundle b = bundle(scholarship(1, true, LocalDate.now().plusDays(30)),
                    criteria(null, true, null, null, null, null),
                    List.of(), List.of(), List.of(), List.of());
            assertNull(MatchingService.stage1Gate(b, profile(1, 1, 90, true, SchoolType.PUBLIC, null, null, null)));
        }

        @Test
        void residencyProvinceMismatch_isRejected() {
            ScholarshipEligibilityBundle b = bundle(scholarship(1, true, LocalDate.now().plusDays(30)),
                    criteria(null, true, null, null, "CEBU", null),
                    List.of(), List.of(), List.of(), List.of());
            assertEquals("Restricted to residents of CEBU.",
                    MatchingService.stage1Gate(b, profile(1, 1, 90, false, SchoolType.PUBLIC, null, "LAGUNA", null)));
        }

        @Test
        void residencyCityMismatch_isRejected() {
            ScholarshipEligibilityBundle b = bundle(scholarship(1, true, LocalDate.now().plusDays(30)),
                    criteria(null, true, null, null, null, "CEBU CITY"),
                    List.of(), List.of(), List.of(), List.of());
            assertEquals("Restricted to residents of CEBU CITY.",
                    MatchingService.stage1Gate(b, profile(1, 1, 90, false, SchoolType.PUBLIC, null, null, "CALAMBA")));
        }

        @Test
        void residencyBothMatch_passesCaseInsensitive() {
            ScholarshipEligibilityBundle b = bundle(scholarship(1, true, LocalDate.now().plusDays(30)),
                    criteria(null, true, null, null, "CEBU", "CEBU CITY"),
                    List.of(), List.of(), List.of(), List.of());
            assertNull(MatchingService.stage1Gate(b,
                    profile(1, 1, 90, false, SchoolType.PUBLIC, null, "cebu", "cebu city")));
        }
    }

    // ==================================================================
    // Stage 2 — academic (35%)
    // ==================================================================

    @Nested
    @DisplayName("Stage 2 academic component (35%)")
    class AcademicComponent {

        private final StudentProfile p = profile(1, 1, 0, false, SchoolType.PUBLIC, null, null, null);

        @Test
        void noMinimum_fullPoints() {
            var r = MatchingService.academicScore(criteria(null, true, null, null, null, null), p);
            assertEquals(35.0, r.points(), TOL);
            assertFalse(r.borderline());
            assertNull(r.warning());
        }

        @Test
        void meetsMinimum_baseOnly() {
            var r = MatchingService.academicScore(criteria(83.0, true, null, null, null, null),
                    profile(1, 1, 83.0, false, SchoolType.PUBLIC, null, null, null));
            assertEquals(21.0, r.points(), TOL);
            assertFalse(r.borderline());
        }

        @Test
        void exceedsMinimum_competitiveBuffer() {
            // 91.5 vs 83: buffer = 8.5/10 * 14 = 11.9 -> 32.9
            var r = MatchingService.academicScore(criteria(83.0, true, null, null, null, null),
                    profile(1, 1, 91.5, false, SchoolType.PUBLIC, null, null, null));
            assertEquals(32.9, r.points(), TOL);
        }

        @Test
        void farAboveMinimum_bufferCapped() {
            var r = MatchingService.academicScore(criteria(83.0, true, null, null, null, null),
                    profile(1, 1, 100.0, false, SchoolType.PUBLIC, null, null, null));
            assertEquals(35.0, r.points(), TOL);
        }

        @Test
        void nearMissGraceZone_borderlineFlagged() {
            var r = MatchingService.academicScore(criteria(83.0, true, null, null, null, null),
                    profile(1, 1, 81.6, false, SchoolType.PUBLIC, null, null, null));
            assertEquals(10.5, r.points(), TOL);
            assertTrue(r.borderline());
            assertTrue(r.warning().startsWith("Borderline Grade:"));
        }

        @Test
        void belowMinimum_zeroWithWarning() {
            var r = MatchingService.academicScore(criteria(83.0, true, null, null, null, null),
                    profile(1, 1, 80.0, false, SchoolType.PUBLIC, null, null, null));
            assertEquals(0.0, r.points(), TOL);
            assertTrue(r.warning().contains("below the 83% minimum"));
        }
    }

    // ==================================================================
    // Stage 2 — financial (30%)
    // ==================================================================

    @Nested
    @DisplayName("Stage 2 financial component (30%)")
    class FinancialComponent {

        private final StudentProfile p = profile(1, 1, 90, false, SchoolType.PUBLIC, null, null, null);

        @Test
        void meritOnly_fullPoints() {
            var r = MatchingService.financialScore(criteria(null, true, null, null, null, null), p);
            assertEquals(30.0, r.points(), TOL);
            assertNull(r.warning());
        }

        @Test
        void incomeAtCap_baseOnly() {
            var r = MatchingService.financialScore(criteria(null, true, new BigDecimal("300000"), null, null, null),
                    profile(1, 1, 90, false, SchoolType.PUBLIC, new BigDecimal("300000"), null, null));
            assertEquals(18.0, r.points(), TOL);
        }

        @Test
        void incomeHalfCap_basePlusHalfBuffer() {
            var r = MatchingService.financialScore(criteria(null, true, new BigDecimal("300000"), null, null, null),
                    profile(1, 1, 90, false, SchoolType.PUBLIC, new BigDecimal("150000"), null, null));
            assertEquals(24.0, r.points(), TOL);
        }

        @Test
        void incomeOverCap_zeroWithWarning() {
            var r = MatchingService.financialScore(criteria(null, true, new BigDecimal("300000"), null, null, null),
                    profile(1, 1, 90, false, SchoolType.PUBLIC, new BigDecimal("350000"), null, null));
            assertEquals(0.0, r.points(), TOL);
            assertTrue(r.warning().contains("exceeds the cap"));
            assertTrue(r.warning().contains("350,000"));
        }

        @Test
        void incomeUnknown_zeroWithWarning() {
            var r = MatchingService.financialScore(criteria(null, true, new BigDecimal("300000"), null, null, null), p);
            assertEquals(0.0, r.points(), TOL);
            assertTrue(r.warning().contains("not on file"));
        }
    }

    // ==================================================================
    // Stage 2 — demographics & tags (20%)
    // ==================================================================

    @Nested
    @DisplayName("Stage 2 tags component (20%)")
    class TagsComponent {

        @Test
        void noExpectations_fullPoints() {
            var r = MatchingService.tagsScore(List.of(), Set.of());
            assertEquals(20.0, r.points(), TOL);
            assertNull(r.disqualifyReason());
        }

        @Test
        void mandatoryTagMissing_disqualifies() {
            var r = MatchingService.tagsScore(List.of(req(1, "PWD", "Person with Disability", true)), Set.of());
            assertEquals(0.0, r.points(), TOL);
            assertEquals("Requires tag: Person with Disability.", r.disqualifyReason());
        }

        @Test
        void multipleMandatoryMissing_pluralReason() {
            var r = MatchingService.tagsScore(
                    List.of(req(1, "PWD", "Person with Disability", true), req(8, "4PS", "4Ps Beneficiary", true)),
                    Set.of());
            assertEquals("Requires tags: Person with Disability, 4Ps Beneficiary.", r.disqualifyReason());
        }

        @Test
        void mandatoryPresent_preferredMissed_zeroPoints() {
            var r = MatchingService.tagsScore(
                    List.of(req(1, "PWD", "Person with Disability", true), req(2, "SP", "Solo Parent Dep", false)),
                    Set.of(1));
            assertEquals(0.0, r.points(), TOL);
            assertNull(r.disqualifyReason());
        }

        @Test
        void preferredTags_proportionalPoints() {
            var r = MatchingService.tagsScore(
                    List.of(req(1, "PWD", "Person with Disability", false), req(2, "SP", "Solo Parent Dep", false)),
                    Set.of(1));
            assertEquals(10.0, r.points(), TOL);
        }

        @Test
        void allPreferredMatched_fullPoints() {
            var r = MatchingService.tagsScore(List.of(req(1, "PWD", "Person with Disability", false)), Set.of(1));
            assertEquals(20.0, r.points(), TOL);
        }
    }

    // ==================================================================
    // Stage 2 — document readiness (15%)
    // ==================================================================

    @Nested
    @DisplayName("Stage 2 documents component (15%)")
    class DocumentsComponent {

        private static final Map<Integer, String> NAMES =
                Map.of(1, "GRADES_CTC", 2, "ITR", 4, "GOOD_MORAL", 5, "BIRTH_CERTIFICATE",
                        6, "ENROLLMENT_FORM", 7, "ESSAY");

        @Test
        void noneRequired_fullPoints() {
            var r = MatchingService.documentsScore(List.of(), Set.of(), NAMES);
            assertEquals(15.0, r.points(), TOL);
            assertNull(r.warning());
        }

        @Test
        void allReady_fullPointsNoWarning() {
            var r = MatchingService.documentsScore(List.of(1, 2, 4), Set.of(1, 2, 4), NAMES);
            assertEquals(15.0, r.points(), TOL);
            assertNull(r.warning());
        }

        @Test
        void proportionalPoints_withSingularWarning() {
            // 1 of 2 ready -> 7.5, warning names the missing document
            var r = MatchingService.documentsScore(List.of(1, 2), Set.of(1), NAMES);
            assertEquals(7.5, r.points(), TOL);
            assertEquals("Missing 1 required document: ITR.", r.warning());
        }

        @Test
        void pluralWarning_listsMissingSorted() {
            var r = MatchingService.documentsScore(List.of(2, 7), Set.of(), NAMES);
            assertEquals(0.0, r.points(), TOL);
            assertEquals("Missing 2 required documents: ESSAY, ITR.", r.warning());
        }

        @Test
        void unknownDocumentId_fallsBackToNumber() {
            var r = MatchingService.documentsScore(List.of(99), Set.of(), NAMES);
            assertEquals("Missing 1 required document: document #99.", r.warning());
        }
    }

    // ==================================================================
    // End-to-end composition
    // ==================================================================

    @Nested
    @DisplayName("End-to-end match()")
    class EndToEnd {

        @Test
        void noRequirementsAtAll_scores100Prime() {
            MatchResult r = MatchingService.match(openBundle(), anyProfile(), Set.of(), Set.of(), Map.of());
            assertEquals(100.0, r.score(), TOL);
            assertEquals(MatchTier.PRIME, r.tier());
            assertTrue(r.eligible());
            assertNull(r.gateReason());
            assertTrue(r.warnings().isEmpty());
        }

        @Test
        void gateFailure_scoresZeroIneligibleWithReason() {
            Scholarship s = scholarship(7, false, LocalDate.now().plusDays(30));
            ScholarshipEligibilityBundle b = bundle(s, criteria(null, true, null, null, null, null),
                    List.of(), List.of(), List.of(), List.of());
            MatchResult r = MatchingService.match(b, anyProfile(), Set.of(), Set.of(), Map.of());
            assertEquals(0.0, r.score(), TOL);
            assertEquals(MatchTier.INELIGIBLE, r.tier());
            assertFalse(r.eligible());
            assertNotNull(r.gateReason());
        }

        @Test
        void dostLikeScenario_composesAndRounds() {
            // DOST-like: min 83, cap 500k, BSIT-only, year 1, mandatory DOST_PRIORITY,
            // 6 required docs of which 4 ready. Expected: 32.9 + 24.24 + 20 + 10 = 87.1
            Scholarship s = scholarship(1, true, LocalDate.now().plusDays(30));
            ScholarshipCriteria c = criteria(83.0, false, new BigDecimal("500000"), null, null, null);
            List<TagRequirement> tags = List.of(req(5, "DOST_PRIORITY", "DOST Priority S&T Course", true));
            ScholarshipEligibilityBundle b = bundle(s, c, List.of(1), List.of(1), tags,
                    List.of(1, 2, 4, 5, 6, 7));

            StudentProfile p = profile(1, 1, 91.5, false, SchoolType.PUBLIC, new BigDecimal("240000"),
                    "LAGUNA", "SANTA ROSA");
            MatchResult r = MatchingService.match(b, p, Set.of(5), Set.of(1, 4, 5, 6),
                    Map.of(1, "GRADES_CTC", 2, "ITR", 4, "GOOD_MORAL", 5, "BIRTH_CERTIFICATE",
                            6, "ENROLLMENT_FORM", 7, "ESSAY"));

            assertEquals(87.1, r.score(), TOL);
            assertEquals(MatchTier.STRONG, r.tier());
            assertTrue(r.eligible());
            assertEquals("Missing 2 required documents: ESSAY, ITR.", r.warnings().get(0));
        }

        @Test
        void passedGatesButLowScore_isEligibleWithIneligibleTier() {
            // Oblation-like: min 95 (missed by more than the grace zone), merit-only,
            // one preferred tag missed, 4 of 5 docs ready -> 0 + 30 + 0 + 12 = 42
            Scholarship s = scholarship(12, true, LocalDate.now().plusDays(30));
            ScholarshipCriteria c = criteria(95.0, false, null, null, null, null);
            ScholarshipEligibilityBundle b = bundle(s, c, List.of(), List.of(),
                    List.of(req(9, "VALEDICTORIAN", "Batch Valedictorian", false)),
                    List.of(1, 4, 5, 6, 7));

            StudentProfile p = profile(1, 1, 91.5, false, SchoolType.PUBLIC, new BigDecimal("240000"),
                    "LAGUNA", "SANTA ROSA");
            MatchResult r = MatchingService.match(b, p, Set.of(), Set.of(1, 4, 5, 6), Map.of());

            assertEquals(42.0, r.score(), TOL);
            assertEquals(MatchTier.INELIGIBLE, r.tier());
            assertTrue(r.eligible());
            assertNull(r.gateReason());
            assertTrue(r.warnings().stream().anyMatch(w -> w.contains("below the 95% minimum")));
        }

        @Test
        void borderlineFlagSurfacesOnResult() {
            Scholarship s = scholarship(1, true, LocalDate.now().plusDays(30));
            ScholarshipEligibilityBundle b = bundle(s, criteria(83.0, true, null, null, null, null),
                    List.of(), List.of(), List.of(), List.of());
            MatchResult r = MatchingService.match(b,
                    profile(1, 1, 81.6, false, SchoolType.PUBLIC, null, null, null),
                    Set.of(), Set.of(), Map.of());
            assertTrue(r.borderlineGrade());
            assertTrue(r.warnings().stream().anyMatch(w -> w.startsWith("Borderline Grade:")));
        }
    }
}

package com.iskolar.service;

import com.iskolar.dao.DocumentDao;
import com.iskolar.dao.ScholarshipDao;
import com.iskolar.dao.StudentProfileDao;
import com.iskolar.dao.TagDao;
import com.iskolar.dto.MatchResult;
import com.iskolar.dto.ScholarshipEligibilityBundle;
import com.iskolar.dto.TagRequirement;
import com.iskolar.model.DocumentType;
import com.iskolar.model.StudentDocument;
import com.iskolar.model.StudentProfile;
import com.iskolar.model.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The two-stage scholarship matching engine.
 *
 * <p><b>Stage 1 — Hard Eligibility Gate</b> (any failure &rarr; {@code INELIGIBLE},
 * 0% match, with a clear reason):
 * <ol>
 *   <li>Active &amp; not past deadline</li>
 *   <li>Allowed degree program (empty junction = open to all)</li>
 *   <li>Allowed year level (empty junction = open to all)</li>
 *   <li>Allowed school sector ({@code NULL} = public + private)</li>
 *   <li>Failing-grade rule</li>
 *   <li>Hard residency (province/city)</li>
 * </ol>
 *
 * <p><b>Stage 2 — Weighted Compatibility Score (0-100)</b>:
 * <ul>
 *   <li><b>Academic (35%)</b> — base for meeting the minimum GWA (60% of the
 *       component) + a competitive buffer for exceeding it, linear over a
 *       10-point band (40%). Within the 1.5-point near-miss grace zone earns a
 *       partial credit and is flagged <i>Borderline Grade</i>.</li>
 *   <li><b>Financial (30%)</b> — full points when merit-only
 *       ({@code max_annual_income IS NULL}); otherwise base for being under the
 *       cap + a priority bonus scaled by how far below the cap the family income
 *       falls.</li>
 *   <li><b>Demographics &amp; Tags (20%)</b> — missing a mandatory tag
 *       disqualifies; preferred tags earn proportional points.</li>
 *   <li><b>Document Readiness (15%)</b> — proportional to required documents
 *       already marked ready in the student's vault.</li>
 * </ul>
 *
 * <p>The scoring core is a pure static function (unit-testable without a
 * database); the instance methods are a thin DAO-backed facade.
 */
public class MatchingService {

    // ---- Stage 2 weights (must sum to 100) ----
    public static final double ACADEMIC_WEIGHT = 35.0;
    public static final double FINANCIAL_WEIGHT = 30.0;
    public static final double TAGS_WEIGHT = 20.0;
    public static final double DOCUMENTS_WEIGHT = 15.0;

    // ---- tuning ----
    /** Share of a component awarded for merely meeting the minimum. */
    public static final double BASE_SHARE = 0.60;
    /** Share of a component awarded as a competitive buffer. */
    public static final double BUFFER_SHARE = 0.40;
    /** GWA points above the minimum needed to max out the academic buffer. */
    public static final double BUFFER_BAND = 10.0;
    /** Near-miss grace zone (GWA points below the minimum). */
    public static final double GRACE_ZONE = 1.5;
    /** Share of the academic component granted inside the grace zone. */
    public static final double GRACE_SHARE = 0.30;

    private final ScholarshipDao scholarshipDao;
    private final StudentProfileDao studentProfileDao;
    private final TagDao tagDao;
    private final DocumentDao documentDao;

    public MatchingService(ScholarshipDao scholarshipDao, StudentProfileDao studentProfileDao,
                           TagDao tagDao, DocumentDao documentDao) {
        this.scholarshipDao = scholarshipDao;
        this.studentProfileDao = studentProfileDao;
        this.tagDao = tagDao;
        this.documentDao = documentDao;
    }

    // ==================================================================
    // Facade (DAO-backed)
    // ==================================================================

    /**
     * Scores every scholarship for a student, ordered best-first
     * (tier ascending, then score descending). Returns an empty list when the
     * student has no profile yet.
     */
    public List<MatchResult> matchAll(int studentId) {
        Optional<StudentProfile> profileOpt = studentProfileDao.findById(studentId);
        if (profileOpt.isEmpty()) {
            return List.of();
        }
        StudentProfile profile = profileOpt.get();
        Set<Integer> tagIds = tagDao.findForStudent(studentId).stream()
                .map(Tag::tagId)
                .collect(Collectors.toUnmodifiableSet());
        Set<Integer> readyDocs = documentDao.findForStudent(studentId).stream()
                .filter(StudentDocument::isReady)
                .map(StudentDocument::documentTypeId)
                .collect(Collectors.toUnmodifiableSet());
        Map<Integer, String> docNames = documentDao.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(DocumentType::documentTypeId, DocumentType::docCode));

        return scholarshipDao.findEligibilityBundles().stream()
                .map(bundle -> match(bundle, profile, tagIds, readyDocs, docNames))
                .sorted(Comparator.comparing((MatchResult m) -> m.tier().ordinal())
                        .thenComparing(Comparator.comparingDouble(MatchResult::score).reversed()))
                .toList();
    }

    /** Scores a single scholarship for a student. */
    public Optional<MatchResult> match(int studentId, long scholarshipId) {
        return studentProfileDao.findById(studentId)
                .flatMap(profile -> scholarshipDao.findEligibilityBundle(scholarshipId).map(bundle ->
                        match(bundle,
                                profile,
                                tagDao.findForStudent(studentId).stream()
                                        .map(Tag::tagId)
                                        .collect(Collectors.toUnmodifiableSet()),
                                documentDao.findForStudent(studentId).stream()
                                        .filter(StudentDocument::isReady)
                                        .map(StudentDocument::documentTypeId)
                                        .collect(Collectors.toUnmodifiableSet()),
                                documentDao.findAll().stream()
                                        .collect(Collectors.toUnmodifiableMap(
                                                DocumentType::documentTypeId, DocumentType::docCode)))));
    }

    // ==================================================================
    // Pure two-stage engine
    // ==================================================================

    /**
     * Scores one bundle. Pure function — no database access.
     *
     * @param documentNames document-type-id &rarr; display code (e.g. 2 &rarr;
     *                      "ITR"); used only for warning text
     */
    public static MatchResult match(ScholarshipEligibilityBundle bundle,
                                    StudentProfile profile,
                                    Set<Integer> studentTagIds,
                                    Set<Integer> readyDocumentIds,
                                    Map<Integer, String> documentNames) {
        long id = bundle.scholarship().scholarshipId();
        String title = bundle.scholarship().title();

        String gateReason = stage1Gate(bundle, profile);
        if (gateReason != null) {
            return MatchResult.ineligible(id, title, gateReason);
        }

        TagsOutcome tags = tagsScore(bundle.tagRequirements(), studentTagIds);
        if (tags.disqualifyReason() != null) {
            return MatchResult.ineligible(id, title, tags.disqualifyReason());
        }

        AcademicOutcome academic = academicScore(bundle.criteria(), profile);
        FinancialOutcome financial = financialScore(bundle.criteria(), profile);
        DocumentsOutcome documents = documentsScore(bundle.requiredDocumentIds(), readyDocumentIds, documentNames);

        double total = round1(academic.points() + financial.points() + tags.points() + documents.points());

        List<String> warnings = new ArrayList<>();
        if (academic.warning() != null) {
            warnings.add(academic.warning());
        }
        if (financial.warning() != null) {
            warnings.add(financial.warning());
        }
        if (documents.warning() != null) {
            warnings.add(documents.warning());
        }

        return MatchResult.eligible(id, title, total, academic.borderline(), warnings);
    }

    /** Convenience overload with no document names (warnings use {@code document #N}). */
    public static MatchResult match(ScholarshipEligibilityBundle bundle,
                                    StudentProfile profile,
                                    Set<Integer> studentTagIds,
                                    Set<Integer> readyDocumentIds) {
        return match(bundle, profile, studentTagIds, readyDocumentIds, Map.of());
    }

    // ------------------------------------------------------------------
    // Stage 1
    // ------------------------------------------------------------------

    /** Returns a disqualification reason, or {@code null} when all gates pass. */
    static String stage1Gate(ScholarshipEligibilityBundle bundle, StudentProfile profile) {
        var scholarship = bundle.scholarship();
        var criteria = bundle.criteria();

        if (!scholarship.isActive()) {
            return "This scholarship is no longer accepting applications.";
        }
        LocalDate today = LocalDate.now();
        if (scholarship.deadlineDate() == null || scholarship.deadlineDate().isBefore(today)) {
            return scholarship.deadlineDate() == null
                    ? "The deadline has passed."
                    : "The deadline has passed (" + scholarship.deadlineDate() + ").";
        }
        if (!bundle.allowedProgramIds().isEmpty()) {
            if (profile.programId() == null) {
                return "Add your degree program to your profile to check eligibility.";
            }
            if (!bundle.allowedProgramIds().contains(profile.programId())) {
                return "Not open to your degree program.";
            }
        }
        if (!bundle.allowedYearLevels().isEmpty()) {
            if (profile.yearLevel() == null || !bundle.allowedYearLevels().contains(profile.yearLevel())) {
                return "Requires year level(s): "
                        + bundle.allowedYearLevels().stream().map(String::valueOf)
                        .collect(Collectors.joining(", "));
            }
        }
        if (criteria.allowedSchoolType() != null && criteria.allowedSchoolType() != profile.schoolType()) {
            return "Open to " + criteria.allowedSchoolType().name() + " school students only.";
        }
        if (profile.hasFailingGrade() && !criteria.allowFailingGrade()) {
            return "Does not allow students with failing grades.";
        }
        if (criteria.requiredProvince() != null || criteria.requiredCity() != null) {
            boolean provinceOk = criteria.requiredProvince() == null
                    || equalsIgnoreCase(criteria.requiredProvince(), profile.province());
            boolean cityOk = criteria.requiredCity() == null
                    || equalsIgnoreCase(criteria.requiredCity(), profile.cityMunicipality());
            if (!provinceOk || !cityOk) {
                String location = java.util.stream.Stream
                        .of(criteria.requiredProvince(), criteria.requiredCity())
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.joining(", "));
                return "Restricted to residents of " + location + ".";
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Stage 2 components
    // ------------------------------------------------------------------

    /** Academic (35%): base + competitive buffer, with a flagged grace zone. */
    static AcademicOutcome academicScore(com.iskolar.model.ScholarshipCriteria criteria, StudentProfile profile) {
        if (criteria.minGwaPercentage() == null) {
            return new AcademicOutcome(ACADEMIC_WEIGHT, false, null);
        }
        double min = criteria.minGwaPercentage();
        double gwa = profile.gwaPercentage();
        if (gwa >= min) {
            double excess = Math.min(gwa - min, BUFFER_BAND);
            double buffer = excess / BUFFER_BAND * (ACADEMIC_WEIGHT * BUFFER_SHARE);
            return new AcademicOutcome(ACADEMIC_WEIGHT * BASE_SHARE + buffer, false, null);
        }
        if (gwa >= min - GRACE_ZONE) {
            return new AcademicOutcome(ACADEMIC_WEIGHT * GRACE_SHARE, true,
                    "Borderline Grade: GWA " + fmt(gwa) + "% is within " + fmt(GRACE_ZONE)
                            + " points of the " + fmt(min) + "% minimum.");
        }
        return new AcademicOutcome(0.0, false,
                "GWA " + fmt(gwa) + "% is below the " + fmt(min) + "% minimum.");
    }

    /** Financial (30%): merit-only, or base under cap + scaled priority bonus. */
    static FinancialOutcome financialScore(com.iskolar.model.ScholarshipCriteria criteria, StudentProfile profile) {
        if (criteria.maxAnnualIncome() == null) {
            return new FinancialOutcome(FINANCIAL_WEIGHT, null); // merit-only
        }
        BigDecimal cap = criteria.maxAnnualIncome();
        BigDecimal income = profile.annualFamilyIncome();
        if (income == null) {
            return new FinancialOutcome(0.0, "Family income is not on file - complete your financial profile.");
        }
        if (income.compareTo(cap) > 0) {
            return new FinancialOutcome(0.0, "Family income (PHP " + fmtMoney(income)
                    + ") exceeds the cap (PHP " + fmtMoney(cap) + ").");
        }
        if (cap.signum() <= 0) {
            return new FinancialOutcome(FINANCIAL_WEIGHT * BASE_SHARE, null);
        }
        double ratio = income.doubleValue() / cap.doubleValue();
        double priorityBonus = (1.0 - ratio) * (FINANCIAL_WEIGHT * BUFFER_SHARE);
        return new FinancialOutcome(FINANCIAL_WEIGHT * BASE_SHARE + priorityBonus, null);
    }

    /** Demographics &amp; Tags (20%): mandatory = hard gate, preferred = proportional. */
    static TagsOutcome tagsScore(List<TagRequirement> requirements, Set<Integer> studentTagIds) {
        List<TagRequirement> mandatory = requirements.stream()
                .filter(TagRequirement::mandatory)
                .toList();
        List<String> missingMandatory = mandatory.stream()
                .filter(r -> !studentTagIds.contains(r.tag().tagId()))
                .map(r -> r.tag().tagName())
                .toList();
        if (!missingMandatory.isEmpty()) {
            String label = missingMandatory.size() > 1 ? "tags" : "tag";
            return new TagsOutcome(0.0,
                    "Requires " + label + ": " + String.join(", ", missingMandatory) + ".");
        }
        List<TagRequirement> preferred = requirements.stream()
                .filter(r -> !r.mandatory())
                .toList();
        if (preferred.isEmpty()) {
            return new TagsOutcome(TAGS_WEIGHT, null);
        }
        long matched = preferred.stream()
                .filter(r -> studentTagIds.contains(r.tag().tagId()))
                .count();
        return new TagsOutcome(TAGS_WEIGHT * matched / preferred.size(), null);
    }

    /** Document Readiness (15%): proportional to ready required documents. */
    static DocumentsOutcome documentsScore(List<Integer> requiredDocumentIds,
                                           Set<Integer> readyDocumentIds,
                                           Map<Integer, String> documentNames) {
        if (requiredDocumentIds.isEmpty()) {
            return new DocumentsOutcome(DOCUMENTS_WEIGHT, null);
        }
        long ready = requiredDocumentIds.stream().filter(readyDocumentIds::contains).count();
        double points = DOCUMENTS_WEIGHT * ready / requiredDocumentIds.size();
        if (ready >= requiredDocumentIds.size()) {
            return new DocumentsOutcome(points, null);
        }
        String missing = requiredDocumentIds.stream()
                .filter(id -> !readyDocumentIds.contains(id))
                .map(id -> documentNames.getOrDefault(id, "document #" + id))
                .sorted()
                .collect(Collectors.joining(", "));
        long missingCount = requiredDocumentIds.size() - ready;
        String warning = "Missing " + missingCount + (missingCount > 1 ? " required documents" : " required document")
                + ": " + missing + ".";
        return new DocumentsOutcome(points, warning);
    }

    // ------------------------------------------------------------------
    // Helpers + component outcome records
    // ------------------------------------------------------------------

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** {@code 91.5 -> "91.5"}, {@code 83.0 -> "83"}, {@code 1.5 -> "1.5"}. */
    private static String fmt(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** {@code 300000 -> "300,000"}. */
    private static String fmtMoney(BigDecimal value) {
        return String.format("%,.0f", value);
    }

    record AcademicOutcome(double points, boolean borderline, String warning) {
    }

    record FinancialOutcome(double points, String warning) {
    }

    record TagsOutcome(double points, String disqualifyReason) {
    }

    record DocumentsOutcome(double points, String warning) {
    }
}

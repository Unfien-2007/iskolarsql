package com.iskolar.dto;

import com.iskolar.model.MatchTier;

import java.util.List;

/**
 * The outcome of the two-stage matching engine for one scholarship.
 *
 * <ul>
 *   <li>{@code eligible == false} — failed a Stage 1 hard gate or a mandatory
 *       tag; {@code score} is forced to 0 and {@code gateReason} explains why.</li>
 *   <li>{@code eligible == true} — passed all gates; {@code score} is the
 *       weighted Stage 2 compatibility score (0-100) and {@code tier} classifies
 *       it. A passed-gates score below 50 still maps to
 *       {@link MatchTier#INELIGIBLE} (see spec thresholds).</li>
 *   <li>{@code borderlineGrade} — GWA is inside the 1.5-point near-miss grace
 *       zone below the minimum.</li>
 *   <li>{@code warnings} — actionable hints, e.g.
 *       <i>"Missing 1 required document: ITR"</i>.</li>
 * </ul>
 */
public record MatchResult(
        long scholarshipId,
        String scholarshipTitle,
        double score,
        MatchTier tier,
        boolean eligible,
        String gateReason,
        boolean borderlineGrade,
        List<String> warnings) {

    public MatchResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Factory for a gate-passing result; the tier is derived from the score. */
    public static MatchResult eligible(long scholarshipId, String scholarshipTitle,
                                       double score, boolean borderlineGrade, List<String> warnings) {
        return new MatchResult(scholarshipId, scholarshipTitle, score,
                MatchTier.forScore(score), true, null, borderlineGrade, warnings);
    }

    /** Factory for a Stage 1 / mandatory-tag disqualification (0% match). */
    public static MatchResult ineligible(long scholarshipId, String scholarshipTitle,
                                         String gateReason) {
        return new MatchResult(scholarshipId, scholarshipTitle, 0.0,
                MatchTier.INELIGIBLE, false, gateReason, false, List.of());
    }

    /** {@code "87%"} for the match badge. */
    public String scoreLabel() {
        return String.format("%.0f%%", score);
    }
}

package com.iskolar.model;

/**
 * Match quality tiers produced by the Stage 2 weighted compatibility score.
 *
 * <p>{@code PRIME} 90-100 · {@code STRONG} 75-89 · {@code STRETCH} 50-74 ·
 * {@code INELIGIBLE} &lt;50 (or failed a Stage 1 hard gate).
 */
public enum MatchTier {
    PRIME("Prime Match", 90),
    STRONG("Strong Match", 75),
    STRETCH("Stretch Match", 50),
    INELIGIBLE("Ineligible", 0);

    private final String label;
    private final int minScore;

    MatchTier(String label, int minScore) {
        this.label = label;
        this.minScore = minScore;
    }

    public String label() {
        return label;
    }

    public int minScore() {
        return minScore;
    }

    /**
     * Classifies a 0-100 score into its tier. Primitive type patterns are
     * still preview in Java 21, so the selector is boxed — the guarded
     * reference patterns themselves are final.
     */
    public static MatchTier forScore(double score) {
        return switch ((Double) score) {
            case Double s when s >= 90 -> PRIME;
            case Double s when s >= 75 -> STRONG;
            case Double s when s >= 50 -> STRETCH;
            case null, default -> INELIGIBLE;
        };
    }
}

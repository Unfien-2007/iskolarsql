package com.iskolar.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tier boundary checks for {@link MatchTier#forScore(double)}. */
class MatchTierTest {

    @Test
    void score100_isPrime() {
        assertEquals(MatchTier.PRIME, MatchTier.forScore(100));
    }

    @Test
    void score90_isPrime_boundary() {
        assertEquals(MatchTier.PRIME, MatchTier.forScore(90));
    }

    @Test
    void justBelow90_isStrong() {
        assertEquals(MatchTier.STRONG, MatchTier.forScore(89.9));
    }

    @Test
    void score75_isStrong_boundary() {
        assertEquals(MatchTier.STRONG, MatchTier.forScore(75));
    }

    @Test
    void justBelow75_isStretch() {
        assertEquals(MatchTier.STRETCH, MatchTier.forScore(74.9));
    }

    @Test
    void score50_isStretch_boundary() {
        assertEquals(MatchTier.STRETCH, MatchTier.forScore(50));
    }

    @Test
    void justBelow50_isIneligible() {
        assertEquals(MatchTier.INELIGIBLE, MatchTier.forScore(49.9));
    }

    @Test
    void zero_isIneligible() {
        assertEquals(MatchTier.INELIGIBLE, MatchTier.forScore(0));
    }
}

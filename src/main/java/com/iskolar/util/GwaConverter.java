package com.iskolar.util;

import java.math.BigDecimal;

/**
 * GWA standardization helpers: the Philippine 5-point scale
 * (1.00 best … 5.00 worst) and the app's internal 0-100% scale.
 *
 * <p>Conversion: {@code percent = (5.0 - gwa5) * 25} — e.g. GWA 1.25 → 93.75%.
 */
public final class GwaConverter {

    private GwaConverter() {
    }

    /** 5-point scale → percentage. {@code 1.25 -> 93.75}. */
    public static double fivePointToPercent(double gwa5Point) {
        return (5.0 - gwa5Point) * 25.0;
    }

    /** Percentage → 5-point scale. {@code 93.75 -> 1.25}. */
    public static double percentToFivePoint(double percent) {
        return 5.0 - percent / 25.0;
    }

    /** {@code 93.75 -> "93.75%"}, trimming trailing zeros ({@code 90.0 -> "90%"}). */
    public static String formatPercent(double percent) {
        return BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString() + "%";
    }
}

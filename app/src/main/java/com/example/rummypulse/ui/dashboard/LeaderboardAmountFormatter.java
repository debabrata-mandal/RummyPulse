package com.example.rummypulse.ui.dashboard;

import java.util.Locale;

/**
 * Formats ranked net amounts for the dashboard and the player ranking screen.
 *
 * <p>Shared so the donut slice labels and the ranking rows cannot drift apart. Amounts are rounded
 * to whole rupees because the ranking is about direction and magnitude, not paise.
 */
public final class LeaderboardAmountFormatter {

    private LeaderboardAmountFormatter() {
    }

    /** Signed whole rupees with thousands separators, e.g. {@code +₹1,240} or {@code ₹0}. */
    public static String formatSigned(double value) {
        long rounded = Math.round(value);
        if (rounded > 0) {
            return String.format(Locale.getDefault(), "+₹%,d", rounded);
        }
        if (rounded < 0) {
            return String.format(Locale.getDefault(), "-₹%,d", Math.abs(rounded));
        }
        return "₹0";
    }
}

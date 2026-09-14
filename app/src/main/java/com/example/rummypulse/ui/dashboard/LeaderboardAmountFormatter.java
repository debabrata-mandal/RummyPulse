package com.example.rummypulse.ui.dashboard;

import java.util.Locale;

/**
 * Formats ranked Game Point results for the dashboard and the player ranking screen.
 *
 * <p>Shared so the donut slice labels and the ranking rows cannot drift apart. Amounts are rounded
 * to whole Game Points because the ranking is about direction and magnitude.
 */
public final class LeaderboardAmountFormatter {

    private LeaderboardAmountFormatter() {
    }

    /** Signed whole Game Points with thousands separators, e.g. {@code +1,240 GP}. */
    public static String formatSigned(double value) {
        long rounded = Math.round(value);
        if (rounded > 0) {
            return String.format(Locale.getDefault(), "+%,d GP", rounded);
        }
        if (rounded < 0) {
            return String.format(Locale.getDefault(), "-%,d GP", Math.abs(rounded));
        }
        return "0 GP";
    }
}

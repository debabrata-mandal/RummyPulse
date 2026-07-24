package com.example.rummypulse.data;

import androidx.annotation.Nullable;

/**
 * Monotonic progress rules for dashboard rows. Network timing must never move a
 * game backward from a later round (especially Completed) to an older status.
 */
public final class DashboardFreshnessPolicy {

    private DashboardFreshnessPolicy() {}

    public static boolean isProgressRegression(
            @Nullable String currentStatus, @Nullable String incomingStatus) {
        int current = progressRank(currentStatus);
        int incoming = progressRank(incomingStatus);
        return current >= 0 && incoming >= 0 && incoming < current;
    }

    public static int progressRank(@Nullable String status) {
        if (status == null) {
            return -1;
        }
        String normalized = status.trim();
        if ("Completed".equalsIgnoreCase(normalized)) {
            return 11;
        }
        if ("Not Started".equalsIgnoreCase(normalized)) {
            return 0;
        }
        if (normalized.length() > 1
                && (normalized.charAt(0) == 'R' || normalized.charAt(0) == 'r')) {
            try {
                int round = Integer.parseInt(normalized.substring(1));
                return round >= 1 && round <= 10 ? round : -1;
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }
}

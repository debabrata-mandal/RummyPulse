package com.example.rummypulse.data;

/**
 * Controls when monetary settlement values are visible in Game View.
 */
public final class GameAmountVisibilityPolicy {

    private GameAmountVisibilityPolicy() {
    }

    /** A mapped player may show their settlement without revealing unmapped player amounts. */
    public static boolean shouldShowPlayerAmount(
            boolean showLiveAmounts,
            boolean gameCompleted,
            boolean playerIsMapped) {
        return showLiveAmounts || gameCompleted || playerIsMapped;
    }
}

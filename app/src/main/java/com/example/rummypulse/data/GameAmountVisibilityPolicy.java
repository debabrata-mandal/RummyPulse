package com.example.rummypulse.data;

/**
 * Controls when monetary settlement values are visible in Game View.
 */
public final class GameAmountVisibilityPolicy {

    private GameAmountVisibilityPolicy() {
    }

    /**
     * During an in-progress game with live amounts off, only the mapped user sees their own
     * settlement. When live amounts are on or the game is completed, everyone sees all amounts.
     */
    public static boolean shouldShowPlayerAmount(
            boolean showLiveAmounts,
            boolean gameCompleted,
            String playerUserId,
            String viewerUserId) {
        if (showLiveAmounts || gameCompleted) {
            return true;
        }
        return playerUserId != null && !playerUserId.isEmpty()
                && playerUserId.equals(viewerUserId);
    }
}

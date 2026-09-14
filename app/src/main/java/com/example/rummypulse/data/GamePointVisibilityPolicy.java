package com.example.rummypulse.data;

/**
 * Controls when Game Point results are visible in Game View.
 */
public final class GamePointVisibilityPolicy {

    private GamePointVisibilityPolicy() {
    }

    /**
     * During an in-progress game with live results off, only the mapped user sees their own Game
     * Points. When live results are on or the game is completed, everyone sees all Game Points.
     */
    public static boolean shouldShowPlayerGamePoints(
            boolean showLiveGamePoints,
            boolean gameCompleted,
            String playerUserId,
            String viewerUserId) {
        if (showLiveGamePoints || gameCompleted) {
            return true;
        }
        return playerUserId != null && !playerUserId.isEmpty()
                && playerUserId.equals(viewerUserId);
    }
}

package com.example.rummypulse.ui.dashboard;

import com.example.rummypulse.data.PlayerStats;

/**
 * Orderings offered by the player ranking screen.
 *
 * <p>Total net answers "who is up the most", which rewards playing often; the per-game and win-rate
 * orderings answer "who plays best", which a short but strong run can win. Each asks a different
 * question of the same buckets, so the screen lets the player choose rather than picking one.
 */
public enum RankingSort {

    NET_TOTAL,
    NET_PER_GAME,
    WIN_RATE,
    GAMES;

    /**
     * Primary sort key for one bucket, ranked highest first.
     *
     * <p>Dividing by games is safe because only players with at least one game in the period are
     * ranked; the guard is defensive.
     */
    double keyOf(PlayerStats.Bucket bucket) {
        long games = bucket.getGames();
        switch (this) {
            case NET_PER_GAME:
                return games <= 0 ? 0 : bucket.getNetAmount() / games;
            case WIN_RATE:
                return games <= 0 ? 0 : (double) bucket.getWins() / games;
            case GAMES:
                return games;
            case NET_TOTAL:
            default:
                return bucket.getNetAmount();
        }
    }
}

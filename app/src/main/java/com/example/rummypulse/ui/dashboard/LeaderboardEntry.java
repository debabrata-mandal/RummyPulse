package com.example.rummypulse.ui.dashboard;

/** One ranked player row in the dashboard leaderboard. */
public class LeaderboardEntry {

    private final String userId;
    private final String displayName;
    private final double finalGamePoints;
    private final long games;
    private final long wins;
    private final int rank;
    private final boolean currentUser;

    public LeaderboardEntry(
            String userId,
            String displayName,
            double finalGamePoints,
            long games,
            long wins,
            int rank,
            boolean currentUser) {
        this.userId = userId;
        this.displayName = displayName;
        this.finalGamePoints = finalGamePoints;
        this.games = games;
        this.wins = wins;
        this.rank = rank;
        this.currentUser = currentUser;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getFinalGamePoints() {
        return finalGamePoints;
    }

    public long getGames() {
        return games;
    }

    public long getWins() {
        return wins;
    }

    /** Position within the full ranking, 1-based, not within the displayed slice. */
    public int getRank() {
        return rank;
    }

    public boolean isCurrentUser() {
        return currentUser;
    }
}

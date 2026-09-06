package com.example.rummypulse.data;

import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

/**
 * Pre-aggregated performance for one signed-in user, stored at
 * {@code playerStats_v2/{userId}}.
 *
 * <p>All reporting periods the dashboard offers live in this single document, so switching between
 * All Time, this month, last month and this week costs no additional Firestore reads. Buckets are
 * maintained incrementally by {@link PlayerStatsRecorder} when a game completes; nothing is
 * recomputed at read time.
 */
public class PlayerStats {

    /** Months retained in {@link #months} before the oldest are pruned. */
    public static final int MONTH_RETENTION = 24;

    /** Weeks retained in {@link #weeks} before the oldest are pruned. */
    public static final int WEEK_RETENTION = 12;

    private String userId;
    private String displayName;
    private Bucket allTime;
    private Map<String, Bucket> months;
    private Map<String, Bucket> weeks;
    private Timestamp updatedAt;

    public PlayerStats() {
        // Default constructor required for Firestore
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Bucket getAllTime() {
        return allTime;
    }

    public void setAllTime(Bucket allTime) {
        this.allTime = allTime;
    }

    public Map<String, Bucket> getMonths() {
        return months;
    }

    public void setMonths(Map<String, Bucket> months) {
        this.months = months;
    }

    public Map<String, Bucket> getWeeks() {
        return weeks;
    }

    public void setWeeks(Map<String, Bucket> weeks) {
        this.weeks = weeks;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Never null: an absent bucket reads as all zeroes. */
    public Bucket allTimeOrEmpty() {
        return allTime == null ? new Bucket() : allTime;
    }

    public Bucket monthOrEmpty(String monthKey) {
        return bucketOrEmpty(months, monthKey);
    }

    public Bucket weekOrEmpty(String weekKey) {
        return bucketOrEmpty(weeks, weekKey);
    }

    private static Bucket bucketOrEmpty(Map<String, Bucket> source, String key) {
        if (source == null || key == null) {
            return new Bucket();
        }
        Bucket bucket = source.get(key);
        return bucket == null ? new Bucket() : bucket;
    }

    /** Totals for one reporting period. */
    public static class Bucket {
        private long games;
        private long wins;
        private double netAmount;
        private double grossAmount;
        private double contributionPaid;

        public Bucket() {
            // Default constructor required for Firestore
        }

        public Bucket(
                long games,
                long wins,
                double netAmount,
                double grossAmount,
                double contributionPaid) {
            this.games = games;
            this.wins = wins;
            this.netAmount = netAmount;
            this.grossAmount = grossAmount;
            this.contributionPaid = contributionPaid;
        }

        public long getGames() {
            return games;
        }

        public void setGames(long games) {
            this.games = games;
        }

        public long getWins() {
            return wins;
        }

        public void setWins(long wins) {
            this.wins = wins;
        }

        public double getNetAmount() {
            return netAmount;
        }

        public void setNetAmount(double netAmount) {
            this.netAmount = netAmount;
        }

        public double getGrossAmount() {
            return grossAmount;
        }

        public void setGrossAmount(double grossAmount) {
            this.grossAmount = grossAmount;
        }

        public double getContributionPaid() {
            return contributionPaid;
        }

        public void setContributionPaid(double contributionPaid) {
            this.contributionPaid = contributionPaid;
        }

        /** Percentage of completed games with a positive net settlement, 0 when no games recorded. */
        public int winRatePercent() {
            if (games <= 0) {
                return 0;
            }
            return (int) Math.round((wins * 100.0) / games);
        }

        public Map<String, Object> toFirestoreMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("games", games);
            map.put("wins", wins);
            map.put("netAmount", netAmount);
            map.put("grossAmount", grossAmount);
            map.put("contributionPaid", contributionPaid);
            return map;
        }
    }
}

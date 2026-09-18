package com.example.rummypulse.ui.playerranking;

import com.example.rummypulse.data.PlayerStats;
import com.example.rummypulse.ui.dashboard.LeaderboardEntry;
import com.example.rummypulse.ui.dashboard.RankingSort;
import com.example.rummypulse.ui.dashboard.StatsPeriod;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Full ranking profile for one player across every reporting period.
 */
public final class PlayerRankingDetail {

    /** Stats, ranks, and comparisons for one reporting period. */
    public static final class PeriodBreakdown {

        private final StatsPeriod period;
        private final int totalPlayers;
        private final PlayerStats.Bucket bucket;
        private final double maxAbsoluteNet;
        private final double leaderNet;
        private final double periodAverageNet;
        private final Map<RankingSort, Integer> rankBySort;

        public PeriodBreakdown(
                StatsPeriod period,
                int totalPlayers,
                PlayerStats.Bucket bucket,
                double maxAbsoluteNet,
                double leaderNet,
                double periodAverageNet,
                Map<RankingSort, Integer> rankBySort) {
            this.period = period;
            this.totalPlayers = totalPlayers;
            this.bucket = bucket == null ? new PlayerStats.Bucket() : bucket;
            this.maxAbsoluteNet = maxAbsoluteNet;
            this.leaderNet = leaderNet;
            this.periodAverageNet = periodAverageNet;
            this.rankBySort = rankBySort == null
                    ? new EnumMap<>(RankingSort.class)
                    : new EnumMap<>(rankBySort);
        }

        public StatsPeriod getPeriod() {
            return period;
        }

        public int getTotalPlayers() {
            return totalPlayers;
        }

        public PlayerStats.Bucket getBucket() {
            return bucket;
        }

        public double getLeaderNet() {
            return leaderNet;
        }

        public double getPeriodAverageNet() {
            return periodAverageNet;
        }

        /** Earned rank for the chosen ordering, or 0 when the player did not qualify. */
        public int rankFor(RankingSort sort) {
            Integer rank = rankBySort.get(sort == null ? RankingSort.NET_TOTAL : sort);
            return rank == null ? 0 : rank;
        }

        public int getRank() {
            return rankFor(RankingSort.NET_TOTAL);
        }

        public boolean hasGames() {
            return bucket.getGames() > 0;
        }

        public long getLosses() {
            return Math.max(0, bucket.getGames() - bucket.getWins());
        }

        public int winRatePercent() {
            if (bucket.getGames() <= 0) {
                return 0;
            }
            return (int) Math.round((bucket.getWins() * 100.0) / bucket.getGames());
        }

        public double netPerGame() {
            return bucket.getGames() <= 0
                    ? 0
                    : bucket.getFinalGamePoints() / bucket.getGames();
        }

        public double basePerGame() {
            return bucket.getGames() <= 0
                    ? 0
                    : bucket.getBaseGamePoints() / bucket.getGames();
        }

        public double boardPerGame() {
            return bucket.getGames() <= 0
                    ? 0
                    : bucket.getBoardAdjustmentPoints() / bucket.getGames();
        }

        public int boardShareOfBasePercent() {
            double base = bucket.getBaseGamePoints();
            if (Math.abs(base) < 0.5) {
                return 0;
            }
            return (int) Math.round(
                    (bucket.getBoardAdjustmentPoints() / base) * 100.0);
        }

        public int topPercentile() {
            if (totalPlayers <= 0 || getRank() <= 0) {
                return 0;
            }
            return (int) Math.ceil((getRank() * 100.0) / totalPlayers);
        }

        public int playersAhead() {
            int rank = getRank();
            return rank <= 0 ? 0 : rank - 1;
        }

        public int playersBehind() {
            int rank = getRank();
            return rank <= 0 ? 0 : Math.max(0, totalPlayers - rank);
        }

        public double gapToLeader() {
            return leaderNet - bucket.getFinalGamePoints();
        }

        public double gapToAverage() {
            return bucket.getFinalGamePoints() - periodAverageNet;
        }

        public int shareOfLeaderPercent() {
            if (maxAbsoluteNet < 0.5) {
                return 0;
            }
            return (int) Math.round(
                    (Math.abs(bucket.getFinalGamePoints()) / maxAbsoluteNet) * 100.0);
        }

        public float shareOfLeaderFraction() {
            if (maxAbsoluteNet < 0.5) {
                return 0.04f;
            }
            return (float) Math.max(0.04, Math.abs(bucket.getFinalGamePoints()) / maxAbsoluteNet);
        }
    }

    private final LeaderboardEntry listEntry;
    private final StatsPeriod activePeriod;
    private final List<PeriodBreakdown> periods;
    private final boolean showAmounts;

    public PlayerRankingDetail(
            LeaderboardEntry listEntry,
            StatsPeriod activePeriod,
            List<PeriodBreakdown> periods,
            boolean showAmounts) {
        this.listEntry = listEntry;
        this.activePeriod = activePeriod == null ? StatsPeriod.THIS_MONTH : activePeriod;
        this.periods = periods == null ? Collections.emptyList() : periods;
        this.showAmounts = showAmounts;
    }

    public LeaderboardEntry getListEntry() {
        return listEntry;
    }

    public StatsPeriod getActivePeriod() {
        return activePeriod;
    }

    public List<PeriodBreakdown> getPeriods() {
        return periods;
    }

    public boolean isShowAmounts() {
        return showAmounts;
    }

    public PeriodBreakdown breakdownFor(StatsPeriod period) {
        if (period == null) {
            return activeBreakdown();
        }
        for (PeriodBreakdown breakdown : periods) {
            if (breakdown.getPeriod() == period) {
                return breakdown;
            }
        }
        return null;
    }

    public PeriodBreakdown activeBreakdown() {
        return breakdownFor(activePeriod);
    }
}

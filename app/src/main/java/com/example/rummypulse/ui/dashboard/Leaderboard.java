package com.example.rummypulse.ui.dashboard;

import com.example.rummypulse.data.PlayerStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Top and bottom performers for one reporting period, ranked by net amount.
 *
 * <p>Ranking happens on the client from the stats documents already held in memory, so switching
 * period costs no Firestore reads. Only players with at least one game in the period qualify;
 * everyone else would otherwise sit at zero and crowd out the real results.
 */
public class Leaderboard {

    /** Players shown at each end of the table. */
    public static final int SIZE = 2;

    private static final Leaderboard EMPTY =
            new Leaderboard(Collections.emptyList(), Collections.emptyList(), 0);

    private final List<LeaderboardEntry> top;
    private final List<LeaderboardEntry> bottom;
    private final int rankedPlayers;

    private Leaderboard(List<LeaderboardEntry> top, List<LeaderboardEntry> bottom, int rankedPlayers) {
        this.top = top;
        this.bottom = bottom;
        this.rankedPlayers = rankedPlayers;
    }

    public static Leaderboard empty() {
        return EMPTY;
    }

    public List<LeaderboardEntry> getTop() {
        return top;
    }

    public List<LeaderboardEntry> getBottom() {
        return bottom;
    }

    /** How many players qualified, which is not the same as how many are displayed. */
    public int getRankedPlayers() {
        return rankedPlayers;
    }

    public boolean isEmpty() {
        return top.isEmpty() && bottom.isEmpty();
    }

    /**
     * Ranks every qualifying player by net amount and slices the ends.
     *
     * <p>The two slices never share a player: with fewer than {@code 2 * SIZE} qualifiers the
     * bottom list shrinks, and it disappears entirely once the top list covers everyone.
     */
    public static Leaderboard from(
            List<PlayerStats> allStats, StatsPeriod period, String currentUserId) {
        List<LeaderboardEntry> ranked = rankAll(allStats, period, currentUserId);
        if (ranked.isEmpty()) {
            return EMPTY;
        }

        int total = ranked.size();
        int topCount = Math.min(SIZE, total);
        int bottomCount = Math.min(SIZE, total - topCount);

        List<LeaderboardEntry> topEntries = new ArrayList<>(ranked.subList(0, topCount));
        List<LeaderboardEntry> bottomEntries =
                new ArrayList<>(ranked.subList(total - bottomCount, total));

        return new Leaderboard(topEntries, bottomEntries, total);
    }

    /**
     * Every qualifying player for the period, best net first, each carrying its rank in this list.
     *
     * <p>This is the whole table the donut samples the ends of, and what the player ranking screen
     * shows in full. Returns an empty list rather than null when nothing qualifies.
     */
    public static List<LeaderboardEntry> rankAll(
            List<PlayerStats> allStats, StatsPeriod period, String currentUserId) {
        if (allStats == null || allStats.isEmpty() || period == null) {
            return Collections.emptyList();
        }

        List<Ranked> ranked = new ArrayList<>();
        for (PlayerStats stats : allStats) {
            if (stats == null || stats.getUserId() == null) {
                continue;
            }
            PlayerStats.Bucket bucket = period.bucketOf(stats);
            if (bucket.getGames() <= 0) {
                continue;
            }
            ranked.add(new Ranked(stats, bucket));
        }
        if (ranked.isEmpty()) {
            return Collections.emptyList();
        }

        // Highest net first. Name breaks ties so the order is stable across recompositions.
        Collections.sort(ranked, Comparator
                .comparingDouble((Ranked r) -> r.bucket.getNetAmount()).reversed()
                .thenComparing(r -> nameOf(r.stats)));

        List<LeaderboardEntry> entries = new ArrayList<>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            entries.add(toEntry(ranked.get(i), i + 1, currentUserId));
        }
        return entries;
    }

    private static LeaderboardEntry toEntry(Ranked ranked, int rank, String currentUserId) {
        String userId = ranked.stats.getUserId();
        return new LeaderboardEntry(
                userId,
                nameOf(ranked.stats),
                ranked.bucket.getNetAmount(),
                ranked.bucket.getGames(),
                ranked.bucket.getWins(),
                rank,
                userId.equals(currentUserId));
    }

    private static String nameOf(PlayerStats stats) {
        String name = stats.getDisplayName();
        return name == null || name.trim().isEmpty() ? "Player" : name.trim();
    }

    private static final class Ranked {
        final PlayerStats stats;
        final PlayerStats.Bucket bucket;

        Ranked(PlayerStats stats, PlayerStats.Bucket bucket) {
            this.stats = stats;
            this.bucket = bucket;
        }
    }
}

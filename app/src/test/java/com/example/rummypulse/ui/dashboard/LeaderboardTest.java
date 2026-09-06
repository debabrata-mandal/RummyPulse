package com.example.rummypulse.ui.dashboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.rummypulse.data.PlayerStats;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LeaderboardTest {

    private static PlayerStats player(String userId, String name, long games, double net) {
        PlayerStats stats = new PlayerStats();
        stats.setUserId(userId);
        stats.setDisplayName(name);
        stats.setAllTime(new PlayerStats.Bucket(games, 0, net, 0, 0));
        return stats;
    }

    private static List<String> namesOf(List<LeaderboardEntry> entries) {
        List<String> names = new ArrayList<>();
        for (LeaderboardEntry entry : entries) {
            names.add(entry.getDisplayName());
        }
        return names;
    }

    @Test
    public void ranksTopAndBottomByNetAmount() {
        List<PlayerStats> stats = Arrays.asList(
                player("a", "Alice", 5, 300),
                player("b", "Bob", 5, -200),
                player("c", "Cara", 5, 900),
                player("d", "Dan", 5, 50),
                player("e", "Eve", 5, -750),
                player("f", "Finn", 5, 120),
                player("g", "Gus", 5, -40));

        Leaderboard board = Leaderboard.from(stats, StatsPeriod.ALL_TIME, "d");

        assertEquals(Arrays.asList("Cara", "Alice"), namesOf(board.getTop()));
        assertEquals(Arrays.asList("Bob", "Eve"), namesOf(board.getBottom()));
        assertEquals(7, board.getRankedPlayers());
    }

    @Test
    public void ranksAreAbsolutePositionsNotSlicePositions() {
        List<PlayerStats> stats = Arrays.asList(
                player("a", "Alice", 1, 500),
                player("b", "Bob", 1, 400),
                player("c", "Cara", 1, 300),
                player("d", "Dan", 1, 200),
                player("e", "Eve", 1, 100),
                player("f", "Finn", 1, 0));

        Leaderboard board = Leaderboard.from(stats, StatsPeriod.ALL_TIME, null);

        assertEquals(1, board.getTop().get(0).getRank());
        assertEquals(2, board.getTop().get(1).getRank());
        assertEquals(5, board.getBottom().get(0).getRank());
        assertEquals(6, board.getBottom().get(1).getRank());
    }

    @Test
    public void excludesPlayersWithNoGamesInPeriod() {
        List<PlayerStats> stats = Arrays.asList(
                player("a", "Alice", 3, 100),
                player("b", "Bob", 0, 0));

        Leaderboard board = Leaderboard.from(stats, StatsPeriod.ALL_TIME, null);

        assertEquals(Collections.singletonList("Alice"), namesOf(board.getTop()));
        assertEquals(1, board.getRankedPlayers());
    }

    @Test
    public void slicesNeverShareAPlayer() {
        // Three qualifiers: the top takes two, so only one remains for the bottom.
        List<PlayerStats> stats = Arrays.asList(
                player("a", "Alice", 1, 500),
                player("b", "Bob", 1, 400),
                player("c", "Cara", 1, 300));

        Leaderboard board = Leaderboard.from(stats, StatsPeriod.ALL_TIME, null);

        assertEquals(2, board.getTop().size());
        assertEquals(1, board.getBottom().size());
        for (LeaderboardEntry top : board.getTop()) {
            for (LeaderboardEntry bottom : board.getBottom()) {
                assertFalse(top.getUserId().equals(bottom.getUserId()));
            }
        }
    }

    @Test
    public void bottomIsEmptyWhenTopCoversEveryone() {
        List<PlayerStats> stats = Arrays.asList(
                player("a", "Alice", 1, 300),
                player("b", "Bob", 1, 200));

        Leaderboard board = Leaderboard.from(stats, StatsPeriod.ALL_TIME, null);

        assertEquals(2, board.getTop().size());
        assertTrue(board.getBottom().isEmpty());
    }

    @Test
    public void flagsTheSignedInUser() {
        List<PlayerStats> stats = Arrays.asList(
                player("a", "Alice", 1, 300),
                player("b", "Bob", 1, 200));

        Leaderboard board = Leaderboard.from(stats, StatsPeriod.ALL_TIME, "b");

        assertFalse(board.getTop().get(0).isCurrentUser());
        assertTrue(board.getTop().get(1).isCurrentUser());
    }

    @Test
    public void tiesBreakByNameSoOrderIsStable() {
        List<PlayerStats> stats = Arrays.asList(
                player("b", "Bob", 1, 100),
                player("a", "Alice", 1, 100));

        Leaderboard board = Leaderboard.from(stats, StatsPeriod.ALL_TIME, null);

        assertEquals(Arrays.asList("Alice", "Bob"), namesOf(board.getTop()));
    }

    @Test
    public void emptyInputsProduceAnEmptyBoard() {
        assertTrue(Leaderboard.from(null, StatsPeriod.ALL_TIME, null).isEmpty());
        assertTrue(Leaderboard.from(new ArrayList<>(), StatsPeriod.ALL_TIME, null).isEmpty());
        assertTrue(Leaderboard.from(
                Collections.singletonList(player("a", "Alice", 1, 10)), null, null).isEmpty());
    }

    @Test
    public void periodWithNoDataRanksNobody() {
        // Stats carry an all-time bucket only, so a weekly view has nothing to rank.
        List<PlayerStats> stats = Collections.singletonList(player("a", "Alice", 4, 250));

        assertTrue(Leaderboard.from(stats, StatsPeriod.THIS_WEEK, null).isEmpty());
    }

    @Test
    public void rankAllKeepsTheMiddleThatTheDonutDiscards() {
        List<PlayerStats> stats = Arrays.asList(
                player("a", "Alice", 5, 300),
                player("b", "Bob", 5, -200),
                player("c", "Cara", 5, 900),
                player("d", "Dan", 5, 50),
                player("e", "Eve", 5, -750));

        List<LeaderboardEntry> ranked = Leaderboard.rankAll(stats, StatsPeriod.ALL_TIME, null);

        assertEquals(Arrays.asList("Cara", "Alice", "Dan", "Bob", "Eve"), namesOf(ranked));
        for (int i = 0; i < ranked.size(); i++) {
            assertEquals(i + 1, ranked.get(i).getRank());
        }
    }

    @Test
    public void rankAllExcludesPlayersWithNoGamesInPeriod() {
        List<PlayerStats> stats = Arrays.asList(
                player("a", "Alice", 3, 100),
                player("b", "Bob", 0, 0));

        assertEquals(Collections.singletonList("Alice"),
                namesOf(Leaderboard.rankAll(stats, StatsPeriod.ALL_TIME, null)));
    }

    @Test
    public void rankAllTiesBreakByName() {
        List<PlayerStats> stats = Arrays.asList(
                player("b", "Bob", 1, 100),
                player("a", "Alice", 1, 100));

        assertEquals(Arrays.asList("Alice", "Bob"),
                namesOf(Leaderboard.rankAll(stats, StatsPeriod.ALL_TIME, null)));
    }

    @Test
    public void rankAllFlagsTheSignedInUser() {
        List<PlayerStats> stats = Arrays.asList(
                player("a", "Alice", 1, 300),
                player("b", "Bob", 1, 200));

        List<LeaderboardEntry> ranked = Leaderboard.rankAll(stats, StatsPeriod.ALL_TIME, "b");

        assertFalse(ranked.get(0).isCurrentUser());
        assertTrue(ranked.get(1).isCurrentUser());
    }

    @Test
    public void rankAllReturnsEmptyRatherThanNull() {
        assertTrue(Leaderboard.rankAll(null, StatsPeriod.ALL_TIME, null).isEmpty());
        assertTrue(Leaderboard.rankAll(new ArrayList<>(), StatsPeriod.ALL_TIME, null).isEmpty());
        assertTrue(Leaderboard.rankAll(
                Collections.singletonList(player("a", "Alice", 1, 10)), null, null).isEmpty());
    }
}

package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlayerStatsRecorderTest {

    private static final Date APPROVAL_TIME = new Date(1789344000000L);

    @Test
    public void approvedGameCreatesExpectedDeltasOnlyForLinkedPlayers() {
        GameData game = game(
                player("A", "user-a", 10),
                player("B", "user-b", 20),
                player("Guest", null, 30));

        Map<String, PlayerStatsRecorder.PeriodDelta> deltas =
                PlayerStatsRecorder.deltasForApproval(game, APPROVAL_TIME);

        assertEquals(2, deltas.size());
        assertFalse(deltas.containsKey(null));
        assertDelta(deltas.get("user-a"), 1, 1, 54, 60, 6);
        assertDelta(deltas.get("user-b"), 1, 0, 0, 0, 0);
        assertEquals(PlayerStatsKeys.monthKey(APPROVAL_TIME),
                deltas.get("user-a").monthKey);
        assertEquals(PlayerStatsKeys.weekKey(APPROVAL_TIME),
                deltas.get("user-a").weekKey);
    }

    @Test
    public void batchApprovalKeepsEveryGameForSameUser() {
        Map<String, List<PlayerStatsRecorder.PeriodDelta>> batch = new LinkedHashMap<>();
        PlayerStatsRecorder.appendApprovalDeltas(batch,
                PlayerStatsRecorder.deltasForApproval(game(
                        player("A", "user-a", 10),
                        player("B", "user-b", 20),
                        player("Guest", null, 30)), APPROVAL_TIME));
        PlayerStatsRecorder.appendApprovalDeltas(batch,
                PlayerStatsRecorder.deltasForApproval(game(
                        player("A", "user-a", 30),
                        player("C", "user-c", 20),
                        player("Guest", null, 10)), APPROVAL_TIME));

        assertEquals(2, batch.get("user-a").size());
        Map<String, Object> document = PlayerStatsRecorder.buildStatsDocument(
                null, "user-a", batch.get("user-a"));
        @SuppressWarnings("unchecked")
        Map<String, Object> allTime = (Map<String, Object>) document.get("allTime");
        assertEquals(2L, allTime.get("games"));
        assertEquals(1L, allTime.get("wins"));
        assertEquals(-6.0, (Double) allTime.get("netAmount"), 0.001);
    }

    @Test
    public void approvalAddsToExistingStatisticsWithoutChangingPriorPeriods() {
        PlayerStats existing = new PlayerStats();
        existing.setDisplayName("Old name");
        existing.setAllTime(new PlayerStats.Bucket(3, 1, 25, 30, 5));
        Map<String, PlayerStats.Bucket> priorMonths = new LinkedHashMap<>();
        priorMonths.put("2026-08", new PlayerStats.Bucket(3, 1, 25, 30, 5));
        existing.setMonths(priorMonths);

        PlayerStatsRecorder.PeriodDelta delta =
                PlayerStatsRecorder.deltasForApproval(game(
                        player("Updated name", "user-a", 10),
                        player("B", "user-b", 20),
                        player("Guest", null, 30)), APPROVAL_TIME).get("user-a");
        Map<String, Object> document = PlayerStatsRecorder.buildStatsDocument(
                existing, "user-a", Arrays.asList(delta));

        @SuppressWarnings("unchecked")
        Map<String, Object> allTime = (Map<String, Object>) document.get("allTime");
        @SuppressWarnings("unchecked")
        Map<String, Object> months = (Map<String, Object>) document.get("months");
        assertEquals(4L, allTime.get("games"));
        assertEquals(2L, allTime.get("wins"));
        assertEquals(79.0, (Double) allTime.get("netAmount"), 0.001);
        assertEquals(new PlayerStats.Bucket(3, 1, 25, 30, 5).toFirestoreMap(),
                months.get("2026-08"));
        assertEquals("Updated name", document.get("displayName"));
    }

    @Test
    public void duplicateMappedUserInOneGameIsCountedOnce() {
        GameData game = game(
                player("A1", "user-a", 10),
                player("A2", "user-a", 20),
                player("B", "user-b", 30));

        Map<String, PlayerStatsRecorder.PeriodDelta> deltas =
                PlayerStatsRecorder.deltasForApproval(game, APPROVAL_TIME);

        assertEquals(2, deltas.size());
        assertEquals("A1", deltas.get("user-a").displayName);
        assertEquals(1L, deltas.get("user-a").bucket.getGames());
    }

    @Test
    public void approvedGameWithoutLinkedPlayersProducesNoStatsWrites() {
        GameData game = game(
                player("A", null, 10),
                player("B", null, 20));

        assertTrue(PlayerStatsRecorder.deltasForApproval(game, APPROVAL_TIME).isEmpty());
    }

    private static GameData game(Player... players) {
        GameData data = new GameData();
        data.setNumPlayers(players.length);
        data.setPointValue(2.0);
        data.setGstPercent(10.0);
        data.setGameStatus("Completed");
        data.setPlayers(new ArrayList<>(Arrays.asList(players)));
        return data;
    }

    private static Player player(String name, String userId, int totalScore) {
        Player player = new Player(name, Arrays.asList(totalScore), totalScore);
        player.setUserId(userId);
        return player;
    }

    private static void assertDelta(
            PlayerStatsRecorder.PeriodDelta delta,
            long games,
            long wins,
            double finalGamePoints,
            double baseGamePoints,
            double boardAdjustmentPoints) {
        assertEquals(games, delta.bucket.getGames());
        assertEquals(wins, delta.bucket.getWins());
        assertEquals(finalGamePoints, delta.bucket.getNetAmount(), 0.001);
        assertEquals(baseGamePoints, delta.bucket.getGrossAmount(), 0.001);
        assertEquals(boardAdjustmentPoints, delta.bucket.getContributionPaid(), 0.001);
    }
}

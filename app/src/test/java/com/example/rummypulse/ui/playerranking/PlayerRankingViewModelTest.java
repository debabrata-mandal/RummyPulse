package com.example.rummypulse.ui.playerranking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.rummypulse.ui.dashboard.LeaderboardEntry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlayerRankingViewModelTest {

    private static LeaderboardEntry entry(String userId, int rank, boolean currentUser) {
        return new LeaderboardEntry(userId, userId, 100 - rank, 5, 2, rank, currentUser);
    }

    private static List<String> userIdsOf(List<LeaderboardEntry> entries) {
        List<String> ids = new ArrayList<>();
        for (LeaderboardEntry entry : entries) {
            ids.add(entry.getUserId());
        }
        return ids;
    }

    @Test
    public void pinCurrentUser_movesCurrentUserToFront() {
        List<LeaderboardEntry> ranked = Arrays.asList(
                entry("a", 1, false),
                entry("b", 2, false),
                entry("c", 3, true),
                entry("d", 4, false));

        assertEquals(
                Arrays.asList("c", "a", "b", "d"),
                userIdsOf(PlayerRankingViewModel.pinCurrentUser(ranked)));
    }

    @Test
    public void pinCurrentUser_preservesEarnedRanks() {
        List<LeaderboardEntry> ranked = Arrays.asList(
                entry("a", 1, false),
                entry("b", 2, false),
                entry("c", 3, true));

        List<LeaderboardEntry> pinned = PlayerRankingViewModel.pinCurrentUser(ranked);

        assertEquals(3, pinned.get(0).getRank());
        assertEquals(1, pinned.get(1).getRank());
        assertEquals(2, pinned.get(2).getRank());
    }

    @Test
    public void pinCurrentUser_currentUserAlreadyFirst_returnsSameList() {
        List<LeaderboardEntry> ranked = Arrays.asList(
                entry("a", 1, true),
                entry("b", 2, false));

        assertSame(ranked, PlayerRankingViewModel.pinCurrentUser(ranked));
    }

    @Test
    public void pinCurrentUser_lastPlaceCurrentUser_movesToFront() {
        List<LeaderboardEntry> ranked = Arrays.asList(
                entry("a", 1, false),
                entry("b", 2, false),
                entry("c", 3, true));

        assertEquals(
                Arrays.asList("c", "a", "b"),
                userIdsOf(PlayerRankingViewModel.pinCurrentUser(ranked)));
    }

    @Test
    public void pinCurrentUser_noCurrentUserInPeriod_leavesOrderAlone() {
        List<LeaderboardEntry> ranked = Arrays.asList(
                entry("a", 1, false),
                entry("b", 2, false));

        assertSame(ranked, PlayerRankingViewModel.pinCurrentUser(ranked));
    }

    @Test
    public void pinCurrentUser_emptyRanking_isHandled() {
        assertTrue(PlayerRankingViewModel.pinCurrentUser(Collections.emptyList()).isEmpty());
    }
}

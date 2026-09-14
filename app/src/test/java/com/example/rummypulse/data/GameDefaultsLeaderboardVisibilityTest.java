package com.example.rummypulse.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Amounts must require both leaderboard switches, because the admin UI disables the amounts
 * switch while the leaderboard switch is off and can therefore leave it stranded at true.
 */
public class GameDefaultsLeaderboardVisibilityTest {

    private static GameDefaults defaults(Boolean leaderboard, Boolean amounts) {
        GameDefaults raw = new GameDefaults();
        raw.setShowDashboardLeaderboard(leaderboard);
        raw.setShowDashboardLeaderboardGamePoints(amounts);
        return GameDefaults.resolvedFromFirestoreBean(raw);
    }

    @Test
    public void amountsVisibleWhenBothSwitchesAreOn() {
        assertTrue(defaults(true, true).isLeaderboardGamePointsVisible());
    }

    @Test
    public void amountsHiddenWhenOnlyAmountsSwitchIsOff() {
        assertFalse(defaults(true, false).isLeaderboardGamePointsVisible());
    }

    @Test
    public void amountsHiddenWhenLeaderboardIsOffEvenIfAmountsSwitchSaysOn() {
        assertFalse(defaults(false, true).isLeaderboardGamePointsVisible());
    }

    @Test
    public void amountsHiddenWhenBothSwitchesAreOff() {
        assertFalse(defaults(false, false).isLeaderboardGamePointsVisible());
    }

    @Test
    public void unsetFlagsFallBackToVisible() {
        assertTrue(GameDefaults.resolvedFromFirestoreBean(null).isLeaderboardGamePointsVisible());
        assertTrue(defaults(null, null).isLeaderboardGamePointsVisible());
    }
}

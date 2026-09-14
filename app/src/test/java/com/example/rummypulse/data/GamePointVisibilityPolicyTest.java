package com.example.rummypulse.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GamePointVisibilityPolicyTest {

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";

    @Test
    public void inProgress_privacyOn_onlyViewerOwnMappedAmountVisible() {
        assertTrue(GamePointVisibilityPolicy.shouldShowPlayerGamePoints(
                false, false, USER_A, USER_A));
        assertFalse(GamePointVisibilityPolicy.shouldShowPlayerGamePoints(
                false, false, USER_A, USER_B));
        assertFalse(GamePointVisibilityPolicy.shouldShowPlayerGamePoints(
                false, false, null, USER_A));
    }

    @Test
    public void liveAmountsEnabled_showsEveryPlayerAmount() {
        assertTrue(GamePointVisibilityPolicy.shouldShowPlayerGamePoints(
                true, false, null, USER_A));
        assertTrue(GamePointVisibilityPolicy.shouldShowPlayerGamePoints(
                true, false, USER_A, USER_B));
    }

    @Test
    public void gameCompleted_showsEveryPlayerAmount() {
        assertTrue(GamePointVisibilityPolicy.shouldShowPlayerGamePoints(
                false, true, null, USER_A));
        assertTrue(GamePointVisibilityPolicy.shouldShowPlayerGamePoints(
                false, true, USER_A, USER_B));
    }
}

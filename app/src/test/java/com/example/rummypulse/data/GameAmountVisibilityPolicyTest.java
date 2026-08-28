package com.example.rummypulse.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GameAmountVisibilityPolicyTest {

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";

    @Test
    public void inProgress_privacyOn_onlyViewerOwnMappedAmountVisible() {
        assertTrue(GameAmountVisibilityPolicy.shouldShowPlayerAmount(
                false, false, USER_A, USER_A));
        assertFalse(GameAmountVisibilityPolicy.shouldShowPlayerAmount(
                false, false, USER_A, USER_B));
        assertFalse(GameAmountVisibilityPolicy.shouldShowPlayerAmount(
                false, false, null, USER_A));
    }

    @Test
    public void liveAmountsEnabled_showsEveryPlayerAmount() {
        assertTrue(GameAmountVisibilityPolicy.shouldShowPlayerAmount(
                true, false, null, USER_A));
        assertTrue(GameAmountVisibilityPolicy.shouldShowPlayerAmount(
                true, false, USER_A, USER_B));
    }

    @Test
    public void gameCompleted_showsEveryPlayerAmount() {
        assertTrue(GameAmountVisibilityPolicy.shouldShowPlayerAmount(
                false, true, null, USER_A));
        assertTrue(GameAmountVisibilityPolicy.shouldShowPlayerAmount(
                false, true, USER_A, USER_B));
    }
}

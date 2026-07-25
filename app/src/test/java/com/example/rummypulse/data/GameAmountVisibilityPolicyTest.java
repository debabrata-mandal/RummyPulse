package com.example.rummypulse.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GameAmountVisibilityPolicyTest {

    @Test
    public void liveAmountsDisabled_onlyMappedPlayersShowAmount() {
        assertTrue(GameAmountVisibilityPolicy.shouldShowPlayerAmount(false, false, true));
        assertFalse(GameAmountVisibilityPolicy.shouldShowPlayerAmount(false, false, false));
    }

    @Test
    public void liveAmountsEnabled_orCompleted_showsEveryPlayerAmount() {
        assertTrue(GameAmountVisibilityPolicy.shouldShowPlayerAmount(true, false, false));
        assertTrue(GameAmountVisibilityPolicy.shouldShowPlayerAmount(false, true, false));
    }
}

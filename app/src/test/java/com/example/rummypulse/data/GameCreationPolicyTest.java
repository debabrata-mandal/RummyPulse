package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GameCreationPolicyTest {

    @Test
    public void offlineSubmissionIsBlocked() {
        assertFalse(GameCreationPolicy.canStart(false, false));
    }

    @Test
    public void duplicateSubmissionIsBlocked() {
        assertFalse(GameCreationPolicy.canStart(true, true));
    }

    @Test
    public void onlineIdleSubmissionCanStart() {
        assertTrue(GameCreationPolicy.canStart(true, false));
    }

    @Test
    public void slowNetworkNoticeIsBoundedToFifteenSeconds() {
        assertEquals(15_000L, GameCreationPolicy.SLOW_NETWORK_NOTICE_MS);
    }
}

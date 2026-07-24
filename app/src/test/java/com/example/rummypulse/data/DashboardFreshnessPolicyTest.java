package com.example.rummypulse.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DashboardFreshnessPolicyTest {

    @Test
    public void completedCannotRegressToRoundOne() {
        assertTrue(DashboardFreshnessPolicy.isProgressRegression("Completed", "R1"));
    }

    @Test
    public void laterRoundCannotRegressToEarlierRound() {
        assertTrue(DashboardFreshnessPolicy.isProgressRegression("R8", "R3"));
    }

    @Test
    public void completedAdvancesFromAnyRound() {
        assertFalse(DashboardFreshnessPolicy.isProgressRegression("R10", "Completed"));
    }

    @Test
    public void sameOrLaterRoundIsAccepted() {
        assertFalse(DashboardFreshnessPolicy.isProgressRegression("R4", "R4"));
        assertFalse(DashboardFreshnessPolicy.isProgressRegression("R4", "R5"));
    }

    @Test
    public void administrativeStatusesAreNotComparedAsProgress() {
        assertFalse(DashboardFreshnessPolicy.isProgressRegression("Approved", "R1"));
    }
}

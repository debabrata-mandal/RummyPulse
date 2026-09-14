package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class GamePointsCalculatorTest {

    @Test
    public void calculate_preservesExistingRoundingAndBalancesBoard() {
        GamePointsCalculator.Result result = GamePointsCalculator.calculate(
                Arrays.asList(10, 20, 30),
                2.0,
                10.0,
                3);

        assertPlayer(result, 0, 10, 60, 6, 54);
        assertPlayer(result, 1, 20, 0, 0, 0);
        assertPlayer(result, 2, 30, -60, 0, -60);
        assertEquals(6, result.getBoardPoints(), 0.001);
        assertEquals(6, result.getBoardResult().getFinalGamePoints(), 0.001);
        assertEquals(-6, result.getFinalGamePointsTotal(), 0.001);
        assertTrue(result.isBalanced());
    }

    @Test
    public void calculate_supportsMultiplePositivePlayers() {
        GamePointsCalculator.Result result = GamePointsCalculator.calculate(
                Arrays.asList(0, 0, 10, 10),
                1.0,
                10.0,
                4);

        assertPlayer(result, 0, 0, 20, 2, 18);
        assertPlayer(result, 1, 0, 20, 2, 18);
        assertPlayer(result, 2, 10, -20, 0, -20);
        assertPlayer(result, 3, 10, -20, 0, -20);
        assertEquals(4, result.getBoardPoints(), 0.001);
        assertTrue(result.isBalanced());
    }

    @Test
    public void calculate_decimalFactorUsesLegacyWholePointRounding() {
        GamePointsCalculator.Result result = GamePointsCalculator.calculate(
                Arrays.asList(0, 5, 10),
                0.15,
                25.0,
                3);

        assertPlayer(result, 0, 0, 2, 1, 1);
        assertPlayer(result, 1, 5, 0, 0, 0);
        assertPlayer(result, 2, 10, -2, 0, -2);
        assertEquals(1, result.getBoardPoints(), 0.001);
        assertTrue(result.isBalanced());
    }

    @Test
    public void calculate_zeroAdjustmentLeavesBasePointsUnchanged() {
        GamePointsCalculator.Result result = GamePointsCalculator.calculate(
                Arrays.asList(10, 20, 30),
                2.0,
                0,
                3);

        assertPlayer(result, 0, 10, 60, 0, 60);
        assertEquals(0, result.getBoardPoints(), 0.001);
        assertTrue(result.isBalanced());
    }

    @Test
    public void calculate_nonPositiveDeclaredCountFallsBackToActualCount() {
        GamePointsCalculator.Result result = GamePointsCalculator.calculate(
                Arrays.asList(10, 20, 30),
                2.0,
                10.0,
                0);

        assertPlayer(result, 0, 10, 60, 6, 54);
        assertTrue(result.isBalanced());
    }

    @Test
    public void calculate_emptyScoresReturnsEmptyBalancedResult() {
        GamePointsCalculator.Result result = GamePointsCalculator.calculate(
                Collections.emptyList(),
                2.0,
                10.0,
                0);

        assertTrue(result.getPlayerResults().isEmpty());
        assertEquals(0, result.getBoardPoints(), 0.001);
        assertEquals(0, result.getFinalGamePointsTotal(), 0.001);
        assertTrue(result.isBalanced());
    }

    @Test
    public void calculate_declaredCountMismatchIsObservableWithoutChangingLegacyMath() {
        GamePointsCalculator.Result result = GamePointsCalculator.calculate(
                Arrays.asList(10, 20, 30),
                2.0,
                10.0,
                4);

        assertFalse(result.isBalanced());
    }

    private static void assertPlayer(
            GamePointsCalculator.Result result,
            int index,
            int playerScore,
            double baseGamePoints,
            double boardAdjustmentPoints,
            double finalGamePoints) {
        GamePointsCalculator.PlayerGamePoints player = result.getPlayerResults().get(index);
        assertEquals(playerScore, player.getPlayerScore());
        assertEquals(baseGamePoints, player.getBaseGamePoints(), 0.001);
        assertEquals(boardAdjustmentPoints, player.getBoardAdjustmentPoints(), 0.001);
        assertEquals(finalGamePoints, player.getFinalGamePoints(), 0.001);
    }
}

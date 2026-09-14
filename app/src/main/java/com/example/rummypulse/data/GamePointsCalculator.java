package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure calculator for RummyPulse's closed, non-monetary Game Points system.
 *
 * <p>The calculator intentionally preserves the rounding used by the existing game calculation:
 * each player's base result is rounded first, followed by the positive player's Board adjustment.
 * The Board is a synthetic result row and has no identity or persistent balance.</p>
 */
public final class GamePointsCalculator {

    private GamePointsCalculator() {
    }

    public static Result calculate(
            List<Integer> playerScores,
            double gamePointFactor,
            double boardAdjustmentPercent,
            int declaredNumPlayers) {
        if (playerScores == null || playerScores.isEmpty()) {
            return Result.empty();
        }

        int totalScore = 0;
        for (Integer score : playerScores) {
            totalScore += normalizedScore(score);
        }

        int numPlayers = effectivePlayerCount(declaredNumPlayers, playerScores.size());
        List<PlayerGamePoints> playerResults = new ArrayList<>(playerScores.size());
        double boardPoints = 0;
        double finalGamePointsTotal = 0;

        for (Integer score : playerScores) {
            PlayerGamePoints result = calculatePlayerFromTotal(
                    totalScore,
                    normalizedScore(score),
                    numPlayers,
                    gamePointFactor,
                    boardAdjustmentPercent);
            playerResults.add(result);
            boardPoints += result.getBoardAdjustmentPoints();
            finalGamePointsTotal += result.getFinalGamePoints();
        }

        return new Result(playerResults, boardPoints, finalGamePointsTotal);
    }

    /**
     * Calculates one player's result against the supplied score list.
     *
     * <p>This overload supports existing callers that calculate one selected player's result. Use
     * {@link #calculate(List, double, double, int)} when the Board row or balance invariant is
     * required.</p>
     */
    public static PlayerGamePoints calculatePlayer(
            List<Integer> allPlayerScores,
            int playerScore,
            double gamePointFactor,
            double boardAdjustmentPercent,
            int declaredNumPlayers) {
        if (allPlayerScores == null || allPlayerScores.isEmpty()) {
            return PlayerGamePoints.zero();
        }

        int totalScore = 0;
        for (Integer score : allPlayerScores) {
            totalScore += normalizedScore(score);
        }

        return calculatePlayerFromTotal(
                totalScore,
                normalizedScore(playerScore),
                effectivePlayerCount(declaredNumPlayers, allPlayerScores.size()),
                gamePointFactor,
                boardAdjustmentPercent);
    }

    private static PlayerGamePoints calculatePlayerFromTotal(
            int totalScore,
            int playerScore,
            int numPlayers,
            double gamePointFactor,
            double boardAdjustmentPercent) {
        double baseGamePoints = Math.round(
                (totalScore - (double) playerScore * numPlayers) * gamePointFactor);
        double boardAdjustmentPoints = 0;
        if (baseGamePoints > 0) {
            boardAdjustmentPoints = Math.round(
                    (baseGamePoints * boardAdjustmentPercent) / 100.0);
        }
        double finalGamePoints = baseGamePoints - boardAdjustmentPoints;
        return new PlayerGamePoints(
                playerScore,
                baseGamePoints,
                boardAdjustmentPoints,
                finalGamePoints);
    }

    private static int effectivePlayerCount(int declaredNumPlayers, int actualNumPlayers) {
        return declaredNumPlayers > 0 ? declaredNumPlayers : actualNumPlayers;
    }

    private static int normalizedScore(Integer score) {
        return score != null && score > 0 ? score : 0;
    }

    public static final class Result {
        private final List<PlayerGamePoints> playerResults;
        private final BoardGamePoints boardResult;
        private final double finalGamePointsTotal;

        private Result(
                List<PlayerGamePoints> playerResults,
                double boardPoints,
                double finalGamePointsTotal) {
            this.playerResults = Collections.unmodifiableList(new ArrayList<>(playerResults));
            this.boardResult = new BoardGamePoints(boardPoints);
            this.finalGamePointsTotal = finalGamePointsTotal;
        }

        private static Result empty() {
            return new Result(Collections.emptyList(), 0, 0);
        }

        public List<PlayerGamePoints> getPlayerResults() {
            return playerResults;
        }

        public double getBoardPoints() {
            return boardResult.getFinalGamePoints();
        }

        /** Returns the synthetic Board row that follows the player rows in result displays. */
        public BoardGamePoints getBoardResult() {
            return boardResult;
        }

        public double getFinalGamePointsTotal() {
            return finalGamePointsTotal;
        }

        public boolean isBalanced() {
            return Double.compare(finalGamePointsTotal + getBoardPoints(), 0.0) == 0;
        }
    }

    public static final class BoardGamePoints {
        private final double finalGamePoints;

        private BoardGamePoints(double finalGamePoints) {
            this.finalGamePoints = finalGamePoints;
        }

        public double getFinalGamePoints() {
            return finalGamePoints;
        }
    }

    public static final class PlayerGamePoints {
        private final int playerScore;
        private final double baseGamePoints;
        private final double boardAdjustmentPoints;
        private final double finalGamePoints;

        private PlayerGamePoints(
                int playerScore,
                double baseGamePoints,
                double boardAdjustmentPoints,
                double finalGamePoints) {
            this.playerScore = playerScore;
            this.baseGamePoints = baseGamePoints;
            this.boardAdjustmentPoints = boardAdjustmentPoints;
            this.finalGamePoints = finalGamePoints;
        }

        private static PlayerGamePoints zero() {
            return new PlayerGamePoints(0, 0, 0, 0);
        }

        public int getPlayerScore() {
            return playerScore;
        }

        public double getBaseGamePoints() {
            return baseGamePoints;
        }

        public double getBoardAdjustmentPoints() {
            return boardAdjustmentPoints;
        }

        public double getFinalGamePoints() {
            return finalGamePoints;
        }
    }
}

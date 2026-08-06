package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.List;

/** Builds the initial score row for a player joining after a game has started. */
public final class MidGameJoinScoreCalculator {
    private static final int ROUND_COUNT = 10;

    private MidGameJoinScoreCalculator() {
    }

    public static List<Integer> buildScores(
            GameData gameData, int activeRound, int increment) {
        int lastCompletedRound = activeRound == 0 ? ROUND_COUNT : activeRound - 1;
        int placeholderCount = activeRound == 0 ? 9 : Math.max(0, activeRound - 2);
        int highestCompletedTotal = highestTotalThroughRound(
                gameData, lastCompletedRound);

        Integer existingSameRoundBackfill = findSameRoundJoinBackfill(
                gameData, activeRound, lastCompletedRound, increment);
        if (existingSameRoundBackfill != null) {
            gameData.setMidGameJoinActiveRound(activeRound);
            gameData.setMidGameJoinBackfillScore(existingSameRoundBackfill);
            return scoreRow(activeRound, existingSameRoundBackfill);
        }

        Integer storedRound = gameData.getMidGameJoinActiveRound();
        Integer storedScore = gameData.getMidGameJoinBackfillScore();
        boolean cachedScoreIsValid = storedRound != null
                && storedScore != null
                && storedRound == activeRound
                && storedScore + placeholderCount >= highestCompletedTotal;

        int backfillScore;
        if (cachedScoreIsValid) {
            backfillScore = storedScore;
        } else {
            backfillScore = Math.max(
                    1, highestCompletedTotal + increment - placeholderCount);
            gameData.setMidGameJoinActiveRound(activeRound);
            gameData.setMidGameJoinBackfillScore(backfillScore);
        }

        return scoreRow(activeRound, backfillScore);
    }

    private static List<Integer> scoreRow(int activeRound, int backfillScore) {
        List<Integer> scores = new ArrayList<>();
        for (int round = 0; round < ROUND_COUNT; round++) {
            scores.add(-1);
        }
        if (activeRound == 0) {
            for (int index = 0; index < 9; index++) {
                scores.set(index, 1);
            }
            scores.set(9, backfillScore);
            return scores;
        }
        if (activeRound == 1) {
            scores.set(0, backfillScore);
            return scores;
        }
        for (int round = 1; round <= activeRound - 2; round++) {
            scores.set(round - 1, 1);
        }
        scores.set(activeRound - 2, backfillScore);
        return scores;
    }

    private static Integer findSameRoundJoinBackfill(
            GameData gameData, int activeRound, int lastCompletedRound,
            int increment) {
        if (gameData == null || gameData.getPlayers() == null) {
            return null;
        }
        int backfillRound = activeRound == 0 ? ROUND_COUNT : Math.max(1, activeRound - 1);
        for (Player player : gameData.getPlayers()) {
            if (player != null
                    && Integer.valueOf(activeRound).equals(player.getMidGameJoinActiveRound())) {
                Integer score = scoreAt(player, backfillRound);
                if (score != null && score > 0) {
                    return score;
                }
            }
        }

        // Compatibility for players created before the per-player join marker existed. Their
        // placeholder-shaped row and total identify the already-calculated same-round baseline.
        int highestEstablishedTotal = 0;
        for (Player player : gameData.getPlayers()) {
            if (!hasLegacyJoinShape(player, backfillRound)) {
                highestEstablishedTotal = Math.max(
                        highestEstablishedTotal,
                        positiveTotalThroughRound(player, lastCompletedRound));
            }
        }
        int expectedJoinedTotal = highestEstablishedTotal + increment;
        for (Player candidate : gameData.getPlayers()) {
            if (hasLegacyJoinShape(candidate, backfillRound)
                    && positiveTotalThroughRound(candidate, backfillRound)
                    == expectedJoinedTotal) {
                return scoreAt(candidate, backfillRound);
            }
        }
        return null;
    }

    private static boolean hasLegacyJoinShape(Player player, int backfillRound) {
        if (player == null || player.getScores() == null
                || player.getScores().size() < backfillRound) {
            return false;
        }
        for (int index = 0; index < backfillRound - 1; index++) {
            if (!Integer.valueOf(1).equals(player.getScores().get(index))) {
                return false;
            }
        }
        Integer backfill = player.getScores().get(backfillRound - 1);
        if (backfill == null || backfill <= 0) {
            return false;
        }
        for (int index = backfillRound; index < player.getScores().size(); index++) {
            Integer score = player.getScores().get(index);
            if (score != null && score >= 0) {
                return false;
            }
        }
        return true;
    }

    private static int positiveTotalThroughRound(Player player, int lastRoundInclusive) {
        if (player == null || player.getScores() == null) {
            return 0;
        }
        int total = 0;
        int count = Math.min(lastRoundInclusive, player.getScores().size());
        for (int index = 0; index < count; index++) {
            Integer score = player.getScores().get(index);
            if (score != null && score > 0) {
                total += score;
            }
        }
        return total;
    }

    private static Integer scoreAt(Player player, int round1Based) {
        if (player == null || player.getScores() == null
                || player.getScores().size() < round1Based) {
            return null;
        }
        return player.getScores().get(round1Based - 1);
    }

    private static int highestTotalThroughRound(
            GameData gameData, int lastRoundInclusive) {
        if (gameData == null || gameData.getPlayers() == null) {
            return 0;
        }
        int highest = 0;
        for (Player player : gameData.getPlayers()) {
            highest = Math.max(
                    highest, positiveTotalThroughRound(player, lastRoundInclusive));
        }
        return highest;
    }
}

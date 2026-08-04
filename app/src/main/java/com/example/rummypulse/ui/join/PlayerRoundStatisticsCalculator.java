package com.example.rummypulse.ui.join;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.Player;

import java.util.List;

/** Classifies entered, genuinely played rounds for the selected game player. */
public final class PlayerRoundStatisticsCalculator {
    private PlayerRoundStatisticsCalculator() {
    }

    public static PlayerRoundStatistics calculate(Player player) {
        return calculate(player, null);
    }

    public static PlayerRoundStatistics calculate(Player player, GameData gameData) {
        if (player == null || player.getScores() == null) {
            return new PlayerRoundStatistics(0, 0, 0);
        }

        List<Integer> scores = player.getScores();
        int firstPlayedIndex = firstPlayedRoundIndex(player, gameData, scores);
        int madeGameCount = 0;
        int packedCount = 0;
        int fullHandCount = 0;

        for (int index = firstPlayedIndex; index < scores.size(); index++) {
            Integer score = scores.get(index);
            if (score == null || score < 0) {
                continue;
            }
            if (score == 0) {
                madeGameCount++;
            } else if (score == 40) {
                packedCount++;
            } else if (score > 80) {
                fullHandCount++;
            }
        }

        return new PlayerRoundStatistics(madeGameCount, packedCount, fullHandCount);
    }

    private static int firstPlayedRoundIndex(
            Player player, GameData gameData, List<Integer> scores) {
        int scoreCount = scores.size();
        Integer joinRound = player.getMidGameJoinActiveRound();
        if (joinRound != null) {
            return firstPlayedIndexForJoinRound(joinRound, scoreCount);
        }

        // Players created before per-player join metadata was introduced can still
        // be identified using the game-level cached round and balancing score.
        Integer legacyJoinRound = gameData == null ? null : gameData.getMidGameJoinActiveRound();
        Integer legacyBackfill = gameData == null ? null : gameData.getMidGameJoinBackfillScore();
        if (legacyJoinRound != null && legacyBackfill != null
                && hasMatchingCatchUpPrefix(scores, legacyJoinRound, legacyBackfill)) {
            return firstPlayedIndexForJoinRound(legacyJoinRound, scoreCount);
        }

        int legacyPrefixEnd = legacyCatchUpPrefixEnd(scores);
        if (legacyPrefixEnd > 0) {
            return legacyPrefixEnd;
        }

        return 0;
    }

    private static int firstPlayedIndexForJoinRound(int joinRound, int scoreCount) {
        if (joinRound <= 0) {
            return scoreCount;
        }
        // Catch-up data occupies every round before the active join round. Round 1
        // is a special case in the existing backfill algorithm and is synthetic too.
        return Math.min(scoreCount, Math.max(1, joinRound - 1));
    }

    private static boolean hasMatchingCatchUpPrefix(
            List<Integer> scores, int joinRound, int backfillScore) {
        int backfillIndex = joinRound <= 1 ? 0 : joinRound - 2;
        if (joinRound == 0) {
            backfillIndex = 9;
        }
        if (backfillIndex < 0 || backfillIndex >= scores.size()
                || !Integer.valueOf(backfillScore).equals(scores.get(backfillIndex))) {
            return false;
        }
        for (int index = 0; index < backfillIndex; index++) {
            if (!Integer.valueOf(1).equals(scores.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static int legacyCatchUpPrefixEnd(List<Integer> scores) {
        int index = 0;
        while (index < scores.size() && Integer.valueOf(1).equals(scores.get(index))) {
            index++;
        }
        // The historical catch-up shape is one or more leading placeholder scores
        // of 1 followed by one positive balancing score. Later played rounds may
        // already exist, so only the synthetic prefix is skipped.
        if (index > 0 && index < scores.size()) {
            Integer balancingScore = scores.get(index);
            if (balancingScore != null && balancingScore > 0) {
                return index + 1;
            }
        }
        return 0;
    }
}

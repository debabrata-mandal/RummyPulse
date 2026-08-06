package com.example.rummypulse.data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rejects writes that could erase or unexpectedly mutate committed scores. */
public final class ScoreRegressionGuard {
    private ScoreRegressionGuard() {}

    public static void requireMetadataPreservesScores(GameData before, GameData after) {
        compare(before, after, 0, null);
    }

    public static void requireOnlyRoundChanged(GameData before, GameData after,
            int round1Based, Set<String> allowedPlayerIds) {
        if (round1Based < 1 || round1Based > GameIntegrityValidator.ROUND_COUNT) {
            throw new IllegalStateException("Invalid target round.");
        }
        compare(before, after, round1Based,
                allowedPlayerIds == null ? new HashSet<>() : allowedPlayerIds);
    }

    private static void compare(GameData before, GameData after, int allowedRound,
            Set<String> allowedPlayerIds) {
        if (before == null || after == null) throw new IllegalStateException("Game data is missing.");
        GameDataSchema.normalize(before);
        GameDataSchema.normalize(after);
        for (String playerId : before.getPlayerOrder()) {
            Player oldPlayer = GameDataSchema.findPlayer(before, playerId);
            Player newPlayer = GameDataSchema.findPlayer(after, playerId);
            if (newPlayer == null) throw new IllegalStateException("A metadata write removed a player.");
            for (int round = 1; round <= GameIntegrityValidator.ROUND_COUNT; round++) {
                Integer oldScore = score(oldPlayer, round);
                Integer newScore = score(newPlayer, round);
                boolean permitted = round == allowedRound && allowedPlayerIds.contains(playerId);
                if (!permitted && !equal(oldScore, newScore)) {
                    throw new IllegalStateException("Write attempted to change an unrelated score.");
                }
                if (valid(oldScore) && !valid(newScore)) {
                    throw new IllegalStateException("A committed score cannot be erased.");
                }
            }
        }
    }

    private static Integer score(Player player, int round) {
        List<Integer> values = player == null ? null : player.getScores();
        return values == null || values.size() < round ? null : values.get(round - 1);
    }

    private static boolean valid(Integer value) { return value != null && value >= 0; }
    private static boolean equal(Integer left, Integer right) {
        if (!valid(left) && !valid(right)) return true;
        return left == null ? right == null : left.equals(right);
    }
}

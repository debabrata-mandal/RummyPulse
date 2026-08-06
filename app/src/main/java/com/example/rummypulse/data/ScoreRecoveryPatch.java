package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Applies a confirmed history snapshot without replacing any valid current score. */
public final class ScoreRecoveryPatch {
    private ScoreRecoveryPatch() {}

    public static GameData restoreMissing(GameData latest, int round1Based,
            Map<String, Integer> historyScores) {
        if (latest == null || historyScores == null) {
            throw new IllegalStateException("Recovery data is unavailable.");
        }
        GameDataSchema.normalize(latest);
        Set<String> currentIds = new HashSet<>(latest.getPlayerOrder());
        // A historical snapshot may contain a player intentionally deleted later.
        // It remains safe only when every current immutable player ID is present;
        // extra historical players are ignored and never re-created.
        if (!historyScores.keySet().containsAll(currentIds)) {
            throw new IllegalStateException(
                    "The player list changed after this history record was created.");
        }
        GameData restored = GameDataPatchPolicy.copyGameShell(latest);
        ArrayList<Player> players = new ArrayList<>();
        boolean changed = false;
        for (String playerId : latest.getPlayerOrder()) {
            Player current = GameDataSchema.findPlayer(latest, playerId);
            Player copy = GameDataPatchPolicy.copyPlayer(current);
            Integer recovered = historyScores.get(playerId);
            if (recovered == null || recovered < 0) {
                throw new IllegalStateException("History contains an invalid player score.");
            }
            while (copy.getScores().size() < GameIntegrityValidator.ROUND_COUNT) {
                copy.getScores().add(-1);
            }
            if (!GameIntegrityValidator.hasValidScore(current, round1Based)) {
                copy.getScores().set(round1Based - 1, recovered);
                changed = true;
            }
            players.add(copy);
        }
        if (!changed) throw new IllegalStateException("The missing scores were already repaired.");
        restored.setPlayers(players);
        restored.setNumPlayers(players.size());
        GameDataSchema.normalize(restored);
        ScoreRegressionGuard.requireOnlyRoundChanged(
                latest, restored, round1Based, currentIds);
        for (Player player : restored.getPlayers()) {
            if (!GameIntegrityValidator.hasValidScore(player, round1Based)) {
                throw new IllegalStateException("History cannot completely repair this round.");
            }
        }
        return restored;
    }
}

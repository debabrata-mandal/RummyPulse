package com.example.rummypulse.data;

import com.google.firebase.firestore.FieldValue;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builder/parser for append-only round recovery records. */
public final class ScoreHistoryEvent {
    public static final int SCHEMA_VERSION = 1;

    private ScoreHistoryEvent() {}

    public static Map<String, Object> create(String gameId, int round1Based,
            GameData committedData, String operationId, String operationType,
            String editorUserId, long editGeneration, long previousRevision,
            long committedRevision) {
        if (gameId == null || operationId == null || editorUserId == null) {
            throw new IllegalArgumentException("History identity is required.");
        }
        GameDataSchema.normalize(committedData);
        Map<String, Integer> scores = snapshot(committedData, round1Based);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("schemaVersion", SCHEMA_VERSION);
        event.put("gameId", gameId);
        event.put("round", round1Based);
        event.put("scoresByPlayerId", scores);
        event.put("recoveryComplete", isRecoveryComplete(scores));
        event.put("operationId", operationId);
        event.put("operationType", operationType);
        event.put("editorUserId", editorUserId);
        event.put("editGeneration", editGeneration);
        event.put("previousRevision", previousRevision);
        event.put("committedRevision", committedRevision);
        event.put("committedAt", FieldValue.serverTimestamp());
        return event;
    }

    public static Map<String, Integer> snapshot(GameData data, int round1Based) {
        if (round1Based < 1 || round1Based > GameIntegrityValidator.ROUND_COUNT) {
            throw new IllegalArgumentException("Round must be between 1 and 10.");
        }
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String playerId : data.getPlayerOrder()) {
            Player player = GameDataSchema.findPlayer(data, playerId);
            scores.put(playerId, GameIntegrityValidator.hasValidScore(player, round1Based)
                    ? player.getScores().get(round1Based - 1) : -1);
        }
        return scores;
    }

    public static boolean isRecoveryComplete(Map<String, Integer> scores) {
        if (scores == null || scores.isEmpty()) return false;
        for (Integer score : scores.values()) {
            if (score == null || score < 0) return false;
        }
        return true;
    }
}

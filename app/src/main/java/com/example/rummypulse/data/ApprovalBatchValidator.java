package com.example.rummypulse.data;

/**
 * Pure validation and limit policy for atomic game approval.
 */
public final class ApprovalBatchValidator {

    public static final int MAX_GAMES_PER_TRANSACTION = 100;
    public static final int MAX_TRANSACTION_WRITES = 450;

    private ApprovalBatchValidator() {}

    public static void validateSelectionCount(int gameCount) {
        if (gameCount <= 0) {
            throw new IllegalArgumentException("No completed games to approve.");
        }
        if (gameCount > MAX_GAMES_PER_TRANSACTION) {
            throw new IllegalArgumentException(
                    "Cannot approve more than " + MAX_GAMES_PER_TRANSACTION
                            + " games at once. Select a smaller group.");
        }
    }

    public static void validateWriteCount(
            int gameCount, int cleanupDocumentCount, int playerStatsDocumentCount) {
        int writes = gameCount * 3 + cleanupDocumentCount + playerStatsDocumentCount;
        if (writes > MAX_TRANSACTION_WRITES) {
            throw new IllegalArgumentException(
                    "This approval needs " + writes + " writes, exceeding the safe limit of "
                            + MAX_TRANSACTION_WRITES + ". Select fewer games.");
        }
    }

    public static GameData validateGameData(String gameId, GameDataWrapper wrapper) {
        if (wrapper == null || wrapper.getData() == null) {
            throw new IllegalStateException(
                    "Validation failed for game " + gameId + ": game data is missing.");
        }
        GameData gameData = wrapper.getData();
        GameIntegrityResult integrity = GameIntegrityValidator.validate(gameData);
        if (!integrity.isComplete()) {
            throw new IllegalStateException(
                    "Validation failed for game " + gameId
                            + ": " + integrity.describe());
        }
        java.util.Set<String> userIds = new java.util.HashSet<>();
        if (gameData.getPlayers() == null || gameData.getPlayers().size() < 2) {
            throw new IllegalStateException(
                    "Validation failed for game " + gameId + ": at least two players are required.");
        }
        for (Player player : gameData.getPlayers()) {
            String userId = player == null ? null : player.getUserId();
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalStateException(
                        "Validation failed for game " + gameId
                                + ": every player must be mapped to an app profile.");
            }
            if (!userIds.add(userId)) {
                throw new IllegalStateException(
                        "Validation failed for game " + gameId
                                + ": a profile is mapped more than once.");
            }
        }
        return gameData;
    }
}

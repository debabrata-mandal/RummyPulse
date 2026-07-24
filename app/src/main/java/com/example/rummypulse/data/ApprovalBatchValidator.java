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

    public static void validateWriteCount(int gameCount, int cleanupDocumentCount) {
        int writes = gameCount * 3 + cleanupDocumentCount;
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
        String status = gameData.getGameStatus();
        if (!"Completed".equals(status)) {
            throw new IllegalStateException(
                    "Validation failed for game " + gameId
                            + ": expected Completed but found " + status + ".");
        }
        return gameData;
    }
}

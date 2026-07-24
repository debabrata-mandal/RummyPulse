package com.example.rummypulse.data;

/**
 * Firestore safety limits for deleting games and their related review records atomically.
 */
public final class DeleteBatchValidator {

    public static final int MAX_GAMES_PER_DELETE = 100;
    public static final int MAX_BATCH_WRITES = 450;

    private DeleteBatchValidator() {}

    public static void validateSelectionCount(int gameCount) {
        if (gameCount <= 0) {
            throw new IllegalArgumentException("Select at least one game to delete.");
        }
        if (gameCount > MAX_GAMES_PER_DELETE) {
            throw new IllegalArgumentException(
                    "Cannot delete more than " + MAX_GAMES_PER_DELETE
                            + " games at once. Select a smaller group.");
        }
    }

    public static void validateWriteCount(int gameCount, int approvalDocumentCount) {
        int writes = gameCount * 2 + approvalDocumentCount;
        if (writes > MAX_BATCH_WRITES) {
            throw new IllegalArgumentException(
                    "This deletion needs " + writes + " writes, exceeding the safe limit of "
                            + MAX_BATCH_WRITES + ". Select fewer games.");
        }
    }
}

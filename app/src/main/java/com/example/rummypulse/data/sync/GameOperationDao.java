package com.example.rummypulse.data.sync;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface GameOperationDao {
    @Query("SELECT * FROM game_snapshots WHERE gameId = :gameId LIMIT 1")
    GameSnapshotEntity getSnapshot(String gameId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertSnapshot(GameSnapshotEntity snapshot);

    @Query("SELECT * FROM round_score_drafts WHERE gameId = :gameId"
            + " AND editGeneration = :editGeneration LIMIT 1")
    RoundScoreDraftEntity getRoundDraft(String gameId, long editGeneration);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertRoundDraft(RoundScoreDraftEntity draft);

    @Query("DELETE FROM round_score_drafts WHERE gameId = :gameId"
            + " AND editGeneration = :editGeneration")
    void deleteRoundDraft(String gameId, long editGeneration);

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM pending_game_operations WHERE gameId = :gameId")
    long nextSequence(String gameId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertOperation(PendingGameOperation operation);

    @Query("SELECT * FROM pending_game_operations WHERE gameId = :gameId"
            + " AND status IN ('PENDING', 'IN_FLIGHT', 'BLOCKED') ORDER BY sequence")
    List<PendingGameOperation> getActiveOperations(String gameId);

    @Query("SELECT * FROM pending_game_operations WHERE gameId = :gameId"
            + " AND status = 'PENDING' ORDER BY sequence LIMIT 1")
    PendingGameOperation getNextPending(String gameId);

    @Query("UPDATE pending_game_operations SET status = 'PENDING'"
            + " WHERE gameId = :gameId AND status = 'IN_FLIGHT'")
    void resetInterruptedOperations(String gameId);

    @Query("SELECT * FROM pending_game_operations WHERE gameId = :gameId"
            + " AND status = 'PENDING' AND type = :type"
            + " AND ((:playerId IS NULL AND playerId IS NULL) OR playerId = :playerId)"
            + " ORDER BY sequence DESC LIMIT 1")
    PendingGameOperation getLatestPending(String gameId, String type, String playerId);

    @Query("DELETE FROM pending_game_operations WHERE operationId = :operationId")
    void deleteOperation(String operationId);

    @Query("UPDATE pending_game_operations SET payloadJson = :payloadJson"
            + " WHERE operationId = :operationId AND status = 'PENDING'")
    int replacePendingPayload(String operationId, String payloadJson);

    @Query("UPDATE pending_game_operations SET status = :status,"
            + " attemptCount = attemptCount + :attemptIncrement, lastError = :lastError"
            + " WHERE operationId = :operationId")
    void updateOperationState(
            String operationId, String status, int attemptIncrement, String lastError);

    @Query("UPDATE pending_game_operations SET status = 'BLOCKED', lastError = :reason"
            + " WHERE gameId = :gameId AND status IN ('PENDING', 'IN_FLIGHT')")
    void blockActiveOperations(String gameId, String reason);

    @Query("SELECT COUNT(*) FROM pending_game_operations WHERE gameId = :gameId"
            + " AND status IN ('PENDING', 'IN_FLIGHT', 'BLOCKED')")
    int activeOperationCount(String gameId);

    @Query("SELECT COUNT(*) FROM pending_game_operations WHERE gameId = :gameId"
            + " AND status IN ('PENDING', 'IN_FLIGHT')")
    int syncableOperationCount(String gameId);
}

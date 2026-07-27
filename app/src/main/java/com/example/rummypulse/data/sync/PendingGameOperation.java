package com.example.rummypulse.data.sync;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pending_game_operations",
        indices = {
                @Index(value = {"gameId", "sequence"}, unique = true),
                @Index(value = {"gameId", "status"})
        })
public class PendingGameOperation {
    @PrimaryKey
    @NonNull
    public String operationId;
    @NonNull
    public String gameId;
    public long editGeneration;
    public String playerId;
    public long sequence;
    @NonNull
    public String type;
    @NonNull
    public String payloadJson;
    @NonNull
    public String status;
    public int attemptCount;
    public String lastError;
    public long createdAt;

    public PendingGameOperation(
            @NonNull String operationId,
            @NonNull String gameId,
            long editGeneration,
            String playerId,
            long sequence,
            @NonNull String type,
            @NonNull String payloadJson,
            @NonNull String status,
            int attemptCount,
            String lastError,
            long createdAt) {
        this.operationId = operationId;
        this.gameId = gameId;
        this.editGeneration = editGeneration;
        this.playerId = playerId;
        this.sequence = sequence;
        this.type = type;
        this.payloadJson = payloadJson;
        this.status = status;
        this.attemptCount = attemptCount;
        this.lastError = lastError;
        this.createdAt = createdAt;
    }

    public GameOperationType operationType() {
        return GameOperationType.valueOf(type);
    }

    public GameOperationStatus operationStatus() {
        return GameOperationStatus.valueOf(status);
    }
}

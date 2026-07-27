package com.example.rummypulse.data.sync;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(
        tableName = "round_score_drafts",
        primaryKeys = {"gameId", "editGeneration"})
public class RoundScoreDraftEntity {
    @NonNull
    public String gameId;
    public long editGeneration;
    @NonNull
    public String serializedDraft;
    public long updatedAt;

    public RoundScoreDraftEntity(
            @NonNull String gameId,
            long editGeneration,
            @NonNull String serializedDraft,
            long updatedAt) {
        this.gameId = gameId;
        this.editGeneration = editGeneration;
        this.serializedDraft = serializedDraft;
        this.updatedAt = updatedAt;
    }
}

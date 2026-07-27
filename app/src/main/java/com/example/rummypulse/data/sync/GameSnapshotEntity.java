package com.example.rummypulse.data.sync;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "game_snapshots")
public class GameSnapshotEntity {
    @PrimaryKey
    @NonNull
    public String gameId;
    @NonNull
    public String snapshotJson;
    public long revision;
    public long editGeneration;
    public long updatedAt;

    public GameSnapshotEntity(
            @NonNull String gameId,
            @NonNull String snapshotJson,
            long revision,
            long editGeneration,
            long updatedAt) {
        this.gameId = gameId;
        this.snapshotJson = snapshotJson;
        this.revision = revision;
        this.editGeneration = editGeneration;
        this.updatedAt = updatedAt;
    }
}

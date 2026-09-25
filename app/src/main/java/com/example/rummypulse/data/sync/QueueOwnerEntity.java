package com.example.rummypulse.data.sync;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "queue_owner")
public class QueueOwnerEntity {
    @PrimaryKey
    public int id = 1;
    @NonNull
    public String uid;

    public QueueOwnerEntity() {}

    @Ignore
    public QueueOwnerEntity(@NonNull String uid) {
        this.uid = uid;
    }
}

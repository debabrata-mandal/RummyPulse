package com.example.rummypulse.data.sync;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                GameSnapshotEntity.class,
                PendingGameOperation.class,
                RoundScoreDraftEntity.class
        },
        version = 1,
        exportSchema = false)
public abstract class GameOperationDatabase extends RoomDatabase {
    private static volatile GameOperationDatabase instance;

    public abstract GameOperationDao operations();

    public static GameOperationDatabase getInstance(Context context) {
        GameOperationDatabase current = instance;
        if (current == null) {
            synchronized (GameOperationDatabase.class) {
                current = instance;
                if (current == null) {
                    current = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    GameOperationDatabase.class,
                                    "rummy-pulse-operations.db")
                            .build();
                    instance = current;
                }
            }
        }
        return current;
    }
}

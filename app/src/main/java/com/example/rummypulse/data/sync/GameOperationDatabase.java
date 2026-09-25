package com.example.rummypulse.data.sync;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

@Database(
        entities = {
                GameSnapshotEntity.class,
                PendingGameOperation.class,
                RoundScoreDraftEntity.class,
                QueueOwnerEntity.class
        },
        version = 2,
        exportSchema = false)
public abstract class GameOperationDatabase extends RoomDatabase {
    private static volatile GameOperationDatabase instance;
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `queue_owner` (`id` INTEGER NOT NULL, `uid` TEXT NOT NULL, PRIMARY KEY(`id`))");
        }
    };

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
                            .addMigrations(MIGRATION_1_2)
                            .build();
                    instance = current;
                }
            }
        }
        return current;
    }

}
